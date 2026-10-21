# Sign Setu gateway

The department gateway the app uses while online. It is optional: a build without `gatewayUrl` never connects, and the app works the same without it. It also carries the online assistant that the AI counsellor needs.

| Route | Caller | Purpose |
| --- | --- | --- |
| `GET /healthz` | Host | Health check |
| `POST /v1/enroll` | Phone | Trades a one-time enrolment code for a device token |
| `GET /v1/models`, `GET /v1/models/{name}` | Phone | Model manifest (`MODEL_DIR/manifest.json`); a file is served only if it matches its manifest hash |
| `POST /v1/assist` | Phone | Proxy to the AI service; the key stays here and no text is stored. The whole system prompt must match a template in `signsetu_gateway/app_prompts.json` (language names fill the marked slots, one line, up to 80 characters), so a caller cannot add instructions. The file is written by the app's `GatewayPromptContractTest` (`UPDATE_PROMPT_FIXTURE=1`) and that test fails when a prompt changes without it |
| `POST /v1/feedback` | Phone | Label outcome of a reading, with consent; any extra field is refused |
| `GET /v1/admin/feedback` | Reviewer portal | Keycloak access token with the `reviewer` realm role |
| `GET /v1/admin/devices`, `POST /v1/admin/devices/{id}/revoke` | Administrator | Keycloak access token with the `gateway-admin` realm role (`ADMIN_ROLE` to rename it). Lists device ids and whether each is revoked, never tokens; a revoked device gets 401 and its app drops the token and falls back to the on-device assistant. Written to the audit table |

Phone routes need the device token and are rate limited to 30 requests a minute. Enrolment is limited per client address (10 a minute) and, across all callers, to 20 wrong codes a minute, because the address comes from a forwarded header a caller can set. While the second limit is reached every enrolment, valid codes included, is refused with 429 and a `Retry-After` header, and it ends by itself a minute after the last wrong code; refused requests are not counted, so an attacker has to keep sending wrong codes to keep it up. The first lockout is written to the audit table as `enroll.locked`. Letting valid codes through would let an attacker test guesses past the cap. Enrolment, assist, feedback and admin reads are written to an audit table. The interactive API docs are switched off.

## Key files

| Path | Contents |
| --- | --- |
| `signsetu_gateway/app.py` | Routes and the `create_app` factory |
| `signsetu_gateway/config.py` | Settings, read from the environment |
| `signsetu_gateway/db.py` | SQLAlchemy tables: devices, feedback, audit |
| `signsetu_gateway/security.py` | Device tokens, rate limit, Keycloak token check |
| `tests/test_gateway.py` | Tests (SQLite, stubbed AI service and JWKS) |
| `tools/make_codes.py` | Prints enrolment codes and their `ENROLL_HASHES` line |
| `Dockerfile` | Container image; the host terminates TLS and passes `$PORT` |

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `sqlite:///gateway.db` | SQLAlchemy URL. A host's `postgres://` or `postgresql://` URL is accepted and uses the psycopg 3 driver. Keep it a secret |
| `TRUSTED_PROXY_HOPS` | `0` | How many reverse proxies in front of the gateway append to `X-Forwarded-For`. With 0 the header is ignored and the socket address is the caller, which behind a proxy means every caller shares one per-address budget (safe, but tight). Set it to 1 behind one proxy that appends the real client address on the right; check the host's documentation first, because a wrong value lets callers choose their own address. The image runs uvicorn with `--no-proxy-headers`, since its `--forwarded-allow-ips='*'` believed the leftmost entry, which a caller writes |
| `ENROLL_PER_MINUTE`, `ENROLL_FAILURES_PER_MINUTE` | `10`, `20` | The two enrolment limits above |
| `REQUIRE_PERSISTENT_DB` | empty | Set to `1` to make the gateway refuse to start on SQLite, so a restart cannot lose enrolments |
| `MODEL_DIR` | `models` | Folder holding `manifest.json` and the model files it lists |
| `ENROLL_HASHES` | empty | Comma-separated SHA-256 hex of each issued enrolment code; empty means no phone can enrol |
| `ASSIST_URL` | empty | Chat completions endpoint of the AI service |
| `ASSIST_KEY` | empty | Bearer key for the AI service |
| `ASSIST_MODEL` | empty | Model name sent to the AI service |
| `OIDC_ISSUER` | empty | Keycloak realm URL; empty disables the admin route |
| `OIDC_AUDIENCE` | `signsetu-portal` | Expected audience of reviewer tokens |

Keep the AI service settings in `backend/.env` (untracked), never in the repository.

## Run

Python 3.13 or later. From `backend/`:

```
pip install -e ".[test]"
pytest
```

A production run, with TLS on the gateway itself:

```
DATABASE_URL=postgresql+psycopg://user:pass@host/signsetu \
MODEL_DIR=/srv/models ENROLL_HASHES=<hashes from make_codes.py> \
ASSIST_URL=... ASSIST_KEY=... ASSIST_MODEL=... \
OIDC_ISSUER=https://id.example/realms/police \
uvicorn signsetu_gateway.app:create_app --factory --host 0.0.0.0 --port 8443 --ssl-keyfile key.pem --ssl-certfile cert.pem
```

To read settings from `.env` instead, install `python-dotenv` and add `--env-file .env` to the `uvicorn` command.

Build the app against it with `./gradlew assembleStandardRelease -PgatewayUrl=https://gateway.example`, or set `gatewayUrl=` in `local.properties`.

## Deploy free

Render (free web service, HTTPS included) with Neon (free Postgres):

1. Neon: create a project, copy its connection string and change the start to `postgresql+psycopg://`; keep `?sslmode=require`.
2. `python tools/make_codes.py 10` prints ten enrolment codes and the `ENROLL_HASHES=` line. Keep the codes; give one to each phone.
3. Render: New, Blueprint, pick this repo (`render.yaml` at the root builds `backend/Dockerfile`; it sets `ASSIST_URL` and `ASSIST_MODEL`). Set `DATABASE_URL`, `ENROLL_HASHES` and `ASSIST_KEY` when asked.
4. Put the service URL in `local.properties` as `gatewayUrl=https://<name>.onrender.com`, then build the app.

A free Render service sleeps after 15 minutes idle and takes up to a minute to wake. The app waits up to 60 seconds for a reply, shows the fallback toast and uses the on-device assistant, then recovers on a later call.

Deeper detail: [docs/README.md](../docs/README.md) and [docs/04-security.md](../docs/04-security.md).

## Where the data lives

The default is a SQLite file inside the container. On a host with an ephemeral disk, such as the Render free plan, that file is lost at every restart and redeploy: every enrolled phone would have to enrol again with a new code, and the audit table would start empty. The blueprint in `render.yaml` leaves `DATABASE_URL` for the operator to set, so check what the running service uses before relying on it.

For anything beyond a trial, use PostgreSQL and set `REQUIRE_PERSISTENT_DB=1`, so a missing `DATABASE_URL` stops the service instead of silently falling back to a file that will vanish. The tables are created on start; there is no migration framework because the three tables have not changed. Add one (Alembic) with the first schema change.

Moving existing records, from `backend/`, with the source file copied somewhere safe first:

```
SOURCE_URL=sqlite:///gateway.db TARGET_URL=postgresql://... python tools/migrate_db.py --dry-run
SOURCE_URL=sqlite:///gateway.db TARGET_URL=postgresql://... python tools/migrate_db.py
```

The tool reads both URLs from the environment, copies devices, feedback and audit rows with their ids, refuses a target that already has rows, checks the row counts and writes nothing if they differ. It only reads the source. It has been tested between SQLite files; run the dry run, then the copy, against a new empty PostgreSQL database before pointing the service at it.

The audit table grows without limit and holds no subject data. Decide on a retention period with the department before it matters.

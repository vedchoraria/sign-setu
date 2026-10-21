# Sign Setu gateway

The department gateway the app uses while online. It is optional: a build without `gatewayUrl` never connects, and the app works the same without it. It also carries the online assistant that the AI counsellor needs.

| Route | Caller | Purpose |
| --- | --- | --- |
| `GET /healthz` | Host | Health check |
| `POST /v1/enroll` | Phone | Trades a one-time enrolment code for a device token |
| `GET /v1/models`, `GET /v1/models/{name}` | Phone | Model manifest (`MODEL_DIR/manifest.json`); a file is served only if it matches its manifest hash |
| `POST /v1/assist` | Phone | Proxy to the AI service; the key stays here and no text is stored |
| `POST /v1/feedback` | Phone | Label outcome of a reading, with consent; any extra field is refused |
| `GET /v1/admin/feedback` | Reviewer portal | Keycloak access token with the `reviewer` realm role |

Phone routes need the device token and are rate limited to 30 requests a minute. Enrolment, assist, feedback and admin reads are written to an audit table. The interactive API docs are switched off.

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
| `DATABASE_URL` | `sqlite:///gateway.db` | SQLAlchemy URL; use `postgresql+psycopg://...` in production |
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

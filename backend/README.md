# Sign Setu gateway

The department gateway the app uses while online (decision D-47). The app works the same without it.

| Route | Caller | Purpose |
| --- | --- | --- |
| `POST /v1/enroll` | Phone | Trades a one-time enrolment code for a device token |
| `GET /v1/models`, `GET /v1/models/{name}` | Phone | Model manifest; a file is served only if it matches its manifest hash |
| `POST /v1/assist` | Phone | Proxy to the AI service; the key stays here and no text is stored |
| `POST /v1/feedback` | Phone | Label outcome of a reading, with consent; any extra field is refused |
| `GET /v1/admin/feedback` | Reviewer portal | Keycloak access token with the `reviewer` realm role |

## Run

```
pip install -e ".[test]"
pytest
DATABASE_URL=postgresql+psycopg://user:pass@host/signsetu \
MODEL_DIR=/srv/models ENROLL_HASHES=<sha256 of each issued code, comma separated> \
ASSIST_URL=... ASSIST_KEY=... ASSIST_MODEL=... \
OIDC_ISSUER=https://id.example/realms/police \
uvicorn signsetu_gateway.app:create_app --factory --host 0.0.0.0 --port 8443 --ssl-keyfile key.pem --ssl-certfile cert.pem
```

Build the app against it with `./gradlew assembleStandardRelease -PgatewayUrl=https://gateway.example`. Keep the AI service settings in `backend/.env` (untracked) and start with `uvicorn ... --env-file .env`.

## Deploy free

Render (free web service, HTTPS included) with Neon (free Postgres):

1. Neon: create a project, copy its connection string and change the start to `postgresql+psycopg://`; keep `?sslmode=require`.
2. `python tools/make_codes.py 10` prints ten enrolment codes and the `ENROLL_HASHES=` line. Keep the codes; give one to each phone.
3. Render: New, Blueprint, pick this repo (`render.yaml` at the root). Set `DATABASE_URL`, `ENROLL_HASHES` and `ASSIST_KEY` when asked.
4. Put the service URL in `local.properties` as `gatewayUrl=https://<name>.onrender.com`, then build the app.

A free Render service sleeps after 15 minutes idle and takes up to a minute to wake. The app waits up to 25 seconds for a reply, shows the fallback toast and uses the on-device assistant, then recovers on a later call.

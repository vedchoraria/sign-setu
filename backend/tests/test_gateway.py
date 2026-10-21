import hashlib
import json
import time

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient

from signsetu_gateway.app import create_app
from signsetu_gateway.config import Settings

CODE = "enrol-code-1234"
ISS = "https://id.example/realms/police"
KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)


class FakeJwks:
    def get_signing_key_from_jwt(self, token):
        return type("K", (), {"key": KEY.public_key()})


def bearer(roles):
    t = jwt.encode({"iss": ISS, "aud": "signsetu-portal", "exp": time.time() + 60, "realm_access": {"roles": roles}}, KEY, algorithm="RS256")
    return {"Authorization": f"Bearer {t}"}


@pytest.fixture
def env(tmp_path):
    blob = b"model-bytes"
    (tmp_path / "sign_reader.bin").write_bytes(blob)
    (tmp_path / "manifest.json").write_text(json.dumps([{"name": "sign_reader.bin", "version": "1", "sha256": hashlib.sha256(blob).hexdigest()}]))
    s = Settings(database_url="sqlite://", model_dir=tmp_path, enroll_hashes=frozenset({hashlib.sha256(CODE.encode()).hexdigest()}),
                 oidc_issuer=ISS, rate_per_minute=5)

    async def assist(system, user, max_tokens):
        return f"echo:{user}"
    with TestClient(create_app(s, assist, jwks=lambda issuer: FakeJwks())) as client:
        yield client, tmp_path


def enrolled(client):
    r = client.post("/v1/enroll", json={"code": CODE})
    assert r.status_code == 200
    return {"Authorization": f"Bearer {r.json()['token']}"}


def test_enrol_needs_a_valid_code(env):
    c, _ = env
    assert c.post("/v1/enroll", json={"code": "wrong-code-0000"}).status_code == 403
    enrolled(c)
    assert c.post("/v1/enroll", json={"code": CODE}).status_code == 403


def test_device_routes_need_a_token(env):
    c, _ = env
    for method, path in [("get", "/v1/models"), ("post", "/v1/assist"), ("post", "/v1/feedback")]:
        assert getattr(c, method)(path).status_code == 401


def test_model_manifest_and_verified_download(env):
    c, d = env
    h = enrolled(c)
    assert c.get("/v1/models", headers=h).json()["models"][0]["version"] == "1"
    assert c.get("/v1/models/sign_reader.bin", headers=h).content == b"model-bytes"
    assert c.get("/v1/models/..%2fmanifest.json", headers=h).status_code == 404
    (d / "sign_reader.bin").write_bytes(b"tampered")
    assert c.get("/v1/models/sign_reader.bin", headers=h).status_code == 500


def test_assist_proxies_and_limits_size(env):
    c, _ = env
    h = enrolled(c)
    assert c.post("/v1/assist", json={"system": "s", "user": "hi"}, headers=h).json() == {"text": "echo:hi"}
    assert c.post("/v1/assist", json={"system": "s", "user": "x" * 20_001}, headers=h).status_code == 413


def test_feedback_takes_labels_only_and_needs_consent(env):
    c, _ = env
    h = enrolled(c)
    ok = {"consent": True, "predicted": "water", "outcome": "confirmed"}
    assert c.post("/v1/feedback", json=ok, headers=h).status_code == 201
    assert c.post("/v1/feedback", json={**ok, "consent": False}, headers=h).status_code == 422
    assert c.post("/v1/feedback", json={**ok, "landmarks": [[0.1, 0.2]]}, headers=h).status_code == 422
    assert c.post("/v1/feedback", json={"predicted": "water", "outcome": "confirmed"}, headers=h).status_code == 422


def test_admin_needs_the_reviewer_role(env):
    c, _ = env
    h = enrolled(c)
    c.post("/v1/feedback", json={"consent": True, "predicted": "water", "outcome": "rejected"}, headers=h)
    assert c.get("/v1/admin/feedback").status_code == 401
    assert c.get("/v1/admin/feedback", headers=bearer(["user"])).status_code == 403
    assert c.get("/v1/admin/feedback", headers=h).status_code == 401
    assert c.get("/v1/admin/feedback", headers=bearer(["reviewer"])).json()[0]["outcome"] == "rejected"


def test_rate_limit(env):
    c, _ = env
    h = enrolled(c)
    codes = [c.get("/v1/models", headers=h).status_code for _ in range(7)]
    assert codes[:5] == [200] * 5 and codes[-1] == 429

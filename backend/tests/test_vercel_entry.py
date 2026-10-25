import importlib.util
import json
from pathlib import Path

from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parent.parent


def test_the_vercel_entry_serves_the_gateway(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", "sqlite://")
    spec = importlib.util.spec_from_file_location("vercel_index", ROOT / "api" / "index.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    with TestClient(module.app) as c:
        assert c.get("/healthz").json() == {"ok": True}


def test_vercel_sends_every_path_to_the_entry():
    config = json.loads((ROOT / "vercel.json").read_text())
    assert {"source": "/(.*)", "destination": "/api/index"} in config["rewrites"]
    assert (ROOT / "api" / "index.py").is_file()

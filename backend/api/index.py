"""Vercel entry point: Vercel looks for an ASGI object named `app` in api/index.py."""
from signsetu_gateway.app import create_app

app = create_app()

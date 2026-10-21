FROM python:3.15.0-slim
WORKDIR /app
COPY pyproject.toml ./
COPY signsetu_gateway ./signsetu_gateway
RUN pip install --no-cache-dir .
RUN mkdir -p /app/models && echo '[]' > /app/models/manifest.json
ENV MODEL_DIR=/app/models PYTHONUNBUFFERED=1
# The host terminates TLS and passes the port in $PORT.
CMD ["sh", "-c", "exec uvicorn signsetu_gateway.app:create_app --factory --host 0.0.0.0 --port ${PORT:-8000} --proxy-headers --forwarded-allow-ips='*'"]

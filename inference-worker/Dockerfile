FROM python:3.10-slim

WORKDIR /app

# Copy requirements and install
COPY inference-worker/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Copy source codes
COPY plugin-sdk /app/plugin-sdk
COPY plugins /app/plugins
COPY inference-worker /app/inference-worker

ENV PYTHONPATH=/app/plugin-sdk:/app/plugins:/app/inference-worker
ENV NATS_URL=nats://nats:4222

ENTRYPOINT ["python", "inference-worker/main.py"]

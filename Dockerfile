FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl git ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pyproject.toml README.md ./
COPY packages ./packages
COPY services ./services
COPY pipelines ./pipelines
COPY config ./config

RUN pip install --no-cache-dir -e .

ENV GEM_HOME=/app
ENV PYTHONPATH=/app/packages:/app

CMD ["python", "-m", "services.scheduler.cli", "--status"]

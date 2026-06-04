COMPOSE := $(shell command -v docker-compose >/dev/null 2>&1 && echo docker-compose || echo "docker compose")
GRADLE ?= gradle

.PHONY: setup compose-config up down logs ps build test lint admin-health router-health scheduler-status aso-stub

setup:
	@test -f .env || cp .env.example .env

compose-config: setup
	$(COMPOSE) config

build: setup
	$(COMPOSE) build

up: setup
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

test:
	$(GRADLE) test

lint:
	$(GRADLE) check

admin-health:
	curl -fsS http://localhost:8000/health

router-health:
	curl -fsS http://localhost:8010/health

scheduler-status:
	$(GRADLE) schedulerStatus

aso-stub:
	$(GRADLE) asoStub

COMPOSE := $(shell command -v docker-compose >/dev/null 2>&1 && echo docker-compose || echo "docker compose")
GRADLE ?= gradle
LAUNCH_AGENT := $(HOME)/Library/LaunchAgents/com.onexeor.gem-os.plist
LAUNCH_LABEL := gui/$(shell id -u)/com.onexeor.gem-os

.PHONY: setup compose-config up down logs ps build test lint admin-health brain-health router-health slack-health scheduler-status aso-stub macos-autostart-install macos-autostart-uninstall macos-autostart-run

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

brain-health:
	curl -fsS http://localhost:8020/health

router-health:
	curl -fsS http://localhost:8010/health

slack-health:
	curl -fsS http://localhost:8030/health

scheduler-status:
	$(GRADLE) schedulerStatus

aso-stub:
	$(GRADLE) asoStub

macos-autostart-install:
	mkdir -p $(HOME)/Library/LaunchAgents logs
	chmod +x ops/macos/start-gem-os.sh
	cp ops/macos/com.onexeor.gem-os.plist $(LAUNCH_AGENT)
	-launchctl bootout $(LAUNCH_LABEL) 2>/dev/null
	launchctl bootstrap gui/$(shell id -u) $(LAUNCH_AGENT)
	launchctl enable $(LAUNCH_LABEL)
	launchctl kickstart -k $(LAUNCH_LABEL)

macos-autostart-uninstall:
	-launchctl bootout $(LAUNCH_LABEL) 2>/dev/null
	rm -f $(LAUNCH_AGENT)

macos-autostart-run:
	ops/macos/start-gem-os.sh

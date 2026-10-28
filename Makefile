# CI locale (Linux/macOS/Git Bash avec make). Sous Windows sans make : scripts\ci.ps1
.PHONY: all backend ml frontend up down e2e

all: backend ml frontend

backend:
	cd backend && ./mvnw -B -q verify

ml:
	cd ml-service && python3.11 -m venv .venv && .venv/bin/pip install -q -r requirements-dev.txt && .venv/bin/python -m pytest

frontend:
	cd frontend && npm ci && npx ng build && npx ng test --watch=false

up:
	docker compose up --build -d

down:
	docker compose down

e2e:
	cd frontend && npx playwright test

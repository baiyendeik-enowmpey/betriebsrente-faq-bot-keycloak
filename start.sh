#!/bin/bash
docker compose up -d postgres ollama keycloak-db keycloak
sleep 5
docker exec $(docker ps -qf "name=ollama") ollama pull llama3.2:3b || true
docker exec $(docker ps -qf "name=ollama") ollama pull nomic-embed-text || true
docker compose up --build -d app

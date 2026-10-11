# Copilot instructions for betriebsrente-faq-bot-keycloak

## Build and run

- This is the Keycloak variant of the Betriebsrente FAQ Bot.
- Start dependencies with `docker compose up -d postgres ollama keycloak-db keycloak`.
- Pull Ollama models with `docker compose exec -T ollama ollama pull llama3.2:3b` and `docker compose exec -T ollama ollama pull nomic-embed-text`.
- Build and start the app with `docker compose up --build -d app`.
- App URL: `http://localhost:8091`; browser-facing Keycloak realm URL: `http://localhost:8180/realms/betriebsrente`.
- Keycloak imports the realm from `keycloak/realm/betriebsrente-realm.json` on first startup.
- Override local-development secrets with `KEYCLOAK_*` environment variables or a local `.env` file.
- Build/test commands: `mvn test`; package without tests: `mvn -DskipTests package`.
- UI E2E requires the app, Keycloak, and Playwright Chromium. Set `E2E_BASE_URL`, `E2E_USERNAME`, `E2E_PASSWORD`, `E2E_ADMIN_USERNAME`, and `E2E_ADMIN_PASSWORD` as needed.

## Architecture

- `src/main/java/de/team/betriebsrente/config/`: OAuth2 login, Keycloak client registration, OIDC realm-role mapping, and security rules.
- `src/main/java/de/team/betriebsrente/controller/`: HTTP layer for chat, login, and admin functions.
- `src/main/java/de/team/betriebsrente/rag/`: retrieval, document ingestion, parsing, OCR, and seed initialization.
- `src/main/resources/application.yml`: PostgreSQL, Flyway, pgvector, Ollama, and Keycloak issuer/client settings.
- `keycloak/realm/betriebsrente-realm.json`: development realm, confidential OIDC client, USER/ADMIN roles, and sample accounts.
- `docker-compose.yml`: app, application database, Ollama, Keycloak, and Keycloak database. The browser uses `localhost:8180`; the app uses `keycloak:8080` for internal OIDC requests while validating the public issuer.

## Conventions

- Authentication and authorization are provided by Keycloak; do not add local application password storage.
- Realm roles `USER` and `ADMIN` map to Spring authorities `ROLE_USER` and `ROLE_ADMIN`. Admin routes remain restricted to `ROLE_ADMIN`.
- Keycloak realm JSON is imported only when the realm does not already exist. Existing realm users and configuration are managed in Keycloak Admin Console.
- Use constructor injection, Flyway migrations for application database schema changes, and preserve RAG source metadata.

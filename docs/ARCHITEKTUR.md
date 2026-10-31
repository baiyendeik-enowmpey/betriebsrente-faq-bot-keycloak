# Architektur – Betriebsrente FAQ Bot (Keycloak-Variante)

Lokal betriebener, deutschsprachiger FAQ-Chatbot für interne Teams zur Betriebsrente.
Er beantwortet Fragen per **Retrieval-Augmented Generation (RAG)**: Passende Abschnitte aus
freigegebenem Fachwissen werden per Vektorsuche gefunden und einem lokalen LLM als Kontext übergeben.
Es verlassen keine Daten die lokale Umgebung.

| Bereich | Technologie |
|---|---|
| Laufzeit | Java 25, Spring Boot 4.1.2 (Spring MVC, Thymeleaf, Data JPA / Hibernate 7) |
| KI | Spring AI 2.0.2, Ollama (`llama3.2:3b` für Antworten, `nomic-embed-text` für Embeddings, 768 Dimensionen) |
| Datenhaltung | PostgreSQL 16 + pgvector (HNSW-Index, Kosinus-Distanz), Flyway-Migrationen |
| Authentifizierung | Keycloak 26 (OIDC Authorization Code + PKCE), Spring Security OAuth2 Client |
| Dokumentverarbeitung | PDFBox 3 (PDF), Apache POI (DOCX), Tess4J/Tesseract (OCR, aktuell nicht angebunden) |
| Betrieb | Docker Compose |

---

## 1. Systemkontext

```mermaid
flowchart LR
    team["👤 Team-Mitarbeiter<br/>(Rolle USER)"]
    fach["👤 Fachbereich<br/>(Rolle ADMIN)"]
    kcadmin["👤 Keycloak-Admin"]

    subgraph lokal["Lokale Umgebung (Docker)"]
        bot["Betriebsrente FAQ Bot"]
        kc["Keycloak<br/>(Identity Provider)"]
    end

    team -- "Fragen stellen (Browser)" --> bot
    fach -- "Fachwissen hochladen / löschen" --> bot
    team & fach -- "Anmeldung (SSO)" --> kc
    kcadmin -- "Benutzer & Rollen pflegen" --> kc
    bot -- "OIDC: Token, UserInfo, JWKS" --> kc
```

---

## 2. Container / Deployment

```mermaid
flowchart TB
    browser["🌐 Browser"]

    subgraph compose["docker-compose.yml"]
        app["<b>app</b><br/>Spring Boot 4.1.2 / Java 25<br/>+ Tesseract<br/>:8090"]
        pg[("<b>postgres</b><br/>pgvector/pgvector:pg16<br/>DB betriebsrente<br/>:5432")]
        ollama["<b>ollama</b><br/>llama3.2:3b<br/>nomic-embed-text<br/>:11434"]
        kc["<b>keycloak</b><br/>26.7.3 (start-dev)<br/>:8080"]
        kcdb[("<b>keycloak-db</b><br/>postgres:16-alpine")]
        init["<b>realm-init</b><br/>alpine (einmalig)<br/>rendert Realm-JSON"]
        vol[/"Volume realm_rendered"/]
    end

    browser -- "localhost:8091" --> app
    browser -- "localhost:8180 (Login-Seite)" --> kc
    app -- "JDBC" --> pg
    app -- "HTTP: Chat + Embeddings" --> ollama
    app -- "keycloak:8080 (Backchannel)" --> kc
    kc --> kcdb
    init -- "schreibt" --> vol
    vol -- "--import-realm" --> kc
```

| Dienst | Host-Port → Container | Volume | Zweck |
|---|---|---|---|
| `app` | 8091 → 8090 | – | Web-UI, REST-API, RAG-Logik |
| `postgres` | 5445 → 5432 | `pgdata` | Fachwissen, Vektoren, Audit/Chat-Tabellen |
| `ollama` | 11435 → 11434 | `ollama_data` | LLM- und Embedding-Inferenz (CPU), `OLLAMA_KEEP_ALIVE=24h` |
| `keycloak` | 8180 → 8080 | – | OIDC-Provider, Realm `betriebsrente` |
| `keycloak-db` | – | `keycloak_data` | Keycloak-Persistenz |
| `realm-init` | – | `realm_rendered` | Ersetzt `${KEYCLOAK_*}`-Platzhalter in der Realm-JSON (Build-Kontext statt Bind-Mount) |

**Zwei Keycloak-URLs:** Der Browser nutzt `http://localhost:8180` (öffentlicher Issuer),
die App spricht intern mit `http://keycloak:8080` (Backchannel für Token, UserInfo, JWKS).
Deshalb wird die `ClientRegistration` in `SecurityConfig` manuell aufgebaut statt per Issuer-Discovery.

---

## 3. Komponenten der Anwendung

```mermaid
flowchart TB
    subgraph web["Web-Schicht"]
        LC["LoginController<br/>GET /login"]
        CC["ChatController<br/>GET / · POST /api/chat"]
        AC["AdminController<br/>GET /admin · POST /admin/upload<br/>DELETE /admin/{id}"]
        AIC["AdminImportController<br/>CLI: --import=&lt;datei&gt;"]
        TPL["Thymeleaf: login.html,<br/>chat.html, admin.html"]
    end

    subgraph sec["Sicherheit"]
        SC["SecurityConfig<br/>OAuth2 Login · Rollen-Mapping<br/>OIDC-Logout"]
    end

    subgraph rag["RAG / Fachlogik"]
        RS["RagService<br/>Suche + Prompt + LLM"]
        CS["ChunkingService<br/>TokenTextSplitter"]
        DPS["DocumentParserService<br/>PDF · DOCX · TXT/MD"]
        IS["IngestionService<br/>JSON-Seed-Import"]
        SDI["SeedDataInitializer<br/>Import bei leerer DB"]
    end

    subgraph unused["Vorhanden, aber nicht angebunden"]
        AS["AuditService"]
        CES["ChatExportService (PDF)"]
        OCR["OcrService"]
    end

    subgraph data["Persistenz"]
        REPO["FachwissenRepository<br/>(Spring Data JPA)"]
        EM["EntityManager"]
        VS["VectorStore<br/>(PgVectorStore)"]
        CHAT["ChatClient / EmbeddingModel<br/>(Ollama)"]
    end

    CC --> RS
    CC --> REPO
    AC --> DPS
    AC --> CS
    AC --> VS
    AC --> EM
    AIC --> IS
    SDI --> IS
    IS --> CS
    IS --> VS
    IS --> EM
    RS --> VS
    RS --> CHAT
    VS -. "Embeddings" .-> CHAT
```

### Paketstruktur

```
de.team.betriebsrente
├── BetriebsrenteBotApplication      Einstiegspunkt
├── config/                          SecurityConfig, LoginController
├── controller/                      ChatController, AdminController, AdminImportController
├── model/                           JPA-Entitäten: Fachwissen, FachwissenChunks, AuditLog, ChatHistory
├── repository/                      FachwissenRepository
└── rag/                             RagService, ChunkingService, DocumentParserService,
                                     IngestionService, SeedDataInitializer,
                                     AuditService, ChatExportService, OcrService
```

---

## 4. Zentrale Abläufe

### 4.1 Anmeldung (OIDC Authorization Code + PKCE)

```mermaid
sequenceDiagram
    autonumber
    actor U as Browser
    participant A as App (Spring Security)
    participant K as Keycloak

    U->>A: GET /
    A-->>U: 302 → /login
    U->>A: GET /oauth2/authorization/keycloak
    A-->>U: 302 → localhost:8180/.../auth (state, nonce, PKCE)
    U->>K: Login-Formular (Benutzer/Passwort)
    K-->>U: 302 → /login/oauth2/code/keycloak?code=…
    U->>A: Callback mit Code
    A->>K: POST keycloak:8080/.../token (Client-Secret + Code-Verifier)
    K-->>A: ID-Token + Access-Token
    A->>K: GET /userinfo, /certs
    A->>A: realm_access.roles → ROLE_USER / ROLE_ADMIN<br/>(ADMIN erhält zusätzlich ROLE_USER)
    A-->>U: 302 → / (Session-Cookie)
```

### 4.2 Frage beantworten (RAG)

```mermaid
sequenceDiagram
    autonumber
    actor U as Browser (chat.html)
    participant C as ChatController
    participant R as RagService
    participant V as PgVectorStore
    participant O as Ollama

    U->>C: POST /api/chat {frage, kategorie} + CSRF-Header
    C->>C: Validierung (nicht leer, ≤ 2000 Zeichen) sonst 400
    C->>R: ask(frage, kategorie)
    R->>O: Embedding der Frage (nomic-embed-text)
    R->>V: similaritySearch(topK=3, Schwelle 0.35,<br/>Filter kategorie == X falls ≠ "Alle")
    V-->>R: Top-Abschnitte inkl. Metadaten
    R->>O: Systemprompt (Regeln + KONTEXT) + Frage<br/>llama3.2:3b, temperature 0.2, num-predict 384
    O-->>R: Antworttext
    R-->>C: Answer(antwort, quellen)
    C-->>U: {antwort, quellen[titel, kategorie, Textvorschau 400 Z.]}
    U->>U: HTML-Escaping, Anzeige mit Quellen-Chips
```

Ohne passenden Kontext antwortet das Modell laut Systemprompt mit
*„Dazu liegt keine freigegebene Fachinfo vor. Bitte Fachbereich fragen.“*

### 4.3 Dokument hochladen (nur ADMIN)

```mermaid
sequenceDiagram
    autonumber
    actor F as Fachbereich (admin.html)
    participant A as AdminController
    participant P as DocumentParserService
    participant DB as PostgreSQL
    participant CH as ChunkingService
    participant V as PgVectorStore
    participant O as Ollama

    F->>A: POST /admin/upload (Datei, Kategorie, Quelle, Titel)
    A->>P: parse(file) → Text, Seiten
    alt weniger als 50 Zeichen
        A-->>F: {error: "zu wenig Text – gescanntes PDF?"}
    else
        A->>DB: INSERT fachwissen (Originaltext, Metadaten)
        A->>CH: chunk(text, {fachwissen_id, titel, kategorie, quelle})
        CH-->>A: Abschnitte (≈650 Tokens) + chunk_index/count
        A->>V: add(chunks)
        V->>O: Embeddings je Abschnitt
        V->>DB: INSERT vector_store
        A-->>F: {success, chunks, chars, pages}
    end
```

### 4.4 Dokument löschen (nur ADMIN)

`DELETE /admin/{id}` entfernt in einer Transaktion
1. alle Vektoren mit `metadata.fachwissen_id == id` aus `vector_store`,
2. Altdaten aus `fachwissen_chunks`,
3. den Eintrag in `fachwissen`.

### 4.5 Seed-Import beim Start

`SeedDataInitializer` prüft beim Start, ob `fachwissen` leer ist. Falls ja, importiert der
`IngestionService` `testdaten/fachwissen_seed.json`. Die `training_phrases` jedes Eintrags werden als
„Beispielfragen“ an den Text gehängt, damit typische Formulierungen bei der Vektorsuche besser treffen.
Ein manueller Import ist per Startargument `--import=<pfad>` möglich.

---

## 5. Datenmodell

```mermaid
erDiagram
    fachwissen ||--o{ vector_store : "metadata.fachwissen_id"
    fachwissen ||--o{ fachwissen_chunks : "fachwissen_id (Altlast, leer)"

    fachwissen {
        bigserial id PK
        varchar titel
        varchar kategorie
        varchar quelle
        text original_text
        int version
        varchar erstellt_von
        timestamp created_at
    }
    vector_store {
        uuid id PK
        text content
        json metadata "fachwissen_id, titel, kategorie, quelle, chunk_index, chunk_count"
        vector embedding "768 Dim., HNSW-Index, Kosinus"
    }
    fachwissen_chunks {
        bigserial id PK
        bigint fachwissen_id FK
        text chunk_text
        vector embedding
    }
    audit_log {
        bigserial id PK
        timestamp timestamp
        varchar username
        varchar action
        text details
    }
    chat_history {
        bigserial id PK
        varchar session_id
        varchar username
        text frage
        text antwort
    }
```

- `fachwissen`, `fachwissen_chunks`, `audit_log`, `chat_history`: per **Flyway** (`V1__init.sql`, `V2__audit_and_chat.sql`); Hibernate läuft mit `ddl-auto: validate`.
- `vector_store`: wird von **Spring AI** angelegt (`initialize-schema: true`) und ist die eigentliche Wissensbasis der Suche.
- `fachwissen_chunks`, `audit_log`, `chat_history` werden derzeit nicht beschrieben.

---

## 6. Sicherheit

| Thema | Umsetzung |
|---|---|
| Authentifizierung | Ausschließlich Keycloak (OIDC); keine lokalen Passwörter in der App |
| Autorisierung | `/admin/**`, `/api/admin/**` → `ROLE_ADMIN`; alles andere → `ROLE_USER` oder `ROLE_ADMIN`; `/login`, `/error`, `/css/**`, `/js/**`, OAuth2-Endpunkte öffentlich |
| Rollen-Mapping | Keycloak-Realm-Rollen `USER`/`ADMIN` aus `realm_access.roles` → Spring-Authorities |
| CSRF | Aktiv; Token per `<meta>`-Tag, von `fetch()` als Header mitgesendet |
| XSS | Frage und LLM-Antwort werden im Browser vor dem Einfügen HTML-maskiert |
| Logout | `OidcClientInitiatedLogoutSuccessHandler` beendet auch die Keycloak-Sitzung |
| Geheimnisse | `KEYCLOAK_*` über Umgebungsvariablen/`.env`; die Standardwerte sind nur für lokale Entwicklung gedacht |
| Datenschutz | LLM und Embeddings laufen lokal in Ollama; keine externen KI-Dienste |

---

## 7. Konfiguration (`application.yml`)

| Schlüssel | Wert | Bedeutung |
|---|---|---|
| `spring.ai.ollama.chat.options.model` | `llama3.2:3b` | Antwortmodell |
| `…chat.options.temperature` | `0.2` | Wenig Variation, faktentreu |
| `…chat.options.num-predict` | `384` | Max. Antwortlänge in Tokens |
| `spring.ai.ollama.embedding.options.model` | `nomic-embed-text` | Embedding-Modell (768 Dim.) |
| `spring.ai.vectorstore.pgvector.*` | HNSW, `COSINE_DISTANCE`, 768 | Vektorindex |
| `app.chunk.size` | `650` | Abschnittsgröße in Tokens |
| `app.keycloak.issuer-uri` / `backchannel-issuer-uri` | `localhost:8180` / `keycloak:8080` | Öffentlicher bzw. interner Keycloak |
| `app.ocr.*` | `deu+eng` | OCR-Einstellungen (Service nicht angebunden) |
| `RagService.TOP_K` / `SIMILARITY_THRESHOLD` | `3` / `0.35` | Anzahl und Mindestähnlichkeit der Kontextabschnitte (im Code) |

---

## 8. Qualitätssicherung

| Ebene | Umfang | Ausführung |
|---|---|---|
| Unit-Tests | 25 Tests (JUnit 5 + Mockito) für Controller, Services, Security | `mvn verify` (benötigt JDK 25) |
| UI-E2E | Playwright + TestNG für Chat und Admin, Backend-Antworten simuliert | `mvn -Pe2e … exec:java` (siehe README) |
| Coverage | JaCoCo-Report in `target/site/jacoco` | Phase `verify` |
| Bewertungsdaten | `testdaten/eval_dataset.json` | Manuelle Auswertung |

---

## 9. Bekannte Grenzen und nächste Schritte

1. **Antwortzeit:** Die Inferenz läuft nur auf der CPU. Mit 6 vCPUs/8 GB dauert eine Antwort etwa 20–60 s, unter 5 s geht es nur mit GPU.
2. **Nicht angebundene Funktionen:** `AuditService` (Audit-Log, Chatverlauf), `ChatExportService` (PDF-Export) und `OcrService` (gescannte PDFs) sind implementiert, werden aber nirgends aufgerufen.
3. **Altlast `fachwissen_chunks`:** Die Tabelle ist leer und wird nicht genutzt. Sie könnte per Flyway-Migration entfernt werden.
4. **Admin-Kategorien fest verdrahtet:** Das Upload-Formular bietet feste Kategorien an, der Chat-Filter liest sie dagegen aus der Datenbank.
5. **Kein Chunk-Overlap:** `TokenTextSplitter` unterstützt keine Überlappung zwischen Abschnitten.
6. **`.doc` (Word 97–2003)** wird wie DOCX behandelt und schlägt beim Parsen fehl. Dafür wäre `HWPFDocument` nötig.
7. **Keine Streaming-Antworten:** Die UI wartet auf die vollständige Antwort. Mit `ChatClient.stream()` und SSE würde die gefühlte Wartezeit deutlich sinken.

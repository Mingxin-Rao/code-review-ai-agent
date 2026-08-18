# CodeGuardian AI — AI Code Review Agent

CodeGuardian AI is a code review system built on Spring Boot 3.4 and Java 21. It reviews code with an
LLM, cross-checks it with static analysis, and produces a report of the issues it finds — bugs, security
vulnerabilities, performance problems and style violations.

Reviews can be run over a snippet, a single file, a directory, a local project, or a cloned Git repository.

## How it works

A review combines three sources of findings:

- **LLM review** via Spring AI's `ChatClient`. The model is given the code with line numbers and asked to
  return findings as structured JSON. OpenAI, Qwen, DeepSeek and a local Ollama model are all configurable
  behind the same interface.
- **Tool calling.** The model can invoke two analysers during a review: a Semgrep scan and a JavaParser
  syntax check. Their findings are recorded even if the model does not mention them in its answer.
- **A standalone rule engine** with four bundled rule packs (Google Java, Alibaba Java, Airbnb JS/TS,
  PEP 8). This runs *instead of* the LLM when `rulesOnly` is set on the request.

Two things make repeated reviews cheaper:

- **RAG context.** A knowledge base of uploaded documents is indexed in PostgreSQL with PGVector, and
  queried using hybrid retrieval — vector similarity plus an in-memory BM25 index.
- **A semantic fingerprint cache** in Redis. Unchanged code hits an exact SHA-256 match; near-identical
  code is recalled through SimHash bucketing and filtered by Hamming distance.

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1, Spring AI 1.0.0-M4 |
| Database | PostgreSQL with the `pgvector` extension |
| Cache | Redis (via Sa-Token for sessions, and the fingerprint cache) |
| Object storage | MinIO (knowledge base documents) |
| Build | Maven |
| Other | Lombok, Jackson, JavaParser, Thymeleaf, OpenHTMLtoPDF |

## Getting started

### Requirements

- JDK 21 or newer
- Maven 3.6+
- PostgreSQL with `pgvector`, and Redis — both provided by the bundled `docker-compose.yml`
- Semgrep on your `PATH` if you want the Semgrep analyser (`pip install semgrep`). Without it, that one
  tool call fails; the rest of the review still runs.

No font is bundled for PDF reports, because fonts with full CJK coverage are generally not
redistributable. PDF generation works out of the box using the renderer's built-in fonts. If you need
CJK glyphs in PDFs, drop a redistributable TrueType font (for example
[Noto Sans CJK](https://github.com/notofonts/noto-cjk)) at
`src/main/resources/fonts/ArialUnicode.ttf` — that path is gitignored.

### 1. Start the infrastructure

```bash
docker-compose up -d
```

This starts PostgreSQL (with `pgvector`) on 5432 and Redis on 6379, and applies the scripts in
`database/` on first run. Both use the passwords in `docker-compose.yml`; override `DB_PASSWORD` and
`REDIS_PASSWORD` to change them.

### 2. Configure a model provider

Every credential is read from the environment. Set the provider you intend to use:

```bash
export OPENAI_API_KEY=sk-...
# optional overrides
export OPENAI_BASE_URL=https://api.openai.com
export OPENAI_MODEL=gpt-3.5-turbo
```

Qwen (`QWEN_API_KEY`, `QWEN_BASE_URL`, `QWEN_MODEL`) and a local Ollama instance (`OLLAMA_BASE_URL`,
`OLLAMA_MODEL`) are configured the same way. Note that `spring.ai.enabled` defaults to `false` in
`application.yml` — set it to `true` to enable the LLM path.

### 3. Run

```bash
mvn spring-boot:run
```

| | |
|---|---|
| Login | http://localhost:8080/login |
| Review page | http://localhost:8080/review |
| Home | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |

Default account: `admin` / `admin123` — change it before exposing the app anywhere.

### Running the tests

```bash
mvn test
```

Integration tests that need PostgreSQL and Redis are tagged `integration` and skipped by default. To
include them, start the containers first and then:

```bash
mvn test -Pintegration-tests
```

## API

All review endpoints are under `/api/review`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/review/snippet` | Review a code snippet |
| `POST` | `/api/review/file` | Review a single file |
| `POST` | `/api/review/directory` | Review a directory |
| `POST` | `/api/review/project` | Review a project |
| `POST` | `/api/review/git` | Review a cloned Git repository |
| `POST` | `/api/review/git/clone` | Clone a repository and return its file tree |
| `GET` | `/api/review/task/{taskId}` | Task detail |
| `GET` | `/api/review/task/{taskId}/findings` | Findings for a task |
| `GET` | `/api/review/history` | Search review history |
| `DELETE` | `/api/review/task/{taskId}` | Delete a task and its findings |

Reports are under `/api/report`:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/report/{taskId}` | Generate the report |
| `GET` | `/api/report/{taskId}/html` | HTML report |
| `GET` | `/api/report/{taskId}/markdown` | Markdown report |
| `GET` | `/api/report/{taskId}/pdf` | PDF report |

There is also a CI/CD entry point (`POST /api/v1/cicd/trigger`, `GET /api/v1/cicd/status/{taskId}`) for
polling from Jenkins or a CI pipeline, and a GitLab-compatible webhook at `POST /api/v1/webhook/gitcode`.

### Example

```bash
curl -X POST http://localhost:8080/api/review/snippet \
  -H 'Content-Type: application/json' \
  -d '{
    "reviewType": "SNIPPET",
    "codeSnippet": "public class Test { void m(String s) { System.out.println(s.length()); } }",
    "language": "java",
    "taskName": "Snippet review"
  }'
```

To review with the rule packs only and skip the LLM entirely:

```bash
curl -X POST http://localhost:8080/api/review/snippet \
  -H 'Content-Type: application/json' \
  -d '{
    "reviewType": "SNIPPET",
    "codeSnippet": "var x = 1;",
    "language": "javascript",
    "rulesOnly": true,
    "ruleTemplate": "AIRBNB"
  }'
```

## Database

`docker-compose up` applies the schema automatically. To set it up by hand:

```bash
createdb -U postgres code_guardian
psql -U postgres -d code_guardian -f database/schema.sql
psql -U postgres -d code_guardian -f database/init_permissions.sql
psql -U postgres -d code_guardian -f database/init_data.sql   # optional sample data
```

`schema.sql` is the current schema; `init_permissions.sql` seeds roles, permissions and the default admin
user; `init_data.sql` adds sample tasks and findings for a populated UI. The dated directories under
`database/` are earlier snapshots kept for reference. See [database/README.md](database/README.md).

## Project layout

```
src/main/java/com/codeguardian/
├── CodeReviewApplication.java   # entry point
├── config/                      # Spring, Sa-Token and AI configuration
├── controller/                  # REST endpoints and page controllers
├── dto/                         # request/response objects
├── entity/                      # JPA entities
├── enums/                       # review type, severity, category, status
├── exception/                   # global exception handling
├── repository/                  # Spring Data repositories
├── service/
│   ├── ai/                      # prompts, tool calling, output parsing
│   ├── cache/                   # semantic fingerprint cache
│   ├── integration/             # CI/CD quality gate, Git feedback
│   ├── pdf/                     # PDF generation
│   ├── rag/                     # knowledge base, hybrid retrieval
│   └── rules/                   # rule engine and rule packs
├── task/                        # scheduled dashboard aggregation
└── util/

src/main/resources/
├── application.yml
├── knowledge/rules.json         # RAG seed corpus
├── rules/*.json                 # Google, Alibaba, Airbnb, PEP 8 rule packs
├── static/                      # CSS and JS
└── templates/                   # Thymeleaf views

database/                        # schema and seed scripts
```

## Known limitations

- A file larger than the model's context window is sent whole; there is no chunking or map-reduce step,
  so very large files may be truncated by the provider.
- If a provider call fails the review fails; there is no automatic failover to another provider.
- The Git feedback and webhook integration targets a GitLab-compatible API (GitCode); GitHub is not
  wired up.
- `service/ai/impl/` and `service/ai/factory/` contain an earlier hand-rolled provider abstraction that
  the Spring AI `ChatClient` path replaced. It is no longer referenced.

## Acknowledgements

Based on [code-review-ai-agent](https://gitcode.com/dv-susan/code-review-ai-agent) by **dv-susan**,
which this project builds on.

## License

MIT

# FinWise — Offline Personal Finance Manager

FinWise is a complete, offline-first personal finance management system for Windows. It runs entirely on your own machine: a Java 17 / Spring Boot server, a local SQLite database, and a Thymeleaf web UI served from `http://localhost:8080`. No cloud service, no external API, no internet connection is required — financial records never leave the computer.

- **Artifact:** `com.finance:offline-finance-manager:1.0.0`
- **Stack:** Java 17, Spring Boot 3.1, Spring MVC / Data JPA / Security / Thymeleaf, SQLite, Apache POI, PDFBox, OpenCV, Tess4J
- **Entry point:** `com.finance.app.OfflineFinanceApplication`

---

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Build and run](#build-and-run)
- [Default credentials](#default-credentials)
- [Feature tour](#feature-tour)
- [Local data layout](#local-data-layout)
- [Configuration](#configuration)
- [Backups and restore](#backups-and-restore)
- [Security model](#security-model)
- [Project structure](#project-structure)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)

---

## Features

### Core finance
- Local registration, login, logout, profile editing, and account deletion
- BCrypt-hashed passwords and per-user record scoping
- Multiple bank/cash **accounts** with balances
- **Income** and **expense** entry, editing, deletion, categories, and notes
- **Budgets** per category with progress tracking and limit alerts
- **Financial goals** with target amounts, target dates, priority, and status
- **Categories** with user-owned custom categories (default categories protected)
- **Recurring** income and expenses, materialized automatically every day
- **Search and filters** across text, category, date range, amount, and recurrence
- **Receipt images** uploaded per transaction and downloaded by the owner only

### Import, OCR, and Scan & Fill
- Import from **PDF**, **image**, **CSV**, and **Excel** (`.xls` / `.xlsx`)
- OCR pipeline: OpenCV preprocessing (grayscale, threshold, deskew) → Tess4J text extraction
- **Scan & Fill** assistant that parses OCR text into a prefilled transaction form
- Import validation, per-row editing, rejection reasons, and cleanup
- Duplicate detection against existing and previously imported transactions
- Import history browser

### Analytics and insights
- Dashboard with monthly income/expense overview and category breakdown
- **Analytics** engine and charts (Chart.js, served locally)
- **Financial health score** computed server-side from real local records
- **Cash-flow forecasting** from recent monthly history
- **Upcoming bills** calculation with due-date reminders
- **Smart insights** and report generation
- Large-expense alerts, bill reminders, weekly and monthly summaries
- Local in-app **notifications** center

### FinancePlus workspace (`/finance-plus`)
A separately passcode-locked workspace inside the app:
- Recurring rules engine with scheduled automation
- Notification preferences and read/unread handling
- Encrypted attachment vault with its own unlock/lock state
- Import / export for the workspace
- Per-user backup history, download, restore, and delete

### Automation and extras
- AES-256-GCM encrypted backups, scheduled daily, with history, restore, and retention
- Offline rule-based **chatbot** assistant with persisted conversation history
- **Spending habit rules** (need vs. want, hostel food, streak detection) with results
- **Audit log** of user activity
- Light/dark and accent **themes**, token-based design system
- **Localization** for 25 languages via local JSON files: English, Spanish, French, German, Italian, Portuguese, Russian, Turkish, Arabic, Persian, Hebrew, Urdu, Hindi, Marathi, Bengali, Nepali, Punjabi, Gujarati, Tamil, Telugu, Kannada, Malayalam, Japanese, Korean, Chinese (no CDN)
- Local app lock passcode after authentication
- CSV and multi-sheet Excel export

---

## Requirements

| Requirement | Version / path |
| --- | --- |
| Java (JDK) | 17 or newer |
| Maven | 3.8 or newer |
| OCR language data | `data/tessdata/eng.traineddata` (already bundled) |
| OpenCV native runtime | required only when processing images |
| OS | Windows (primary); `run.sh` provided for Linux/macOS |

---

## Build and run

### One-command start (Windows)

```powershell
.\run.bat
```

`run.bat` verifies Java and Maven, creates `data/` and `logs/`, stops any running instance, builds with `mvn clean install -DskipTests`, launches the JAR on port 8080, and opens the browser once the port accepts connections.

### One-command start (Linux / macOS)

```bash
./run.sh
```

### Manual

```powershell
mvn clean verify          # compile + run the test suite
mvn spring-boot:run       # run from source
```

Or build and run the executable JAR:

```powershell
mvn clean package
java -jar target/offline-finance-manager-1.0.0.jar
```

Then open <http://localhost:8080>.

> Stop any running FinWise process before `mvn clean`, otherwise Maven cannot replace the JAR on Windows.

---

## Default credentials

- Username: `admin`
- Password: `admin123`

Change the default password before storing real financial data.

---

## Feature tour

| Area | Route | What it does |
| --- | --- | --- |
| Dashboard | `/dashboard` | Monthly overview, category breakdown, charts |
| Accounts | `/accounts` | Bank and cash accounts, balances |
| Income | `/income` | Income records, filters, export |
| Expenses | `/expenses` | Expense records, filters, receipts |
| Budgets | `/budgets` | Per-category limits and progress |
| Goals | `/goals` | Savings goals and status |
| Categories | `/categories` | Manage custom categories |
| Analytics | `/analytics` | Trends, forecast, health score |
| Reports | `/reports` | Report generation |
| Import | `/import` | Upload PDF/image/CSV/Excel |
| Import preview / edit / history | `/import/...` | Validate, fix, and commit rows |
| Scan & Fill | `/scan` | OCR-assisted transaction capture |
| Notifications | `/notifications` | Reminders and alerts |
| Habits | `/habits` | Spending habit rule results |
| Chatbot | `/chatbot` | Offline assistant |
| Audit | `/audit` | Activity history |
| FinancePlus | `/finance-plus` | Passcode-locked advanced workspace |
| Settings | `/settings` | Themes, language, profile, data & backup |
| App lock | `/app-lock` | Local passcode gate |

---

## Local data layout

Everything lives under the project directory:

```
data/
├── finance.db              SQLite database (created automatically)
├── Finwise_Data.xlsx       Excel workbook mirror
├── finance-e2e.db          Test database (e2e profile)
├── import-images/          Scan & Fill / import uploads
├── uploads/profile/        Profile pictures
├── finance-plus/           FinancePlus workspace data and backups
├── backups/                Encrypted .finwise backups and .backup.key
└── tessdata/               eng.traineddata for OCR
logs/
└── finance.log             Application log
```

Sample import files for testing live in `img/` (CSV, PDF, XLS, XLSX, PNG, JPEG, plus a handwritten note).

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:sqlite:data/finance.db
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect

app.workbook-path=data/Finwise_Data.xlsx
app.import-image-dir=data/import-images
app.tessdata-dir=data/tessdata
app.data-dir=data/

app.backup.enabled=true
app.backup.cron=0 0 2 * * *
app.backup.retention-days=30
# app.backup.encryption-key=replace-with-a-long-random-secret

server.port=8080
logging.file.name=logs/finance.log
```

Language/theme preferences are stored per user in the database, not in properties.

---

## Backups and restore

- Manual backups: **Settings → Data & Backup**.
- Scheduled backups run daily at **02:00** and are retained for **30 days**.
- A backup contains a consistent SQLite snapshot plus the Excel workbook, encrypted with **AES-256-GCM**.
- By default a random local key is generated at `data/backups/.backup.key`. For managed environments, set a stable secret with `app.backup.encryption-key`.
- Restoring is global for local application data. FinWise writes an encrypted safety backup before restoring.

---

## Security model

- CSRF protection is enabled for browser forms and AJAX requests.
- Destructive operations use authenticated `POST` endpoints, never `GET`.
- Every record query is scoped to the authenticated user.
- Category deletion verifies ownership and blocks default categories.
- Import image and receipt downloads require the owning account.
- Backup filenames and archive entries are validated to prevent path traversal.
- Passwords and the app-lock passcode are BCrypt-hashed.
- Uploaded images are stored outside the web root and served only through authorized endpoints.
- Fingerprint and face unlock are delegated to the OS or browser profile; FinWise stores no biometric data.

---

## Project structure

```
src/main/java/com/finance/
├── app/            Application entry point
├── config/         Security, web MVC, interceptors, exception handlers
├── controller/     Authenticated MVC and JSON endpoints
├── service/        Finance, import, OCR, forecast, export, security services
│   └── excel/      Workbook storage, settings, notifications, health score, backup
├── model/
│   ├── entity/     JPA entities
│   └── dto/        Request/response DTOs
├── repository/     User-scoped JPA repositories
├── security/       UserDetailsService, principal
├── chatbot/        Offline assistant (controller, service, DTOs)
├── habits/         Spending habit rules
├── financeplus/    FinancePlus models, repositories, services, web
└── util/           Validation helpers

src/main/resources/
├── application.properties
├── application-e2e.properties
├── templates/      Thymeleaf pages (+ fragments, error)
└── static/         CSS, JS, local Chart.js, 25 locale JSON files

src/test/java/com/finance/   Unit and Spring integration tests
archive/                      Legacy notes, logs, and historical documents
img/                          Sample import fixtures
```

---

## Testing

Tests run under the `e2e` Spring profile and write only to `target/` — never to the production `data/` directory.

```powershell
mvn test
mvn clean verify
```

`application-e2e.properties` points the datasource at `target/finance-test.db`, disables scheduled backups, and redirects the workbook, import images, tessdata, and logs into `target/`.

Existing suites:

| Test | Focus |
| --- | --- |
| `UserRegistrationLoginTest` | Registration, login, authentication |
| `FinanceFeatureIntegrationTest` | CRUD and integration flows |
| `FeatureRegressionTest` | Regression coverage for shipped features |
| `OfflineFeatureTest` | Offline/import/OCR/export behaviour |
| `FinancePlusRecurringServiceTest` | Recurring rules engine |
| `FinancePlusControllerTest` | FinancePlus web layer |

Coverage includes authentication, CRUD, user isolation, CSRF, import validation and fixtures, duplicate detection, OCR/PDF fallback, category authorization, recurring transactions, forecasting, receipt access, encrypted restore, and exports.

---

## Troubleshooting

**Port already in use** — set `server.port=8081` in `application.properties`.

**Java version error** — confirm `java -version` reports 17+.

**Build fails to replace the JAR** — close the running FinWise process (or run `run.bat`, which stops it for you).

**OCR returns nothing** — check `data/tessdata/eng.traineddata` exists; very poor scan quality or handwriting reduces accuracy.

**Database looks corrupted** — stop the app, delete `data/finance.db`, and restart; the schema is recreated automatically. Restore from `data/backups/` if you have one.

**Startup failure** — check `logs/finance.log`.

---

## Limitations

- No bank synchronization and no cloud synchronization.
- OCR quality depends on scan quality and the installed Tesseract language data.
- Cash-flow forecasts use recent local history and are estimates, not financial advice.
- SQLite suits single-user local use; it is not designed for heavy concurrent writes.
- Keep the application directory **and** the backup encryption key backed up separately.

---

## Privacy

All data — accounts, transactions, receipts, credentials, backups — stays on this machine. FinWise makes no outbound network requests at runtime; the chatbot, analytics, OCR, and forecasting all run locally.

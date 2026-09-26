#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_purpose_report.py
==========================

Walks the whole FinWise project and writes a plain-English
"What is this file for?" line for every file it finds.

Report order (test first, then main, then resources, then the rest):

    1. Test sources      src/test/**
    2. Main sources      src/main/java/**
    3. Resources         src/main/resources/**   (settings, pages, styles, scripts)
    4. Project files     pom.xml, README, run scripts, dotfiles
    5. Runtime folders   data/, logs/, img/, target/   (grouped, they are repetitive)

How a purpose is worked out, best guess first:

    1. A hand-written note for a known file         (CURATED / JAVA_CURATED)
    2. Analysis of the file itself                  (Java / HTML / CSS / JS / settings ...)
    3. What the class name or file name suggests    (templates + word list)
    4. What the file extension means                (EXTENSIONS)

Usage
-----
    python tools/generate_purpose_report.py
    python tools/generate_purpose_report.py --output PROJECT_FILE_PURPOSE.txt
    python tools/generate_purpose_report.py --verbose           # one line per file, no grouping
    python tools/generate_purpose_report.py --include-target    # also list every target/ file
    python tools/generate_purpose_report.py --root H:\\javaproject
    python tools/generate_purpose_report.py --stdout           # print instead of writing

Only the report file is written. Nothing in the project is changed.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from datetime import datetime

# ---------------------------------------------------------------------------
# 1. Hand-written notes for files a name cannot explain
# ---------------------------------------------------------------------------

CURATED = {
    "pom.xml":
        "Build file for Maven. Lists the project name and version, the Java version, the main "
        "class to run, and every library needed (Spring Boot, SQLite, Lombok, OCR, PDF, Excel).",
    "README.md":
        "The project instruction booklet: what the app does, how to install, run, configure, test "
        "and troubleshoot it.",
    "run.bat":
        "Windows launcher. Checks Java and Maven, makes the data and logs folders, stops any copy "
        "already running, builds the project, starts the program on port 8080, then opens the browser.",
    "run.sh":
        "Linux and Mac launcher. Does the same steps as run.bat.",
    "fix_mojibake.py":
        "Repair tool. Finds text files whose characters were saved in the wrong encoding (broken "
        "letters) and rewrites them correctly.",
    ".gitignore":
        "Tells Git which files must never be uploaded: build output, database, uploads, backups "
        "and logs.",
    "src/main/resources/application.properties":
        "Main settings file. Holds the port, the database address, how tables are created, the "
        "upload size limit, the default admin login, where files are stored, when backups run and "
        "how long they are kept, and where the log file goes.",
    "src/main/resources/application-e2e.properties":
        "Settings used only while testing. Points the database and every file path into the build "
        "folder, so tests can never touch real data.",
    "data/finance.db":
        "The live database. Holds every user, account, income, expense, budget, goal, category, "
        "alert and setting.",
    "data/finance-e2e.db":
        "Throwaway database used only by the automated tests.",
    "data/Finwise_Data.xlsx":
        "Excel copy of the financial data, one sheet per record type.",
    "data/backups/.backup.key":
        "The secret key that locks the backup files. Keep a separate copy: without it older "
        "backups cannot be opened.",
}

# Notes matched anywhere in the path (checked before the file-type rules)
SUBSTRING_CURATED = [
    (".vscode/settings.json", "Editor settings for this project (VS Code / Java)."),
    (".vscode/extensions.json", "Editor extensions that are recommended for this project."),
    (".github/modernize/java-upgrade/hooks/scripts/recordToolUse",
     "Hook script for the GitHub Java upgrade tool: it records which tools were used."),
    (".github/modernize/java-upgrade/.gitignore", "Ignore rules for the Java upgrade tool folder."),
    (".github/modernize", "Helper scripts for the GitHub Java upgrade tool."),
    ("C/tmp_pom", "Old scratch file used while checking the build file. Not used by the app."),
]

# ---------------------------------------------------------------------------
# 2. Java reading
# ---------------------------------------------------------------------------

PKG_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)
TYPE_RE = re.compile(
    r"^[ \t]*(?:@[\w.]+(?:\([^)]*\))?[ \t]*\n)*"
    r"[ \t]*(?:public\s+)?(?:final\s+|abstract\s+)?"
    r"(class|interface|enum|record)\s+(\w+)"
    r"(?:\s*<[^>{]*>)?"
    r"(?:\s+extends\s+([\w.]+(?:\s*<[^>]*>)?))?"
    r"(?:\s+implements\s+([\w.]+(?:\s*<[^>]*>)?(?:\s*,\s*[\w.]+(?:\s*<[^>]*>)?)*))?"
    r"[^{;]*\{",
    re.M,
)
MAPPING_RE = re.compile(
    r"@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b\s*(\(([^)]*)\))?"
)
ANNOT_RE = re.compile(r"@([A-Z]\w*)")
TEST_METHOD_RE = re.compile(r"@Test\b")
SCHEDULE_RE = re.compile(r"@Scheduled\s*\(([^)]*)\)")
TABLE_RE = re.compile(r"@Table\s*\(\s*name\s*=\s*\"([^\"]+)\"")
FIELD_RE = re.compile(r"^\s*private\s+(?:final\s+)?[\w<>\[\],.?\s]+?\s+(\w+)\s*[;=]", re.M)
METHOD_RE = re.compile(
    r"^[ \t]*public\s+(?:static\s+|final\s+|synchronized\s+|abstract\s+|default\s+)*"
    r"[\w<>\[\],.?\s]+?\s+(\w+)\s*\(",
    re.M,
)
LOMBOK_ANNOTS = ("Getter", "Setter", "Data", "Builder", "AllArgsConstructor",
                 "NoArgsConstructor", "RequiredArgsConstructor", "Value")

VERBS = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT",
         "DeleteMapping": "DELETE", "PatchMapping": "PATCH", "RequestMapping": ""}

# Words inside a class name, written out for humans
WORDS = {
    "FinancePlus": "FinancePlus", "AppLock": "the app lock", "OpenCV": "image processing",
    "Tess4J": "text reading (OCR)", "OCR": "text reading (OCR)", "Ocr": "text reading (OCR)",
    "PDFBox": "PDF files", "PDF": "PDF files", "Pdf": "PDF files", "POI": "Excel files",
    "Poi": "Excel files", "Excel": "Excel files", "DTO": "", "Dto": "",
    "Recurring": "repeat bills and income", "Transaction": "transactions",
    "Category": "categories", "Attachment": "stored files", "Backup": "backups",
    "Notification": "alerts", "Settings": "settings", "Profile": "the profile",
    "Password": "passwords", "App": "app", "Scan": "receipt scanning",
    "Import": "file import", "Export": "export", "Document": "uploaded documents",
    "Chat": "the chatbot", "Chatbot": "the chatbot", "Habit": "spending habits",
    "Rule": "rules", "Spending": "spending", "Goal": "goals", "Budget": "budgets",
    "Income": "income", "Expense": "expenses", "Account": "accounts", "User": "the user",
    "Lock": "lock", "Health": "the health score", "Insight": "insights", "Smart": "smart",
    "Cash": "cash", "Flow": "flow", "Forecast": "forecasting", "Duplicate": "duplicate",
    "Validation": "validation", "Finance": "finance", "Analytics": "analytics",
    "Report": "reports", "History": "history", "Vault": "the vault", "Access": "access",
    "Global": "the whole app", "Web": "web", "Mvc": "layer", "Security": "security",
    "UserDetails": "sign-in", "Principal": "the signed-in user", "Conversation": "conversations",
    "Context": "context", "Request": "requests", "Response": "responses", "Message": "messages",
    "Data": "data", "Workbook": "the Excel workbook", "Restore": "restore",
    "Scheduler": "scheduling", "Database": "the database", "Entity": "records",
    "Local": "local", "Default": "default", "Need": "need", "Want": "want",
    "Hostel": "hostel", "Food": "food", "Streak": "streaks", "Abstract": "base",
    "Extracted": "extracted", "Imported": "imported", "Plus": "Plus", "Init": "",
    "Integration": "integration", "Regression": "regression", "Feature": "feature",
    "Offline": "offline", "Registration": "registration", "Login": "sign-in",
    "Impl": "", "Detail": "", "Details": "", "Info": "", "Extra": "", "Open": "",
    "Tess": "", "J": "", "CV": "", "4J": "", "Id": "", "Base": "",
    "Service": "", "Repository": "", "Controller": "", "Engine": "", "Parser": "",
    "Detector": "", "Handler": "", "Interceptor": "", "Extractor": "", "Processor": "",
    "Preprocessor": "", "Test": "", "Config": "", "Initializer": "", "Automation": "",
    "ControllerAdvice": "", "ExceptionHandler": "", "Exception": "",
}

# What a class-name ending means
KINDS = {
    "Controller": "Web layer: the pages and addresses for {s}.",
    "Service": "Business rules for {s}.",
    "Repository": "Data access: the database questions about {s}.",
    "Config": "Configuration for {s}.",
    "Initializer": "Startup job that prepares {s}.",
    "Scheduler": "Timed job: runs {s} automatically.",
    "Automation": "Timed job: runs {s} automatically.",
    "Engine": "Calculation engine that works out {s}.",
    "Parser": "Parser: turns {s} text into clean, structured records.",
    "Detector": "Detection logic that spots {s}.",
    "Handler": "Handles requests and failures for {s}.",
    "Interceptor": "Checks every request for {s} before the page opens.",
    "Extractor": "Pulls {s} out of raw text.",
    "Processor": "Processes {s} into usable data.",
    "Preprocessor": "Cleans up {s} before the text reader runs.",
    "ExceptionHandler": "Turns {s} problems into friendly error pages.",
    "Dto": "Small data package used to pass {s} around.",
}

# Hand-written notes for individual Java classes
JAVA_CURATED = {
    "OfflineFinanceApplication":
        "The starting point of the program, like the on switch. Starts the web server and turns on "
        "the automatic timers (daily backup, daily repeat bills).",
    "SecurityConfig":
        "Decides who is allowed in: password scrambling, which pages are public, which need a "
        "sign-in, CSRF protection, and logout.",
    "WebConfig":
        "Web settings: where the browser files are served from, plus the resource handlers.",
    "WebMvcConfig":
        "Web settings: registers the extra checks (interceptors) that run before each page opens.",
    "AppLockInterceptor":
        "The extra padlock. Redirects to the PIN screen until the app lock has been entered for "
        "this session.",
    "GlobalExceptionHandler":
        "Catches anything that goes wrong and shows the friendly error page.",
    "ControllerExceptionHandler":
        "Turns problems in the page handlers into clear error messages.",
    "ChatbotExceptionHandler":
        "Does the same for the chatbot, but replies in computer-readable form.",
    "AuthController":
        "The front door: sign up, sign in and sign out.",
    "HomeController":
        "The dashboard, plus your own account page: change password, theme, language and alerts, "
        "download all your data, or delete the account.",
    "ProfileController":
        "A small internal address the browser uses to read and update your profile and photo.",
    "UserService":
        "Creating users, scrambling passwords with BCrypt, profile updates, password changes, "
        "deleting an account and its data, and creating the very first admin.",
    "UserDetailsServiceImpl":
        "Loads your details at sign-in so Spring Security knows who is logged in.",
    "UserPrincipal":
        "The signed-in user's record. It carries the user id, so every page can only ever reach "
        "that person's own data.",
    "UserDTO": "Your details in a safe form for display, with no password in it.",
    "AccountService": "Business rules for bank and cash accounts, including the balance maths.",
    "IncomeService": "Business rules for income: add, edit, delete, search, filter and totals.",
    "ExpenseService":
        "Business rules for expenses: add, edit, delete, search, filter, receipt files and totals.",
    "BudgetService":
        "Business rules for budgets: how much of each limit is used, and when a limit is close to "
        "or over.",
    "FinancialGoalService": "Business rules for savings goals: progress, target dates and status.",
    "AppLockService": "Stores and checks the app PIN (scrambled, never written in plain text).",
    "ChatHistoryService": "Remembers what was said in the chatbot, separately for each user.",
    "CashFlowForecastService":
        "Builds the 'what is coming' view: bills due soon, the projected balance and the warnings.",
    "TransactionExtractor": "Turns the raw text from a file or photo into clean transaction rows.",
    "ScanTransactionParser":
        "Finds the amounts, dates, shop names and categories inside scanned text, using patterns.",
    "ImportCommitService":
        "Saves the approved imported rows as real records, writes the import history and flags "
        "duplicates.",
    "ImportFileService": "Checks an uploaded file is the right type and size before reading it.",
    "UserDataExportService": "Exports everything a user has into a portable file.",
    "RecurringTransactionScheduler":
        "Every morning adds the repeat bills and repeat income that are due today, exactly once "
        "for each due date.",
    "ChatbotService":
        "The offline assistant. Understands the question and answers it from your own local records "
        "(no internet and no external AI service).",
    "ChatbotController":
        "Addresses for the offline assistant: ask a question, read the history, see what it can do.",
    "ChatRequest": "The question you asked the chatbot.",
    "ChatResponse": "The chatbot's reply, with any extra data or suggestions.",
    "ConversationContext": "Keeps track of the current conversation while you chat.",
    "HabitService": "Runs all the spending habit rules and puts the results together.",
    "HabitController": "Shows the spending habit results on screen and as data.",
    "SpendingRule": "The rule that every spending habit check must follow.",
    "AbstractSpendingRule": "Shared code that the individual spending habit checks build on.",
    "RuleResult": "One habit result: a label, how serious it is, and a tip.",
    "NeedWantRule": "Works out how much of the spending was needs and how much was wants.",
    "HostelFoodRule": "Spots repeated spending on food and eating out.",
    "StreakRule": "Finds patterns such as spending every day for a week.",
    "FinanceValidation":
        "One place for all the checks: amounts positive, dates sensible, names not empty, and file "
        "names safe.",
    "LocalOpenCVPreprocessor":
        "Improves a receipt photo before reading it: greyscale, resize, sharpen, adaptive "
        "threshold and straighten.",
    "LocalTess4JOcrService":
        "Runs the text reader (Tesseract) on the cleaned photo and returns the text plus how sure "
        "it is.",
    "LocalPDFBoxService": "Pulls the words out of a PDF, page by page.",
    "LocalDocumentScanService":
        "Reads PDFs with PDFBox and pictures with OCR, and falls back to the other route when one "
        "of them returns nothing.",
    "LocalDocumentUploadService":
        "Stores uploaded files outside the web folder with safe generated names, so only the owner "
        "can open them.",
    "LocalDuplicateDetectionService":
        "Spots records that are already saved or already imported by matching amount, date and "
        "description.",
    "LocalExcelStorageService": "Owns the Excel file: creates it and reads and writes its sheets.",
    "ExcelWorkbookInitializer":
        "Creates the Excel file and all of its sheets the first time the app runs.",
    "LocalAnalyticsEngine":
        "Works out monthly totals, category shares, trends, and income versus spending.",
    "LocalForecastEngine":
        "Uses the most recent months to project the next month's income and spending.",
    "LocalCategoryEngine": "Picks a category for a purchase by matching keywords.",
    "LocalReportEngine": "Collects everything a report or an export needs.",
    "LocalApachePoiService": "Reads and writes Excel cells.",
    "LocalTransactionService":
        "Adds, edits, searches and filters the signed-in user's own income and expense records.",
    "DefaultDocumentProcessor":
        "Sends each uploaded file to the right reader (PDF, picture, CSV or Excel) and returns the "
        "rows it found.",
    "TransactionService": "Contract for everything that can be done with income and expense records.",
    "AnalyticsEngine": "Contract for working out totals, trends and charts.",
    "ForecastEngine": "Contract for guessing next month's income and spending.",
    "CategoryEngine": "Contract for guessing which category a purchase belongs to.",
    "ReportEngine": "Contract for building reports.",
    "DocumentProcessor": "Contract for turning an uploaded file into a list of records.",
    "DocumentScanService": "Contract for reading documents to get the text out of them.",
    "DocumentUploadService": "Contract for storing and finding uploaded files.",
    "DuplicateDetectionService": "Contract for spotting records that have already been imported.",
    "OpenCVPreprocessor": "Contract for cleaning up a picture before the text reader runs.",
    "Tess4JOcrService": "Contract for reading text out of a picture.",
    "PDFBoxService": "Contract for reading text out of a PDF.",
    "ApachePoiService": "Contract for the Excel reading and writing helpers.",
    "BackupRestoreService":
        "Makes a locked (AES-256-GCM) backup of the database and the Excel file, lists past "
        "backups and restores them. It checks file names to stop path tricks, and takes a safety "
        "backup before restoring.",
    "BackupScheduler":
        "Every night at 02:00 it makes an encrypted backup and deletes backups older than the "
        "retention period.",
    "FinancialHealthScoreService":
        "Gives the finances a score out of 100 using real records: saving rate, sticking to "
        "budgets, steadiness of spending and goal progress.",
    "SmartInsightsService":
        "Writes plain-English tips, for example 'food spending is up 18% compared with last month'.",
    "NotificationService":
        "Creates the in-app alerts: bills due soon, unusually large purchases, and the weekly and "
        "monthly summaries.",
    "FinancePlusAccessInterceptor":
        "Keeps the FinancePlus pages shut until that workspace's own PIN has been entered.",
    "FinancePlusController":
        "Everything the FinancePlus area can do: lock and unlock, settings, repeat rules, alerts, "
        "stored files, import and export, the vault, and backup list, download, restore and delete.",
    "FinancePlusAutomation": "The timer that runs the FinancePlus repeat rules and alerts.",
    "FinancePlusRecurringService": "Creates FinancePlus repeat rules and works out what is due.",
    "FinancePlusNotificationService": "Creates the FinancePlus alerts.",
    "FinancePlusAttachmentService": "Stores FinancePlus files and opens them from the locked vault.",
    "FinancePlusBackupService": "Makes, lists, restores and deletes FinancePlus backups.",
    "FinancePlusSettingsService": "Reads and saves the FinancePlus preferences.",
    "FinancePlusImportService": "Brings FinancePlus data in from a file.",
    "FinancePlusExportService": "Takes FinancePlus data out to a file.",
    "User": "A user account: username, scrambled password, email, name, theme, language and alert preferences.",
    "Account": "A bank or cash account and its balance.",
    "Income": "Money coming in: amount, date, category, source, notes, receipt and repeat rule.",
    "Expense": "Money going out: amount, date, category, payment method, notes, receipt and repeat rule.",
    "Budget": "A spending limit for one category in one month.",
    "FinancialGoal": "A savings goal: target amount, current amount, target date, priority and status.",
    "ImportedTransaction":
        "A row waiting to be confirmed after an import, with the reason if it was rejected or "
        "flagged as a duplicate.",
    "ChatMessage": "One message said in the chatbot, kept per user.",
    "ExtractedTransactionDTO":
        "One record found inside a file or photo, passed along the import steps.",
    "ExpenseExcelService": "Copies expenses into their sheet in the Excel workbook.",
    "IncomeExcelService": "Copies income into their sheet in the Excel workbook.",
    "BudgetExcelService": "Copies budgets into their sheet in the Excel workbook.",
    "GoalExcelService": "Copies savings goals into their sheet in the Excel workbook.",
    "CategoryExcelService": "Copies categories into their sheet in the Excel workbook.",
    "FinancePlusSettings": "The FinancePlus workspace preferences for one user.",
    "FinancePlusRecurring": "A FinancePlus repeat rule that the user defined.",
    "FinancePlusNotification": "An alert raised inside the FinancePlus workspace.",
    "FinancePlusAttachment": "A file stored in the FinancePlus vault, with its details.",
    "FinancePlusBackup": "One FinancePlus backup in the history list.",
}

MERGE_WORDS = [("Finance", "Plus"), ("Open", "CV"), ("Tess", "4J"), ("App", "Lock"),
               ("Exception", "Handler"), ("Controller", "Advice")]

# Words dropped from a test class name to work out what it tests
TEST_DROP = {"Service", "Controller", "Engine", "Repository", "Impl", "Test"}


# ---------------------------------------------------------------------------
# 3. Small helpers
# ---------------------------------------------------------------------------

def read_text(path: str, limit: int = 500_000) -> str:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            return handle.read(limit)
    except OSError:
        return ""


def count_lines(path: str) -> int:
    try:
        with open(path, "rb") as handle:
            return sum(1 for _ in handle)
    except OSError:
        return 0


def human_size(size: int) -> str:
    if size < 1024:
        return f"{size} bytes"
    if size < 1024 * 1024:
        return f"{size / 1024:.0f} KB"
    return f"{size / 1024 / 1024:.1f} MB"


def split_words(name: str) -> list[str]:
    """Turn CamelCase into words, keeping known acronyms and pairs together."""
    spaced = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", name)
    spaced = re.sub(r"(?<=[A-Z])(?=[A-Z][a-z])", " ", spaced)
    words = [w for w in spaced.split() if w]
    changed = True
    while changed:
        changed = False
        for first, second in MERGE_WORDS:
            for index in range(len(words) - 1):
                if words[index] == first and words[index + 1] == second:
                    words[index:index + 2] = [first + second]
                    changed = True
                    break
            if changed:
                break
    return words


def humanise(tokens: list[str]) -> str:
    """Turn class-name words into a readable phrase, without repeats."""
    out: list[str] = []
    for token in tokens:
        if token in WORDS:
            phrase = WORDS[token]
        elif token.isupper() and len(token) > 1:
            phrase = token
        else:
            phrase = token.lower()
        if not phrase:
            continue
        if out and out[-1] == phrase:
            continue
        out.append(phrase)
    return re.sub(r"\s+", " ", " ".join(out)).strip() or "the app"


def strip_generics(text: str) -> str:
    return re.sub(r"<.*", "", text or "").strip()


# ---------------------------------------------------------------------------
# 4. Java purpose
# ---------------------------------------------------------------------------

def java_purpose(path: str) -> str:
    text = read_text(path)
    stem = os.path.splitext(os.path.basename(path))[0]

    match = TYPE_RE.search(text) or re.search(
        r"(class|interface|enum|record)\s+(\w+)", text)
    kind_word = match.group(1) if match else "class"
    type_name = match.group(2) if match else stem
    extends = strip_generics(match.group(3)) if match and match.lastindex and match.lastindex >= 3 else ""
    implements = strip_generics(match.group(4)) if match and match.lastindex and match.lastindex >= 4 else ""

    tokens = split_words(type_name)
    annotations = {a for a in ANNOT_RE.findall(text)}
    lombok = sorted(annotations.intersection(LOMBOK_ANNOTS))

    is_test = stem.endswith("Test")
    is_entity = "Entity" in annotations
    is_dto = tokens[-1] in ("Dto", "DTO") or "Dto" in tokens
    is_interface = kind_word == "interface"

    # Work out the subject words
    subject_tokens = list(tokens)
    if is_interface and len(subject_tokens) > 1:
        subject_tokens = subject_tokens[:-1]
    if is_dto:
        subject_tokens = [t for t in subject_tokens if t not in ("Dto", "DTO")]
    if is_test:
        while subject_tokens and subject_tokens[-1] in TEST_DROP:
            subject_tokens.pop()
    subject = humanise(subject_tokens)

    # Work out the leading sentence
    if type_name in JAVA_CURATED:
        summary = JAVA_CURATED[type_name]
    elif is_entity:
        summary = f"Database record type for {subject}, saved in a table."
    elif is_interface:
        summary = f"Contract (interface): everything that works with {subject} must follow this."
    elif is_test:
        summary = f"Automated test for {subject}: checks the behaviour is still correct."
    else:
        suffix = tokens[-1] if len(tokens) > 1 else ""
        template = KINDS.get(suffix)
        if template:
            summary = template.format(s=subject)
        elif kind_word == "enum":
            summary = f"The list of allowed options for {subject}."
        elif kind_word == "record":
            summary = f"An unchangeable data shape for {subject}."
        else:
            summary = f"Works with {subject}."

    if type_name.startswith("FinancePlus") and type_name not in JAVA_CURATED:
        summary += " Part of the FinancePlus workspace, which sits behind its own PIN."
    elif type_name.startswith("Local") and type_name not in JAVA_CURATED:
        summary += " Runs entirely on this machine: no cloud and no internet."

    # Facts read straight out of the file
    facts: list[str] = []
    if implements and implements not in summary:
        facts.append(f"implements {implements}")
    if extends and extends not in summary:
        facts.append(f"extends {extends}")

    class_start = text.find(type_name) if type_name else len(text)
    if class_start < 0:
        class_start = len(text)
    class_base = ""
    routes: list[str] = []
    for found in MAPPING_RE.finditer(text):
        kind = found.group(1)
        args = found.group(3) or ""
        value_match = re.search(r'"([^"]*)"', args)
        value = value_match.group(1) if value_match else ""
        if kind == "RequestMapping" and found.start() < class_start:
            class_base = value or class_base
            continue
        method_match = re.search(r"method\s*=\s*RequestMethod\.(\w+)", args)
        verb = VERBS.get(kind, "")
        if not verb and method_match:
            verb = method_match.group(1).upper()
        full = (class_base + value) or "/"
        full = re.sub(r"/{2,}", "/", full)
        routes.append(f"{verb} {full}".strip())
    if routes:
        shown = routes[:6]
        if len(routes) > 6:
            shown.append(f"... and {len(routes) - 6} more")
        facts.append(f"{len(routes)} route(s): " + "; ".join(shown))

    if "Scheduled" in annotations:
        cron = SCHEDULE_RE.search(text)
        facts.append("runs on a timer" + (f" ({cron.group(1).strip()})" if cron else ""))

    table = TABLE_RE.search(text)
    if table:
        facts.append(f"stored in table `{table.group(1)}`")

    if is_test:
        test_count = len(TEST_METHOD_RE.findall(text))
        if test_count:
            facts.append(f"{test_count} test method(s)")

    if is_entity:
        fields = FIELD_RE.findall(text)
        if fields:
            facts.append("main fields: " + ", ".join(fields[:8]))

    if lombok:
        facts.append("uses Lombok, so the getters and setters are written for you")
    elif not is_test and not routes:
        methods = {m for m in METHOD_RE.findall(text)
                   if m not in {"if", "for", "while", "switch", "catch", "return", "new"}}
        if methods:
            facts.append(f"{len(methods)} public method(s)")

    if "bcrypt" in text.lower() and "password" in text.lower():
        facts.append("handles password hashing")

    if facts:
        summary += "  [" + "; ".join(facts) + "]"
    return summary


# ---------------------------------------------------------------------------
# 5. Other file types
# ---------------------------------------------------------------------------

def properties_purpose(path: str) -> str:
    text = read_text(path)
    keys = re.findall(r"^\s*([\w.\-]+)\s*=", text, re.M)
    groups: dict[str, int] = {}
    for key in keys:
        groups[key.split(".")[0]] = groups.get(key.split(".")[0], 0) + 1
    listed = ", ".join(f"{name} ({count})" for name, count in sorted(groups.items()))
    return f"Settings file with {len(keys)} setting(s). Groups: {listed}."


def html_purpose(path: str) -> str:
    text = read_text(path)
    title = re.search(r"<title>(.*?)</title>", text, re.S)
    title = re.sub(r"\s+", " ", title.group(1)).strip() if title else ""
    fragments = sorted(set(re.findall(r"~\{[^}]*/([^/}]+)\}", text)))
    scripts = sorted(set(re.findall(r'<script[^>]+src="([^"]+)"', text)))
    styles = sorted(set(re.findall(r'<link[^>]+href="([^"]+)"', text)))
    actions = sorted(set(re.findall(r'action="([^"]+)"', text)))
    bits = [f'Thymeleaf page' + (f', title "{title}"' if title else "")]
    if fragments:
        bits.append("uses shared fragments: " + ", ".join(fragments))
    if scripts:
        bits.append("loads scripts: " + ", ".join(scripts))
    if styles:
        bits.append("loads styles: " + ", ".join(styles))
    if actions:
        bits.append("form posts to: " + ", ".join(actions[:6]))
    return ". ".join(bits) + "."


CSS_MEANING = {
    "finwise-tokens.css": "The design values (colours, spacing, text sizes) that the other stylesheets use.",
    "finwise-theme.css": "The light and dark colour themes.",
    "finwise-layout.css": "The page frame: top bar, side menu and column grid.",
    "finwise-components.css": "Reusable pieces: buttons, cards, tables, forms, pop-ups and labels.",
    "finwise-responsive.css": "Adjusts the layout for phones and small screens.",
    "finwise-bridge.css": "Keeps older style rules working with the new design values.",
    "3d-dynamic.css": "3D card and visual effects.",
    "auth.css": "Styling for the sign-in and sign-up pages.",
    "settings.css": "Styling for the settings and backup screens.",
    "scan-fill.css": "Styling for the Scan & Fill screen.",
    "finance-plus.css": "Styling for the FinancePlus workspace.",
    "chatbot-widget.css": "Styling for the floating chat bubble and chat window.",
}


def css_purpose(path: str) -> str:
    text = read_text(path)
    name = os.path.basename(path)
    blocks = len(re.findall(r"\{", text))
    variables = len(re.findall(r"--[\w-]+\s*:", text))
    meaning = CSS_MEANING.get(name, "Stylesheet for the pages.")
    return f"{meaning} {blocks} rule block(s)" + (f" and {variables} design value(s)" if variables else "") + "."


JS_MEANING = {
    "script.js": "Older page behaviour that is still in use.",
    "finwise-ui.js": "Shared browser behaviour: theme switching, pop-up messages, dialogs and language switching.",
    "scan-fill.js": "Runs the Scan & Fill screen: uploads the photo, shows reading progress, fills the form.",
    "finance-plus.js": "Runs the FinancePlus screens: the vault, the repeat rules and the backups.",
    "chatbot-widget.js": "The floating chat bubble: opens the chat, sends questions, shows answers and keeps the history.",
    "chart.umd.min.js": "The bundled chart-drawing library (Chart.js). It is served from this computer so no internet is needed.",
}


def js_purpose(path: str) -> str:
    text = read_text(path)
    name = os.path.basename(path)
    funcs = sorted(set(re.findall(r"function\s+(\w+)", text)))
    meaning = JS_MEANING.get(name, "Browser script.")
    tail = f" Defines {len(funcs)} function(s)" + (f": {', '.join(funcs[:8])}" if funcs else "") + "."
    return meaning + tail


LANGUAGE_NAMES = {
    "en": "English", "es": "Spanish", "fr": "French", "de": "German", "it": "Italian",
    "pt": "Portuguese", "ru": "Russian", "tr": "Turkish", "ar": "Arabic", "fa": "Persian",
    "he": "Hebrew", "ur": "Urdu", "hi": "Hindi", "mr": "Marathi", "ne": "Nepali",
    "pa": "Punjabi", "gu": "Gujarati", "ta": "Tamil", "te": "Telugu", "kn": "Kannada",
    "ml": "Malayalam", "ja": "Japanese", "ko": "Korean", "zh": "Chinese", "bn": "Bengali",
}


def locale_purpose(path: str) -> str:
    text = read_text(path)
    code = os.path.splitext(os.path.basename(path))[0]
    pairs = len(re.findall(r'"[^"]+"\s*:', text))
    name = LANGUAGE_NAMES.get(code, code)
    return (f"{name} translation file with {pairs} translated text(s). The chosen language is "
            f"remembered with the user account.")


def script_purpose(path: str) -> str:
    text = read_text(path)
    name = os.path.basename(path).lower()
    if name.endswith(".bat") or name.endswith(".cmd") or name.endswith(".sh"):
        steps = []
        if re.search(r"where\s+java|command -v java", text):
            steps.append("checks that Java and Maven are installed")
        if re.search(r"if not exist|mkdir -p", text):
            steps.append("creates the data and logs folders")
        if re.search(r"Stop-Process|taskkill", text):
            steps.append("stops any copy already running")
        if "mvn" in text:
            steps.append("builds the project with Maven")
        if re.search(r"java\s+-jar", text):
            steps.append("starts the finished program")
        if re.search(r"Start-Process\s+'http|start\s+http", text):
            steps.append("opens the browser once the port is ready")
        return ("Command script that " + (", ".join(steps) if steps else "prepares and starts the app")
                + ".")
    if name.endswith(".py"):
        return "Python helper script. " + CURATED.get(name, "Used while maintaining the project, not by the running app.")
    return "Command script."


def document_purpose(path: str, rel: str) -> str:
    ext = os.path.splitext(path)[1].lower()
    name = os.path.basename(path)
    fixture = "fixtures" in rel
    where = "used by the automated tests" if fixture else "for trying the import feature"
    if ext in (".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp"):
        if fixture:
            return f"Example picture {where}."
        if "note" in name:
            return "Handwriting sample, used to see how well the text reader copes."
        if "sample" in name:
            return "Example picture for trying the import and receipt-reading features."
        if "expense" in name or "income" in name:
            return "Made-up sample receipt used to test the reading feature."
        if "uploads" in rel or "profile" in rel:
            return "Picture a user uploaded (profile photo or receipt). Stored outside the web folder."
        return "Image file."
    if ext == ".csv":
        return f"Sample list of transactions ({human_size(os.path.getsize(path))}), {where}."
    if ext == ".pdf":
        return f"Sample PDF statement ({human_size(os.path.getsize(path))}), {where}."
    if fixture and ext in (".xls", ".xlsx"):
        return f"Sample Excel statement ({human_size(os.path.getsize(path))}), {where}."
    return ""


def binary_purpose(path: str) -> str:
    ext = os.path.splitext(path)[1].lower()
    size = human_size(os.path.getsize(path))
    if ext == ".db":
        return f"SQLite database file ({size}). Created and updated by the app."
    if ext in (".xlsx", ".xls"):
        return f"Excel workbook ({size}). The spreadsheet copy of the financial data."
    if ext == ".finwise":
        return f"Encrypted backup file ({size}). Locked with AES-256-GCM; needs the backup key to open."
    if ext == ".gz":
        return f"Compressed older log file ({size})."
    if ext == ".key":
        return "Secret key that locks the backup files. Keep a separate copy."
    if ext == ".traineddata":
        return "Tesseract language model. Needed to read text in pictures; without it OCR is switched off."
    if ext == ".log":
        return f"Log file ({size}). A record of what the app did, for troubleshooting."
    if ext == ".class":
        return "Compiled Java class file produced by the build."
    if ext == ".jar":
        return f"The finished, runnable program ({size}). Start it with java -jar."
    return f"File ({size})."


KEYWORDS = [
    ("README", "Documentation for the project."),
    ("LICENSE", "The licence terms for the project."),
    ("settings", "Editor or app settings."),
    ("extensions", "Recommended editor extensions."),
    ("hooks", "Automation hook script."),
    ("test", "Test-related file."),
    ("log", "Log file written during a run."),
    ("db", "Database file."),
    ("traineddata", "OCR language model."),
    ("gitignore", "The list of files Git must ignore."),
]

EXTENSIONS = {
    ".java": "Java source file.", ".html": "Web page template.", ".css": "Stylesheet.",
    ".js": "JavaScript file.", ".json": "Data file.", ".properties": "Settings file.",
    ".xml": "XML file.", ".md": "Documentation.", ".txt": "Text file.", ".yml": "Configuration.",
    ".yaml": "Configuration.", ".py": "Python script.", ".bat": "Windows command script.",
    ".cmd": "Windows command script.", ".sh": "Shell script.", ".png": "Image.",
    ".jpg": "Image.", ".jpeg": "Image.", ".gif": "Image.", ".ico": "Icon.", ".svg": "Vector image.",
    ".csv": "List of transactions in plain text.", ".db": "Database file.",
    ".xlsx": "Excel workbook.", ".xls": "Older Excel workbook.", ".pdf": "PDF document.",
    ".finwise": "Encrypted backup.", ".gz": "Compressed file.", ".log": "Log file.",
    ".class": "Compiled Java class.", ".jar": "Runnable program.", ".traineddata": "OCR language model.",
    ".key": "Secret key.",
}


def fallback_purpose(path: str) -> str:
    name = os.path.basename(path)
    for keyword, note in KEYWORDS:
        if keyword.lower() in name.lower():
            return note
    return EXTENSIONS.get(os.path.splitext(name)[1].lower(), "Project file.")


def describe(path: str, root: str) -> str:
    """Return the plain-English purpose of one file."""
    rel = os.path.relpath(path, root).replace("\\", "/")
    if rel in CURATED:
        return CURATED[rel]
    for needle, note in SUBSTRING_CURATED:
        if needle in rel:
            return note
    ext = os.path.splitext(path)[1].lower()
    if ext == ".java":
        return java_purpose(path)
    if ext == ".properties":
        return properties_purpose(path)
    if ext == ".html":
        return html_purpose(path)
    if ext == ".css":
        return css_purpose(path)
    if ext == ".js":
        return js_purpose(path)
    if ext == ".json":
        if "/locales/" in rel:
            return locale_purpose(path)
        if rel.endswith("package.json") or rel.endswith("package-lock.json"):
            return "Node package file listing the browser libraries the project uses."
        return "Data file with settings read by the code."
    if ext == ".txt" and rel.count("/") == 0:
        return "A written guide to the project, kept next to the README for reference."
    if ext in (".bat", ".cmd", ".sh", ".py"):
        return script_purpose(path)
    document = document_purpose(path, rel)
    if document:
        return document
    if ext in (".db", ".xlsx", ".xls", ".finwise", ".gz", ".log", ".key", ".traineddata",
               ".class", ".jar", ".pdf", ".csv") or not ext:
        return binary_purpose(path)
    return fallback_purpose(path)


# ---------------------------------------------------------------------------
# 6. Grouping repetitive folders
# ---------------------------------------------------------------------------

GROUPS = [
    ("data/backups/", "Encrypted backup files, one for each backup run, plus the key that locks them."),
    ("data/import-images/", "Receipt and statement images uploaded through import and Scan & Fill."),
    ("data/uploads/profile/", "Profile pictures uploaded by users."),
    ("data/finance-plus/backups/", "FinancePlus workspace backups."),
    ("img/imgg/", "Made-up sample receipt pictures for testing the reading feature."),
    ("logs/", "Log files from previous runs."),
    ("target/classes/", "Compiled Java classes and copied resources produced by the build."),
    ("target/test-classes/", "Compiled test classes produced by the build."),
    ("target/surefire-reports/", "Test results written by Maven during the test run."),
    ("target/generated-sources/", "Files produced automatically while compiling."),
    ("target/generated-test-sources/", "Files produced automatically while compiling the tests."),
    ("target/maven-status/", "Bookkeeping that lets the next build be faster."),
    ("target/backups/", "Backups created during a manual review run."),
]


def group_key(rel: str) -> str | None:
    for prefix, _ in GROUPS:
        if rel.startswith(prefix):
            return prefix
    return None


# ---------------------------------------------------------------------------
# 7. Walk the project
# ---------------------------------------------------------------------------

SKIP_DIRS = {".git", ".idea"}
SECTION_ORDER = [
    "1. TEST SOURCES",
    "2. MAIN SOURCES",
    "3. RESOURCES (settings, web pages, styles, scripts)",
    "4. PROJECT FILES",
    "5. RUNTIME FOLDERS (data, logs, img, build output)",
]


def collect(root: str, include_target: bool) -> tuple[dict[str, list[str]], list[str]]:
    sections: dict[str, list[str]] = {name: [] for name in SECTION_ORDER}
    folders: list[str] = []
    for current, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
        rel_dir = os.path.relpath(current, root).replace("\\", "/")
        if rel_dir == ".":
            rel_dir = ""
        for folder in dirnames:
            folders.append((rel_dir + "/" + folder).lstrip("/"))
        for filename in sorted(filenames):
            rel = (rel_dir + "/" + filename).lstrip("/")
            if rel.startswith("target/") and not include_target:
                continue
            if rel.startswith("src/test/"):
                sections["1. TEST SOURCES"].append(rel)
            elif rel.startswith("src/main/java/"):
                sections["2. MAIN SOURCES"].append(rel)
            elif rel.startswith("src/main/resources/"):
                sections["3. RESOURCES (settings, web pages, styles, scripts)"].append(rel)
            elif rel.startswith(("data/", "logs/", "img/", "target/")):
                sections["5. RUNTIME FOLDERS (data, logs, img, build output)"].append(rel)
            else:
                sections["4. PROJECT FILES"].append(rel)
    return sections, folders


def line_for(rel: str, root: str) -> str:
    full = os.path.join(root, rel.replace("/", os.sep))
    size = ""
    if os.path.isfile(full) and os.path.getsize(full) < 4 * 1024 * 1024:
        lines = count_lines(full)
        if lines:
            size = f"  [{lines} lines]"
    return f"{rel}{size}\n      -> {describe(full, root)}"


def build_report(root: str, include_target: bool, verbose: bool) -> str:
    sections, folders = collect(root, include_target)
    out: list[str] = [
        "=" * 96,
        "FinWise - Offline Personal Finance Manager",
        "EVERY FILE AND WHAT IT IS FOR (auto-generated)",
        f"Project folder: {root}",
        f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}",
        f"Generator: tools/generate_purpose_report.py  (mode: {'verbose' if verbose else 'grouped'})",
        "=" * 96,
        "",
        "Order: tests first, then the main code, then the resources, then the project and runtime files.",
        "Each entry shows the file, then what it does in plain English.",
        "",
    ]

    for title in SECTION_ORDER:
        files = sections[title]
        out += ["=" * 96, title, "=" * 96]
        if not files:
            out += ["(nothing here)", ""]
            continue
        if title.startswith("5. RUNTIME") and not verbose:
            grouped: dict[str, list[str]] = {}
            singles: list[str] = []
            for rel in files:
                key = group_key(rel)
                (grouped.setdefault(key, []).append(rel) if key else singles.append(rel))
            for key in sorted(grouped):
                members = grouped[key]
                out += ["", f"FOLDER {key}  ({len(members)} file(s))", f"      -> {dict(GROUPS)[key]}"]
                for member in members[:4]:
                    out.append(f"         e.g. {os.path.basename(member)}")
                if len(members) > 4:
                    out.append(f"         ... and {len(members) - 4} more of the same kind")
            if singles:
                out += ["", "Other files in this section:"]
                for rel in singles:
                    out += ["", line_for(rel, root)]
        else:
            for rel in files:
                out += ["", line_for(rel, root)]
        out.append("")

    out += ["=" * 96, "FOLDERS (what each folder is for)", "=" * 96]
    for folder in sorted(set(folders)):
        out += ["", f"{folder}/", f"      -> {folder_purpose(folder)}"]
    out.append("")
    return "\n".join(out)


FOLDER_PURPOSES = {
    "src": "Everything the app is made of: the code, the web pages, the styling, the wording files and the tests.",
    "src/main": "The working parts of the app (as opposed to the tests).",
    "src/main/java": "The Java code, grouped into packages.",
    "src/main/java/com/finance": "The root package. Every class of the app lives under here.",
    "src/main/java/com/finance/app": "Holds the file that starts the program.",
    "src/main/java/com/finance/config": "Rules for security, web routing, the app lock and error pages.",
    "src/main/java/com/finance/controller": "One class per screen or group of web addresses.",
    "src/main/java/com/finance/service": "The real work: records, import, receipt reading, forecasting, reports and export.",
    "src/main/java/com/finance/service/excel": "The Excel copy, the alerts, the health score, the tips and the backups.",
    "src/main/java/com/finance/model": "The shapes of the data: database records and small data packages.",
    "src/main/java/com/finance/model/entity": "The records as they are stored in the database.",
    "src/main/java/com/finance/model/dto": "Small safe packages of data passed between layers.",
    "src/main/java/com/finance/repository": "Ready-made questions that talk to the database.",
    "src/main/java/com/finance/security": "Who is signed in, and how that is worked out.",
    "src/main/java/com/finance/chatbot": "The offline assistant.",
    "src/main/java/com/finance/habits": "The spending habit checks.",
    "src/main/java/com/finance/financeplus": "The FinancePlus workspace, which sits behind its own PIN.",
    "src/main/java/com/finance/util": "Shared validation helpers.",
    "src/main/resources": "Files copied into the program as they are: settings, web pages, styles and scripts.",
    "src/main/resources/templates": "The web pages, written in Thymeleaf so the server fills in the blanks.",
    "src/main/resources/templates/fragments": "Shared pieces of page (menu, footer, chat bubble) reused by many pages.",
    "src/main/resources/templates/error": "The page shown when something goes wrong.",
    "src/main/resources/static": "Files sent to the browser unchanged: styles, scripts, the chart library and translations.",
    "src/main/resources/static/css": "All the styling, split into design values, theme, layout, pieces and small screens.",
    "src/main/resources/static/js": "The behaviour that runs in the browser.",
    "src/main/resources/static/vendor": "Third-party code copied in so the app works without internet.",
    "src/main/resources/static/locales": "One translation file per language.",
    "src/test": "The automated checks.",
    "src/test/java": "The test code, in the same package layout as the main code.",
    "src/test/java/com/finance": "Tests for the app package.",
    "src/test/java/com/finance/service": "Tests for the finance, import and offline features.",
    "src/test/java/com/finance/financeplus": "Tests for the FinancePlus workspace.",
    "src/test/resources": "Extra files the tests need, such as sample statements to import.",
    "src/test/resources/fixtures": "Sample files the tests feed into the import feature.",
    "src/test/resources/fixtures/import": "Sample statements (PDF, picture, CSV, Excel, handwriting) used to test importing.",
    "data": "Everything the app saves. Deleting it resets the app to empty, apart from the first admin login.",
    "data/backups": "The locked backup files and the key that locks them.",
    "data/import-images": "Pictures uploaded for import and Scan & Fill.",
    "data/uploads": "Files uploaded by users, such as profile photos.",
    "data/uploads/profile": "Profile pictures.",
    "data/finance-plus": "FinancePlus workspace data.",
    "data/finance-plus/backups": "FinancePlus workspace backups.",
    "data/tessdata": "The language file the text reader needs.",
    "logs": "Records of what the app did, kept for troubleshooting.",
    "img": "Example files for trying the import and receipt-reading features.",
    "img/imgg": "Made-up sample receipts.",
    "target": "Everything the build produced. Safe to delete; the next build recreates it.",
    "target/classes": "The compiled program and the copied resources.",
    "target/test-classes": "The compiled tests.",
    "target/surefire-reports": "The test results.",
    "target/generated-sources": "Files produced automatically while compiling.",
    "target/generated-test-sources": "Files produced automatically while compiling the tests.",
    "target/maven-status": "Bookkeeping for faster repeat builds.",
    "target/backups": "Backups made during a manual review run.",
    ".vscode": "Settings and extension recommendations for VS Code.",
    ".github": "Automation that runs around the code on GitHub.",
    ".github/modernize": "Helper hooks for the Java upgrade tool.",
    ".github/modernize/java-upgrade": "The Java upgrade tool's settings and hook scripts.",
    ".github/modernize/java-upgrade/hooks": "The hook scripts themselves.",
    ".github/modernize/java-upgrade/hooks/scripts": "Small scripts that record which tools were used.",
    "tools": "Helper scripts for working on the project itself (not part of the running app).",
    "tools/generate_purpose_report.py": "This generator: walks the project and writes the 'what is this file for' report.",
    "C": "Old scratch files from earlier debugging. Not used by the build; safe to delete.",
}


PACKAGE_PURPOSES = {
    "app": "Holds the file that starts the program.",
    "config": "Rules for security, web routing, the app lock and error pages.",
    "controller": "One class per screen or group of web addresses.",
    "service": "The real work: records, import, receipt reading, forecasting, reports and export.",
    "service/excel": "The Excel copy, the alerts, the health score, the tips and the backups.",
    "model": "The shapes of the data: database records and small data packages.",
    "model/entity": "The records as they are stored in the database.",
    "model/dto": "Small safe packages of data passed between layers.",
    "repository": "Ready-made questions that talk to the database.",
    "security": "Who is signed in, and how that is worked out.",
    "chatbot": "The offline assistant.",
    "chatbot/model": "The data shapes used by the chatbot.",
    "chatbot/service": "The chatbot's answering logic.",
    "habits": "The spending habit checks.",
    "financeplus": "The FinancePlus workspace, which sits behind its own PIN.",
    "financeplus/model": "The records used inside the FinancePlus workspace.",
    "financeplus/repository": "The database questions for FinancePlus records.",
    "financeplus/service": "The FinancePlus work: rules, alerts, files, backups, import and export.",
    "financeplus/web": "The FinancePlus pages and the check that keeps them behind the PIN.",
    "util": "Shared validation helpers.",
}

BUILD_FOLDERS = {"maven-archiver", "maven-compiler-plugin", "maven-status", "generated-sources",
                 "generated-test-sources", "annotations", "test-annotations", "META-INF"}


def folder_purpose(folder: str) -> str:
    if folder in FOLDER_PURPOSES:
        return FOLDER_PURPOSES[folder]
    tail = folder.split("/")[-1]
    if "com/finance/" in folder + "/":
        package = (folder + "/").split("com/finance/", 1)[1].rstrip("/")
        if not package:
            return "The root package. Every class of the app lives under here."
        if package in PACKAGE_PURPOSES:
            return PACKAGE_PURPOSES[package]
        return f"Part of the {package.split('/')[-1]} package."
    if folder.startswith("target/"):
        if tail in BUILD_FOLDERS:
            return "Created by the build (Maven). Safe to delete; the next build recreates it."
        return "Part of the build output."
    if folder.startswith("data/"):
        return "Part of the data the app saves."
    if tail in ("java", "com", "finance"):
        return "Part of the package layout."
    if tail.startswith("."):
        return "Hidden folder used by a tool."
    return f"Holds the {tail.replace('-', ' ')} files."


# ---------------------------------------------------------------------------
# 8. Entry point
# ---------------------------------------------------------------------------

def main() -> int:
    default_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    parser = argparse.ArgumentParser(
        description="Write a plain-English purpose line for every file in the project.")
    parser.add_argument("--root", default=default_root, help="project root folder")
    parser.add_argument("--output", default=os.path.join(default_root, "PROJECT_FILE_PURPOSE.txt"),
                        help="report file to write")
    parser.add_argument("--verbose", action="store_true",
                        help="list every runtime file instead of grouping them")
    parser.add_argument("--include-target", action="store_true",
                        help="also list every file inside target/")
    parser.add_argument("--stdout", action="store_true", help="print the report instead of writing it")
    args = parser.parse_args()

    root = os.path.abspath(args.root)
    if not os.path.isdir(root):
        print(f"error: not a folder: {root}", file=sys.stderr)
        return 1

    report = build_report(root, args.include_target, args.verbose)
    if args.stdout:
        print(report)
        return 0
    with open(args.output, "w", encoding="utf-8") as handle:
        handle.write(report)
    entries = sum(1 for line in report.splitlines() if line.startswith("      -> "))
    print(f"wrote {args.output}  ({entries} entries, {len(report.splitlines())} lines)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

# Setting Up FinWise on Another Laptop

This guide takes you from a brand-new laptop to a running FinWise, on **Windows, macOS, or Linux**. Pick the section for your system.

---

## 1. What you need to install

| # | What | Version | Why | Required? |
|---|------|---------|-----|-----------|
| 1 | **JDK (Java Development Kit)** | **17 or newer** | The program itself runs on Java | **Yes** |
| 2 | **Maven** | **3.8 or newer** | Builds the project from source | Only if building from source |
| 3 | **Git** | any recent | Only to download the project from a repository | Optional |
| 4 | **Web browser** | Chrome / Edge / Firefox | The app's interface | **Yes** |

**You do NOT need to install these** (they are already inside the project):

- SQLite — the database library is bundled
- Apache POI / PDFBox — Excel and PDF reading is bundled
- OpenCV — the native image libraries come inside the dependency JAR
- Tess4J + `eng.traineddata` — text reading and its English model are bundled
- Chart.js, Bootstrap, Font Awesome — served locally by the app, no internet needed

Check the versions you already have (see [Section 3](#3-check-what-is-already-installed)).

---

## 2. Choose your setup method

### Option A — Copy the finished JAR (easiest, no Maven needed)

Best for: running the app on another laptop without building anything.

1. On the **original** laptop, build it once:

   ```powershell
   mvn clean package -DskipTests
   ```

2. Copy these to the **new** laptop, into one folder (e.g. `FinWise`):

   ```
   FinWise/
   ├── offline-finance-manager-1.0.0.jar    <- from target/
   ├── data/
   │   └── tessdata/
   │       └── eng.traineddata              <- REQUIRED for reading receipts/PDFs
   ├── run.bat                              <- Windows launcher (optional but handy)
   ├── run.sh                               <- Mac/Linux launcher (optional but handy)
   └── SETUP.md
   ```

3. On the new laptop, create the folders the app writes into:

   **Windows (PowerShell)**
   ```powershell
   mkdir data, data\tessdata, data\import-images, data\uploads\profile, data\backups, logs -Force
   Copy-Item "offline-finance-manager-1.0.0.jar" .
   ```

   **macOS / Linux**
   ```bash
   mkdir -p data/tessdata data/import-images data/uploads/profile data/backups logs
   ```

4. Put `eng.traineddata` into `data/tessdata/`.

5. Start it:

   **Windows (PowerShell)**
   ```powershell
   java -jar .\offline-finance-manager-1.0.0.jar --server.port=8080
   ```

   **macOS / Linux**
   ```bash
   java -jar offline-finance-manager-1.0.0.jar --server.port=8080
   ```

6. Open <http://localhost:8080> and sign in with `admin` / `admin123`.

> **Note:** `run.bat` / `run.sh` rebuild the project with Maven, so on the new laptop use the direct `java -jar` command above unless you also install Maven.

---

### Option B — Full source + build (needs Maven)

Best for: development, changing the code, or running the tests.

1. Copy the whole project folder to the new laptop (or clone it):

   ```powershell
   git clone <your-repository-url> FinWise
   cd FinWise
   ```

2. Install JDK 17 and Maven (Section 3).

3. Build and run:

   **Windows (PowerShell)**
   ```powershell
   mvn clean verify
   mvn spring-boot:run
   ```

   **macOS / Linux**
   ```bash
   mvn clean verify
   ./mvnw spring-boot:run   # or: mvn spring-boot:run
   ```

4. Or use the launcher scripts, which check the tools, build, start, and open the browser:

   ```powershell
   .\run.bat
   ```

   ```bash
   ./run.sh
   ```

5. Open <http://localhost:8080>.

---

## 3. Check what is already installed

Run these in a terminal. Note the version numbers.

**Windows (PowerShell or Command Prompt)**

```powershell
java -version
javac -version
mvn -v
git --version
```

**macOS / Linux**

```bash
java -version
javac -version
mvn -v
git --version
```

You want to see `17` or higher for Java. If `mvn` or `java` is "not recognized", install them below.

---

## 4. Install Java (JDK 17)

### Windows

**Option 1 — winget (Windows 10/11, comes with the system)**

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
```

**Option 2 — Chocolatey**

```powershell
choco install temurin17
```

**Option 3 — manual**

1. Go to <https://adoptium.net/temurin/releases/?version=17>
2. Download the **JDK 17 for Windows** `.msi` or `.zip`
3. Run the `.msi` installer, tick "Set JAVA_HOME" and add to `PATH`
4. Open a **new** terminal (so it picks up the change) and run `java -version`

### macOS

```bash
brew install --cask temurin@17
# then follow the printed instructions to add JAVA_HOME, or use:
/usr/libexec/java_home -v 17
```

### Ubuntu / Debian

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
```

### Fedora

```bash
sudo dnf install java-17-openjdk-devel
```

### Arch / Manjaro

```bash
sudo pacman -S jdk17-openjdk
```

### If `java` still is not recognized

Set `JAVA_HOME` and add it to `PATH`.

**Windows (system-wide, run PowerShell as Administrator)**

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot", "Machine")
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","Machine") + ";$env:JAVA_HOME\bin", "Machine")
```

> Close and reopen the terminal afterwards.

**macOS / Linux (`~/.bashrc` or `~/.zshrc`)**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## 5. Install Maven

### Windows

**Option 1 — winget**

```powershell
winget install --id Apache.Maven -e
```

**Option 2 — manual**

1. Go to <https://maven.apache.org/download.cgi>
2. Download the **.zip** (e.g. `apache-maven-3.9.x-bin.zip`)
3. Extract to `C:\tools\apache-maven-3.9.x`
4. Add `C:\tools\apache-maven-3.9.x\bin` to the system `Path`, or set it for your user:

```powershell
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";C:\tools\apache-maven-3.9.x\bin", "User")
```

**Option 3 — Chocolatey**

```powershell
choco install maven
```

### macOS

```bash
brew install maven
```

### Ubuntu / Debian

```bash
sudo apt install -y maven
```

### Fedora

```bash
sudo dnf install maven
```

### If `mvn` is not recognized on Windows

```powershell
$env:Path += ";C:\tools\apache-maven-3.9.x\bin"
mvn -v
```

---

## 6. Install Git (only if you will clone the project)

**Windows**

```powershell
winget install --id Git.Git -e
```

**macOS**

```bash
brew install git
```

**Ubuntu / Debian**

```bash
sudo apt install -y git
```

**Fedora**

```bash
sudo dnf install git
```

---

## 7. Prepare the data folders

FinWise writes everything into `data/` and `logs/` **inside the project folder**, so the program must be started from that folder.

**Windows (PowerShell)**

```powershell
mkdir data, logs, data\tessdata, data\import-images, data\uploads\profile, data\backups, data\finance-plus -Force
```

**macOS / Linux**

```bash
mkdir -p data logs data/tessdata data/import-images data/uploads/profile data/backups data/finance-plus
```

Then copy the OCR language file into place (it comes with the project):

**Windows**

```powershell
Copy-Item ".\src\main\resources\tessdata\eng.traineddata" ".\data\tessdata\eng.traineddata" -Force
```

**macOS / Linux**

```bash
cp src/main/resources/tessdata/eng.traineddata data/tessdata/
```

> If the file is not in `src/main/resources/tessdata`, download `eng.traineddata` from <https://github.com/tesseract-ocr/tessdata> and save it as `data/tessdata/eng.traineddata`. Without it, receipt photo and PDF reading will not work; everything else still runs.

---

## 8. Start the app and sign in

**Windows (PowerShell)**

```powershell
cd H:\FinWise
java -jar .\offline-finance-manager-1.0.0.jar
```

**macOS / Linux**

```bash
cd ~/FinWise
java -jar offline-finance-manager-1.0.0.jar
```

You should see it start, then open <http://localhost:8080>.

**First sign-in**

- Username: `admin`
- Password: `admin123`

Immediately go to **Settings → Profile** and change the password.

### Useful start options

```powershell
# different port
java -jar .\offline-finance-manager-1.0.0.jar --server.port=9090

# allow other devices on the same Wi-Fi to connect (e.g. a tablet)
java -jar .\offline-finance-manager-1.0.0.jar --server.address=0.0.0.0 --server.port=8080

# run without the automatic backup timer
java -jar .\offline-finance-manager-1.0.0.jar --app.backup.enabled=false
```

If you use `--server.address=0.0.0.0`, allow the port through the firewall when Windows asks, and only do this on a network you trust.

---

## 9. Move your existing data to the new laptop

To carry your records over:

1. On the **old** laptop, open **Settings → Data & Backup** and create a backup, or just stop the app and copy the `data/` folder.
2. Copy the `data/` folder to the new laptop (or restore the `.finwise` backup inside the app).
3. **Important:** also copy `data/backups/.backup.key`. Without it, encrypted backups cannot be opened.
4. Start the app and sign in.

You can also export everything from **Profile → Download my data** and import it on the other machine.

---

## 10. Run it automatically at startup (optional)

### Windows — Task Scheduler

```powershell
$action  = New-ScheduledTaskAction -Execute "java" -Argument "-jar ""C:\FinWise\offline-finance-manager-1.0.0.jar""" -WorkingDirectory "C:\FinWise"
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable
Register-ScheduledTask -TaskName "FinWise" -Action $action -Trigger $trigger -Settings $settings -Description "FinWise offline finance manager"
```

### Linux — systemd

Create `/etc/systemd/system/finwise.service`:

```ini
[Unit]
Description=FinWise Offline Finance Manager
After=network.target

[Service]
Type=simple
User=YOUR_USERNAME
WorkingDirectory=/home/YOUR_USERNAME/FinWise
ExecStart=/usr/bin/java -jar /home/YOUR_USERNAME/FinWise/offline-finance-manager-1.0.0.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now finwise
sudo systemctl status finwise
```

### macOS — launchd

Create `~/Library/LaunchAgents/com.finwise.app.plist` with `ProgramArguments` pointing at the JAR and `WorkingDirectory` at the project folder, then:

```bash
launchctl load ~/Library/LaunchAgents/com.finwise.app.plist
```

---

## 11. Verify the installation

| Check | Command | Expected |
| --- | --- | --- |
| Java present | `java -version` | `17` or higher |
| Compiler present | `javac -version` | `17` or higher |
| Maven present (if building) | `mvn -v` | `3.8`+ |
| App starts | `java -jar ...jar` | `Started OfflineFinanceApplication` |
| Web page answers | `curl -I http://localhost:8080/login` | `HTTP/1.1 200` |
| Database created | `dir data\finance.db` | file exists |
| Log written | `dir logs\finance.log` | file exists |

Quick browser check of every page after signing in: dashboard, expenses, income, budgets, goals, accounts, categories, analytics, reports, import, scan, notifications, habits, chatbot, audit, finance-plus, settings, account.

Run the automated checks (source setup only):

```powershell
mvn test
mvn clean verify
```

---

## 12. Troubleshooting

**`java` is not recognized**
Java is not installed or not on `PATH`. Reinstall the JDK and set `JAVA_HOME` + `PATH` (Section 4), then open a **new** terminal.

**`mvn` is not recognized**
Maven is not installed or its `bin` folder is not on `PATH` (Section 5). Or build the JAR once on a machine that has Maven and copy the JAR over (Option A).

**`JAVA_HOME is set to an invalid directory`**
`JAVA_HOME` must point at the JDK folder itself (the one containing `bin\java.exe`), not at `bin` and not at a version-specific subfolder. Check with `echo $env:JAVA_HOME` (Windows) or `echo $JAVA_HOME` (Mac/Linux).

**Port 8080 already in use**
```powershell
java -jar .\offline-finance-manager-1.0.0.jar --server.port=9090
```
or find who is using it: `netstat -ano | findstr :8080`

**`Address already in use` / app does not start**
An earlier FinWise or another program holds the port. Close it, or use a different port. On Windows, `run.bat` kills old FinWise processes automatically.

**Build fails: `Unable to delete target\...jar`**
The old app is still running. Stop it, then run `mvn clean package` again.

**Browser shows "connection refused"**
The app is not running yet or is on another port. Watch the terminal output; the app is ready when it prints the Tomcat started message.

**`no such table: users` or database errors**
You started the program from the wrong folder. The `data/` folder must sit next to the JAR or the project files. `cd` into the project folder first.

**Receipt/PDF reading returns nothing**
- Confirm `data/tessdata/eng.traineddata` exists.
- Try a straighter, well-lit, higher-contrast photo.
- Handwriting is often not readable; typed receipts work far better.

**Database will not open / looks damaged**
Stop the app, copy `data/finance.db` somewhere safe, delete it, and start again — the tables are recreated empty. Then restore a backup from `data/backups/`.

**Antivirus or SmartScreen blocks the JAR**
Right-click → *Properties* → tick *Unblock* → Apply. Or mark the folder as trusted.

**PowerShell says "running scripts is disabled"**
```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```
Or run `run.bat` from Command Prompt instead.

**Path contains spaces or non-English characters**
`run.bat` handles this, but if you start the JAR yourself, wrap the path in quotes: `java -jar "C:\My FinWise\offline-finance-manager-1.0.0.jar"`.

**OneDrive/Desktop folders cause weird failures**
Keep the project in a plain local folder such as `C:\FinWise` or `~/FinWise`, not inside a cloud-synced folder.

**Tests fail because of leftover data**
Tests use `target/`. Delete it and run again: `mvn clean test`.

---

## 13. Copy-paste summary

**Windows (PowerShell) — full source setup**

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Apache.Maven -e
winget install --id Git.Git -e
# close and reopen the terminal, then:
cd H:\FinWise
mkdir data, logs, data\tessdata, data\import-images, data\uploads\profile, data\backups -Force
# OCR language file (needed to read receipt photos and PDFs)
if (Test-Path ".\src\main\resources\tessdata\eng.traineddata") {
  Copy-Item ".\src\main\resources\tessdata\eng.traineddata" ".\data\tessdata\eng.traineddata" -Force
} else {
  Invoke-WebRequest -Uri "https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata" -OutFile ".\data\tessdata\eng.traineddata"
}
java -version
mvn -v
mvn clean verify
.\run.bat
```

**Windows (PowerShell) — JAR only**

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
# close and reopen the terminal, then:
cd H:\FinWise
mkdir data, logs, data\tessdata -Force
java -jar .\offline-finance-manager-1.0.0.jar
# open http://localhost:8080  ->  admin / admin123
```

**macOS / Linux — full source setup**

```bash
sudo apt install -y openjdk-17-jdk maven git     # Debian/Ubuntu
mkdir -p FinWise && cd FinWise
mkdir -p data/tessdata data/import-images data/uploads/profile data/backups logs
cp src/main/resources/tessdata/eng.traineddata data/tessdata/ 2>/dev/null || \
  curl -L -o data/tessdata/eng.traineddata https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata
java -version && mvn -v
mvn clean verify
./run.sh
```

---

## 14. What not to copy between laptops

- `target/` — rebuild it, it is machine-specific
- `logs/` — old logs are not needed
- `C/` — leftover scratch files
- `data/finance-e2e.db` — test database
- `*.iml`, `.idea/` — editor settings

**Do copy:** `data/` (especially `data/tessdata/` and `data/backups/.backup.key`), `pom.xml`, `src/`, `run.bat`, `run.sh`, `README.md`.

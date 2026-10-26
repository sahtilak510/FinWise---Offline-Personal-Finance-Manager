# FinWise Setup Guide

This guide explains how to download, install, and run FinWise. The steps below
focus on Windows, with terminal instructions for macOS and Linux too.

## What you need

- **Java JDK 17 or newer**
- **Maven 3.8 or newer**
- A web browser
- Internet access for the first build, so Maven can download dependencies

SQLite and the other application libraries are included as project dependencies.
You do not need to install a separate database server.

## Windows: easiest setup

### Quick process using Command Prompt

Open **Command Prompt** (CMD) and install Java:

```cmd
winget install --id EclipseAdoptium.Temurin.17.JDK -e
```

Close and reopen Command Prompt after Java installs. Install Maven using
Chocolatey if it is already installed:

```cmd
choco install maven -y
```

If you do not have Chocolatey, follow the manual Maven steps below. Then
download the project with Git:

```cmd
git clone https://github.com/sahtilak510/FinWise---Offline-Personal-Finance-Manager.git
cd FinWise---Offline-Personal-Finance-Manager
run.bat
```

If Git is not installed, download the project ZIP from GitHub, extract it,
open Command Prompt in the extracted project folder, and run:

```cmd
run.bat
```

When the app is ready, open <http://localhost:8080>. Keep Command Prompt open
while using FinWise; press **Ctrl+C** there to stop the app.

To add FinWise to your computer's apps, select **Install on this computer** on
the landing page, or use your browser's **Install FinWise** option. The
installed app opens the local web interface; FinWise must still be running on
your computer, so start it with `run.bat` before opening the installed app.

### 1. Install Java

Open PowerShell or Windows Terminal and run:

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
```

Close and reopen the terminal, then check Java:

```powershell
java -version
```

It should report Java 17 or newer.

### 2. Install Maven

Download the **Binary zip archive** from the
[Apache Maven download page](https://maven.apache.org/download.cgi), then:

1. Extract it to a folder, for example `C:\Tools\apache-maven`.
2. Add Maven's `bin` folder (for example, `C:\Tools\apache-maven\bin`) to your
   Windows **Path** environment variable.
3. Open a new terminal and check Maven:

   ```powershell
   mvn -version
   ```

If you already use Chocolatey, you can install Maven with
`choco install maven`. If you already use Scoop, you can use `scoop install
maven`.

### 3. Download the project

Choose either method:

- **Download ZIP:** Open the
  [FinWise GitHub repository](https://github.com/sahtilak510/FinWise---Offline-Personal-Finance-Manager),
  select **Code → Download ZIP**, and extract the ZIP.
- **Clone with Git:** Open a terminal where you want the project and run:

  ```powershell
  git clone https://github.com/sahtilak510/FinWise---Offline-Personal-Finance-Manager.git
  cd FinWise---Offline-Personal-Finance-Manager
  ```

### 4. Start FinWise

Open PowerShell in the extracted/cloned project folder. You should see files
such as `pom.xml`, `run.bat`, and `src` in that folder. Then run:

```powershell
.\run.bat
```

The script builds the application, starts it, and opens the browser when ready.
Keep the terminal window open while using FinWise. Press **Ctrl+C** to stop it.

Open <http://localhost:8080> if the browser does not open automatically. The
default admin login is:

- Username: `admin`
- Password: `admin123`

Change the default password before storing real financial information.

## Windows: run commands yourself

You can build and run the project without the launcher. In PowerShell, from the
project folder:

```powershell
mvn clean package -DskipTests
java -jar .\target\offline-finance-manager-1.0.0.jar
```

Alternatively, start directly from source:

```powershell
mvn spring-boot:run
```

In either case, open <http://localhost:8080>. Keep the terminal open while the
application is running and press **Ctrl+C** to stop it. The first Maven build
downloads dependencies; later builds can use Maven's local cache.

To run the tests instead of skipping them during a package build:

```powershell
mvn clean verify
```

## Using FinWise on a phone or tablet

FinWise is a responsive web app, so the same pages adapt to desktop, tablet,
and phone-sized screens. The mobile layout provides a navigation drawer and
stacks page sections and forms vertically. Wide tables remain readable in a
horizontal scroll area rather than stretching the entire page.

There is no separate mobile app to install. FinWise runs on the computer where
you start it and saves data there. Opening `localhost` on a phone refers to
the phone itself, not your computer; this setup guide does not configure or
recommend exposing your finance application to a network.

## Other ways to install

### Install Java with a downloaded installer

Instead of `winget`, download the JDK 17 Windows installer from
[Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17). Run the
installer, then open a new terminal and check `java -version`.

### Use an IDE

You can open the project folder in an IDE such as IntelliJ IDEA or VS Code with
Java support. Install a JDK 17+ and Maven first. Import/open the folder that
contains `pom.xml`, wait for Maven dependencies to load, then run
`com.finance.app.OfflineFinanceApplication` or use the IDE's terminal commands
above.

### Run a prebuilt JAR

If someone provides you with the built file
`target\offline-finance-manager-1.0.0.jar`, you can run that file with Java
without installing Maven:

```powershell
java -jar .\offline-finance-manager-1.0.0.jar
```

The JAR must be in the current folder for this exact command; otherwise provide
its full path. Java 17+ is still required. Keep the `data` folder alongside the
application so local records and uploaded files remain together.

## macOS and Linux

Install Java 17+ and Maven using your system's package manager. For example:

**macOS with Homebrew**

```bash
brew install --cask temurin@17
brew install maven
```

**Ubuntu or Debian**

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven
```

Download the project ZIP from GitHub and extract it, or clone it with Git:

```bash
git clone https://github.com/sahtilak510/FinWise---Offline-Personal-Finance-Manager.git
cd FinWise---Offline-Personal-Finance-Manager
```

Run it from the project folder:

```bash
./run.sh
```

Or run the Maven commands directly:

```bash
mvn spring-boot:run
```

Then visit <http://localhost:8080>. You may need to allow the launcher to run
first with `chmod +x run.sh`.

## Where the application saves data

FinWise creates its local data and log folders in the project directory:

- `data/finance.db` — main SQLite database
- `data/Finwise_Data.xlsx` — workbook used by some application features
- `data/import-images/` and `data/uploads/profile/` — uploaded images
- `data/backups/` and `data/finance-plus/backups/` — backup files
- `logs/finance.log` — application log

Some files, such as receipts and Finance Plus attachments, are stored inside the
database. Stop FinWise before manually copying its data. Keep the database,
workbook, uploads, backups, and any required backup keys/passphrases safe
together.

## Troubleshooting

- **`java` or `mvn` is not recognized:** Install the missing tool, add it to
  Windows `Path`, then open a new terminal.
- **The application does not build:** Check `java -version` and `mvn -version`,
  make sure the first build has internet access, and read the Maven error in
  the terminal.
- **Port 8080 is already in use:** Stop the other application using that port
  or configure FinWise to use a different port.
- **The browser does not open:** Open <http://localhost:8080> manually after
  the application reports it is running.
- **Do not delete `data/` to fix a startup problem:** It contains your local
  financial records and files. Back it up before attempting recovery.

For more information about features, routes, and project structure, see
[README.md](README.md). For a concise feature summary, see
[notes/features.txt](notes/features.txt).
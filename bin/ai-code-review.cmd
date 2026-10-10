@echo off
REM ai-code-review Windows launcher
REM NOTE: keep this file ASCII-only. Non-ASCII comments break under GBK codepage.
setlocal enabledelayedexpansion

SET "BIN_DIR=%~dp0"
FOR %%I IN ("%BIN_DIR%..") DO SET "PKG_ROOT=%%~fI"
SET "JAR=%PKG_ROOT%\lib\review.jar"

where java >nul 2>&1
IF ERRORLEVEL 1 (
    echo ERROR: java not found in PATH. Please install JDK 17+.
    exit /b 127
)

IF NOT EXIST "%JAR%" (
    echo ERROR: review.jar not found at %JAR%
    echo Please reinstall, or run "mvn clean package" in the source directory.
    exit /b 2
)

IF "%~1"=="" GOTO :help
IF "%~1"=="-h" GOTO :help
IF "%~1"=="--help" GOTO :help
IF "%~1"=="help" GOTO :help
IF "%~1"=="-v" GOTO :version
IF "%~1"=="--version" GOTO :version
IF "%~1"=="version" GOTO :version

java -Dfile.encoding=UTF-8 %AI_REVIEW_JAVA_OPTS% -jar "%JAR%" %*
exit /b %ERRORLEVEL%

:help
java -jar "%JAR%" --help
exit /b %ERRORLEVEL%

:version
java -jar "%JAR%" --version
exit /b %ERRORLEVEL%
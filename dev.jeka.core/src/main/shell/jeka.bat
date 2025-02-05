@echo off
setlocal enableDelayedExpansion

@rem Change here the default JVM options
@rem SET JEKA_OPTS == ""

@rem set terminal encoding to utf-8
chcp 65001 > nul

set "JAVA_CMD=java"
if not "%JEKA_JDK%" == "" set "JAVA_HOME=%JEKA_JDK%"
if not "%JAVA_HOME%" == "" (
    set "JAVA_CMD=%JAVA_HOME%\bin\java"
    if not exist "!JAVA_CMD!.exe" (
        echo !JAVA_CMD! not found
        if not "%JEKA_JDK%" == "" (
            echo JEKA_JDK environment variable is pointing to invalid JDK directory %JEKA_JDK%
        ) else (
            echo JAVA_HOME environment variable is pointing to invalid JDK directory %JAVA_HOME%
            echo Please set JAVA_HOME or JEKA_JDK environment variable to point on a valid JDK directory.
        )
        exit /b 1
    )
)

if exist "%cd%\jeka\boot" set "LOCAL_BUILD_DIR=.\jeka\boot\*;"
if "%JEKA_HOME%" == "" set "JEKA_HOME=%~dp0"

rem Ensure that the Jeka jar is actually in JEKA_HOME
if not exist "%JEKA_HOME%\dev.jeka.jeka-core.jar" (
	echo Could not find "dev.jeka.jeka-core.jar" in "%JEKA_HOME%"
	echo Please ensure JEKA_HOME points to the correct directory
	echo or that the distrib.zip file has been extracted fully
	rem Pause before exiting so the user can read the message first
	pause
	exit /b 1
)
set "COMMAND="%JAVA_CMD%" %JEKA_OPTS% -cp "%LOCAL_BUILD_DIR%%JEKA_HOME%\dev.jeka.jeka-core.jar" dev.jeka.core.tool.Main %*"
if not "%JEKA_ECHO_CMD%" == "" (
	@echo on
	echo %COMMAND%
	@echo off)
%COMMAND%

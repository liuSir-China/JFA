@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Build Linux runnable package from this JFA source tree.
REM Output:
REM   dist\jfa-1.0.0\
REM   dist\jfa-1.0.0-linux-x86_64.tar.gz
REM   ..\jfa-1.0.0-linux-x86_64.tar.gz
REM Requires JDK 8 + Maven.

set "PROJECT=%~dp0"
if "%PROJECT:~-1%"=="\" set "PROJECT=%PROJECT:~0,-1%"
cd /d "%PROJECT%" || goto :FAIL

set "VERSION=1.0.0"
set "PKG_NAME=jfa-%VERSION%"
set "TAR_NAME=jfa-%VERSION%-linux-x86_64.tar.gz"

if "%JAVA_HOME%"=="" (
  if exist "D:\java\bin\javac.exe" set "JAVA_HOME=D:\java"
)
if not "%JAVA_HOME%"=="" set "PATH=%JAVA_HOME%\bin;%PATH%"

set "MVN_CMD=mvn.cmd"
if exist "F:\install\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd" (
  set "MVN_CMD=F:\install\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd"
)

echo.
echo ========== Build JFA Linux package ==========
echo PROJECT   = %PROJECT%
echo JAVA_HOME = %JAVA_HOME%
echo MVN_CMD   = %MVN_CMD%
echo.

where java >nul 2>&1 || (echo [ERROR] java not found & goto :FAIL)
where javac >nul 2>&1 || (echo [ERROR] javac not found - need JDK 8 & goto :FAIL)

echo [1/4] mvn package ...
call "%MVN_CMD%" package
if errorlevel 1 (echo [ERROR] Maven build failed & goto :FAIL)

set "JAR=%PROJECT%\jfa-cli\target\jfa-cli-%VERSION%.jar"
if not exist "%JAR%" (
  echo [ERROR] Missing %JAR%
  dir /b "%PROJECT%\jfa-cli\target\*.jar" 2>nul
  goto :FAIL
)

echo [2/4] Assemble dist\%PKG_NAME% ...
set "DEST=%PROJECT%\dist\%PKG_NAME%"
if exist "%PROJECT%\dist" rmdir /s /q "%PROJECT%\dist"
mkdir "%DEST%\bin" "%DEST%\lib" "%DEST%\conf" "%DEST%\docs" "%DEST%\testdata" "%DEST%\reportfile"

copy /y "%JAR%" "%DEST%\lib\jfa.jar" >nul
if exist "%PROJECT%\conf\jfa.properties" copy /y "%PROJECT%\conf\jfa.properties" "%DEST%\conf\" >nul
if exist "%PROJECT%\README.md" copy /y "%PROJECT%\README.md" "%DEST\" >nul
if exist "%PROJECT%\docs\quickstart.md" copy /y "%PROJECT%\docs\quickstart.md" "%DEST%\docs\" >nul
if exist "%PROJECT%\docs\user-manual.md" copy /y "%PROJECT%\docs\user-manual.md" "%DEST%\docs\" >nul
if exist "%PROJECT%\testdata" xcopy /e /i /y /q "%PROJECT%\testdata\*" "%DEST%\testdata\" >nul

if not exist "%PROJECT%\scripts\jfa-launcher.sh" (
  echo [ERROR] missing scripts\jfa-launcher.sh
  goto :FAIL
)
REM Write bin wrappers with Unix LF endings (avoid bash\r on Linux).
powershell -NoProfile -ExecutionPolicy Bypass -Command "$names=@('jfa-launcher.sh','jfa','jfa-analyze','jfa-file-analyze','jfa-collect','jfa-config'); foreach($n in $names){ $src='%PROJECT%\scripts\'+$n; if(-not (Test-Path -LiteralPath $src)){ throw ('missing '+$src) }; $dst='%DEST%\bin\'+$n; $t=[IO.File]::ReadAllText($src) -replace [char]13+[char]10,[char]10 -replace [char]13,[char]10; if(-not $t.EndsWith([string][char]10)){ $t+=[char]10 }; [IO.File]::WriteAllBytes($dst,[Text.UTF8Encoding]::new($false).GetBytes($t)) }"
if errorlevel 1 (echo [ERROR] write bin wrappers failed & goto :FAIL)

echo [3/4] Create %TAR_NAME% ...
pushd "%PROJECT%\dist"
tar -czf "%TAR_NAME%" "%PKG_NAME%"if errorlevel 1 (popd & echo [ERROR] tar failed & goto :FAIL)
popd

echo [4/4] Copy to parent folder and remove old source tarball ...
set "PARENT=%PROJECT%\.."
copy /y "%PROJECT%\dist\%TAR_NAME%" "%PARENT%\%TAR_NAME%" >nul
if exist "%PARENT%\JFA-source.tar.gz" del /f /q "%PARENT%\JFA-source.tar.gz"

echo.
echo SUCCESS
echo   %PROJECT%\dist\%TAR_NAME%
echo   %PARENT%\%TAR_NAME%
echo.
echo Linux:
echo   tar -xzf %TAR_NAME%
echo   export JAVA_HOME=/path/to/jdk8
echo   ./%PKG_NAME%/bin/jfa --help
echo   ./%PKG_NAME%/bin/jfa-analyze
echo   ./%PKG_NAME%/bin/jfa-file-analyze
echo   ./%PKG_NAME%/bin/jfa-collect
echo   ./%PKG_NAME%/bin/jfa-config
echo.
pause
exit /b 0
:FAIL
echo.
echo FAILED.
pause
exit /b 1

@echo off
setlocal
set "MAVEN_VERSION=3.9.11"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_BIN=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd"
if not exist "%MAVEN_BIN%" (
  if "%JAVA_HOME%"=="" (
    echo JAVA_HOME is not set. Install Java 21 and set JAVA_HOME. 1>&2
    exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $root=Join-Path $env:USERPROFILE '.m2\wrapper\dists'; $zip=Join-Path $root 'apache-maven-%MAVEN_VERSION%.zip'; New-Item -ItemType Directory -Force -Path $root | Out-Null; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath '%MAVEN_HOME%' -Force; Remove-Item $zip"
)
if not exist "%MAVEN_BIN%" (
  echo Maven Wrapper could not prepare Maven. 1>&2
  exit /b 1
)
call "%MAVEN_BIN%" %*
exit /b %ERRORLEVEL%

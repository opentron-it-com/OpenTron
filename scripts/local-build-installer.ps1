$ErrorActionPreference = 'Stop'

Set-Location "c:\Users\ciorica\Documents\OpenTron"

$env:JAVA_HOME = "C:\Users\ciorica\Documents\jdk-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:PATH = "C:\Users\ciorica\Documents\apache-maven-3.9.16\bin;$env:PATH"
$env:PATH = "C:\Users\ciorica\Documents\node-v24.18.0-win-x64;C:\Users\ciorica\Documents\node-v24.18.0-win-x64\npm;C:\Users\ciorica\Documents\VC\Tools\MSVC\14.51.36231\bin\Hostx64\x64;C:\Users\ciorica\Documents\10\bin\10.0.26100.0\x64;C:\Users\ciorica\.cargo\bin;$env:PATH"
$env:LIB = "C:\Users\ciorica\Documents\VC\Tools\MSVC\14.51.36231\lib\x64;C:\Users\ciorica\Documents\10\Lib\10.0.26100.0\um\x64;C:\Users\ciorica\Documents\10\Lib\10.0.26100.0\ucrt\x64;$env:LIB"
$env:INCLUDE = "C:\Users\ciorica\Documents\VC\Tools\MSVC\14.51.36231\include;C:\Users\ciorica\Documents\10\Include\10.0.26100.0\um;C:\Users\ciorica\Documents\10\Include\10.0.26100.0\shared;C:\Users\ciorica\Documents\10\Include\10.0.26100.0\ucrt"

Write-Host "== Tool Versions =="
java -version
mvn -v
npm -v
node -v
cargo -V
rustup -V

Write-Host "== Build backend jar =="
Set-Location "c:\Users\ciorica\Documents\OpenTron\java\opentron-java\backend"
mvn clean package -DskipTests=true
$jar = Get-ChildItem "target\*-exec.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "No *-exec.jar produced" }

Write-Host "== Stage sidecar =="
$sidecar = "c:\Users\ciorica\Documents\OpenTron\frontend\src-tauri\sidecar"
New-Item -ItemType Directory -Force -Path $sidecar | Out-Null
Copy-Item $jar.FullName -Destination (Join-Path $sidecar "backend.jar") -Force

$jreOut = Join-Path $sidecar "jre"
if (Test-Path $jreOut) { Remove-Item -Recurse -Force $jreOut }
& "$env:JAVA_HOME\bin\jlink.exe" --add-modules java.base,java.logging,java.net.http,java.management,java.naming,java.sql,java.transaction.xa,java.security.jgss,jdk.charsets,jdk.unsupported,java.xml,java.desktop,java.instrument,jdk.management,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --compress=2 --output $jreOut
if (-not (Test-Path (Join-Path $jreOut "bin\java.exe"))) { throw "Minimal JRE missing java.exe" }

Write-Host "== Build frontend installer =="
Set-Location "c:\Users\ciorica\Documents\OpenTron\frontend"
if (-not (Test-Path "node_modules")) { npm ci --no-audit --no-fund }
npm run tauri build

Write-Host "== Installer outputs =="
$bundle = "c:\Users\ciorica\Documents\OpenTron\frontend\src-tauri\target\release\bundle"
if (Test-Path $bundle) {
  Get-ChildItem -Recurse $bundle |
    Where-Object { $_.Extension -in '.msi', '.exe' } |
    Select-Object FullName, Length, LastWriteTime |
    Format-Table -AutoSize
} else {
  Write-Host "Bundle folder not found"
}

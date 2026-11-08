# WiFi 带宽检测 APK 一键构建脚本
# 用法：pwsh -ExecutionPolicy Bypass -File .\build.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$SDK  = "D:\android-sdk"
$JDK  = "D:\tool\apk-tools\jdk-17.0.14+7"
$BT   = Join-Path $SDK "build-tools\35.0.0"
$JAR  = Join-Path $SDK "platforms\android-34\android.jar"

if (-not (Test-Path -LiteralPath $JDK)) { throw "找不到 JDK：$JDK" }
if (-not (Test-Path -LiteralPath $BT))  { throw "找不到 build-tools：$BT" }
if (-not (Test-Path -LiteralPath $JAR)) { throw "找不到 android.jar：$JAR" }

$env:JAVA_HOME = $JDK
$env:PATH = "$JDK\bin;$env:PATH"

$build = Join-Path $root "build"
$out   = Join-Path $root "out"
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build | Out-Null
New-Item -ItemType Directory -Force -Path "$build\gen" | Out-Null
New-Item -ItemType Directory -Force -Path "$build\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$build\dex" | Out-Null
New-Item -ItemType Directory -Force -Path $out | Out-Null

$apkName = "CarWifiCapTest_v1.0.0.apk"

Write-Output "[1/8] 编译资源 (aapt2 compile)"
& "$BT\aapt2.exe" compile --dir "$root\app\res" -o "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败" }

Write-Output "[2/8] 链接资源与清单 (aapt2 link)"
& "$BT\aapt2.exe" link -o "$build\base.apk" -I $JAR --manifest "$root\app\AndroidManifest.xml" -R "$build\res.zip" --java "$build\gen" --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }

Write-Output "[3/8] 编译 Java (javac)"
$srcs = Get-ChildItem -LiteralPath "$root\app\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$JDK\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $JAR -d "$build\classes" $srcs
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

Write-Output "[4/8] 打包 class (jar)"
& "$JDK\bin\jar.exe" cf "$build\classes.jar" -C "$build\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar 失败" }

Write-Output "[5/8] 转换 dex (d8)"
& "$BT\d8.bat" --release --min-api 24 --lib $JAR --output "$build\dex" "$build\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }
if (-not (Test-Path -LiteralPath "$build\dex\classes.dex")) { throw "d8 没有产出 classes.dex" }

Write-Output "[6/8] 合并 dex 到 APK"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$build\base.apk", [System.IO.Compression.ZipArchiveMode]::Update)
$entry = $zip.CreateEntry("classes.dex", [System.IO.Compression.CompressionLevel]::Optimal)
$es = $entry.Open()
$bytes = [System.IO.File]::ReadAllBytes("$build\dex\classes.dex")
$es.Write($bytes, 0, $bytes.Length)
$es.Dispose()
$zip.Dispose()

Write-Output "[7/8] 对齐 (zipalign)"
& "$BT\zipalign.exe" -f -p 4 "$build\base.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

Write-Output "[8/8] 签名 (apksigner)"
$ks = Join-Path $root "keystore\debug.jks"
if (-not (Test-Path -LiteralPath $ks)) {
    New-Item -ItemType Directory -Force -Path (Join-Path $root "keystore") | Out-Null
    & "$JDK\bin\keytool.exe" -genkeypair -v -keystore $ks -storetype JKS -alias carwificap `
        -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android `
        -dname "CN=CarWifiCapTest, OU=DIY, O=DIY, L=Shanghai, ST=Shanghai, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "生成签名密钥失败" }
}
& "$BT\apksigner.bat" sign --ks $ks --ks-key-alias carwificap --ks-pass pass:android --key-pass pass:android --out "$out\$apkName" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "签名失败" }

Write-Output "--- 校验 ---"
& "$BT\apksigner.bat" verify -v "$out\$apkName"
$vcode = $LASTEXITCODE
& "$BT\aapt.exe" dump badging "$out\$apkName" | Select-Object -First 6

$info = Get-Item -LiteralPath "$out\$apkName"
Write-Output "完成：$($info.FullName)  大小 $([Math]::Round($info.Length/1KB,1)) KB  校验码 $vcode"

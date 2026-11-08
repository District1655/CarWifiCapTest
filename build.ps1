# WiFi 带宽检测 APK 一键构建脚本
# 用法：pwsh -ExecutionPolicy Bypass -File .\build.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$SDK  = "D:\android-sdk"
$JDK  = "D:\tool\apk-tools\jdk-17.0.14+7"
$BT   = Join-Path $SDK "build-tools\35.0.0"
$JAR  = Join-Path $SDK "platforms\android-34\android.jar"

# ===================== 签名固定（不要随便改） =====================
# Android 判断"能否覆盖安装"只看签名证书，密钥一换，已装版本就必须卸载重装。
# 下面这串指纹 = v1.0.0 首发使用的证书，所有后续版本必须沿用同一个 release.jks。
$ExpectedSha256 = "47:0D:58:FF:60:B2:50:67:76:7D:8A:A9:54:43:D7:D2:76:2B:4B:57:B3:21:A5:7C:2C:6B:4A:1C:DD:79:98:DD"
$ExpectedSha256Plain = "470d58ff60b25067767d8aa95443d7d2762b4b57b321a57c2c6b4a1cdd7998dd"
$KeyAlias = "carwificap"

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

Write-Output "[8/8] 签名 (apksigner) —— 固定密钥"
$ksCandidates = @(
    (Join-Path $root "keystore\release.jks"),
    "D:\tool\签名\CarWifiCapTest\release.jks"
)
$ks = $null
foreach ($c in $ksCandidates) {
    if (Test-Path -LiteralPath $c) {
        $ks = $c
        break
    }
}
if ($null -eq $ks) {
    throw @"
找不到签名密钥 release.jks，已中止构建。

请把密钥放回以下任一位置（不要重新生成！）：
  1) $(Join-Path $root 'keystore\release.jks')
  2) D:\tool\签名\CarWifiCapTest\release.jks

重新生成密钥会导致已安装的版本无法覆盖安装，只能卸载重装。
"@
}
Write-Output "    使用密钥：$ks"

# 密钥指纹校验：防止拿错密钥
$keyInfo = & "$JDK\bin\keytool.exe" -list -v -keystore $ks -storepass android -alias $KeyAlias 2>&1 | Out-String
$m = [regex]::Match($keyInfo, "SHA256:\s*([0-9A-Fa-f:]+)")
if (-not $m.Success) {
    throw "读不出密钥指纹（keystore 损坏或别名 $KeyAlias 不存在）：$ks"
}
$actualFp = $m.Groups[1].Value.Trim().ToUpper()
if ($actualFp -ne $ExpectedSha256.ToUpper()) {
    throw @"
签名密钥指纹不一致，已中止构建！

期望（v1.0.0 首发证书）：$ExpectedSha256
实际：$actualFp

用错密钥会导致已安装版本无法覆盖安装。请换回原来的 release.jks。
"@
}

& "$BT\apksigner.bat" sign --ks $ks --ks-key-alias $KeyAlias --ks-pass pass:android --key-pass pass:android --out "$out\$apkName" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "签名失败" }

Write-Output "--- 校验 ---"
& "$BT\apksigner.bat" verify -v "$out\$apkName"
$vcode = $LASTEXITCODE

# 校验产物里真实写进去的证书，而不是只信 keystore
$certOut = & "$BT\apksigner.bat" verify --print-certs "$out\$apkName" 2>&1 | Out-String
$cm = [regex]::Match($certOut, "SHA-256 digest:\s*([0-9a-fA-F]+)")
if (-not $cm.Success) { throw "读不出 APK 签名证书摘要" }
if ($cm.Groups[1].Value.ToLower() -ne $ExpectedSha256Plain) {
    throw "APK 内签名证书与固定指纹不符：$($cm.Groups[1].Value)"
}
Write-Output "签名指纹：$($cm.Groups[1].Value)  （与固定值一致）"

& "$BT\aapt.exe" dump badging "$out\$apkName" | Select-Object -First 6

$info = Get-Item -LiteralPath "$out\$apkName"
Write-Output "完成：$($info.FullName)  大小 $([Math]::Round($info.Length/1KB,1)) KB  校验码 $vcode"

# CarWifiCapTest · WiFi 带宽能力检测

给车机做 5GHz 频宽（20/40/80MHz）能力验证的轻量工具，附带 GitHub Release 在线更新。一个 APK，四个检测页 + 一个更新卡片，纯原生 Java，不依赖任何第三方库。

- 包名：`com.fyt.wificap`
- 版本：1.0.0（versionCode 10000）
- 最低支持：Android 7.0（API 24）｜目标：API 29
- 仓库：https://github.com/District1655/CarWifiCapTest
- 产物：`out\CarWifiCapTest_v1.0.0.apk`（约 48 KB，已签名）

## 目录结构

```
CarWifiCapTest\
├─ app\
│  ├─ AndroidManifest.xml
│  ├─ res\values\strings.xml
│  ├─ res\drawable\ic_launcher.png
│  └─ src\
│     ├─ com\fyt\wificap\
│     │  ├─ MainActivity.java     四个检测页 + 更新卡片
│     │  ├─ Probe.java            连接信息采集、频宽/信道换算、反射取值
│     │  ├─ P2pProbe.java         Wi-Fi Direct 组信息（投屏场景）
│     │  ├─ SpeedTest.java        TCP 吞吐实测
│     │  ├─ AppConfig.java        仓库、应用名、版本号常量
│     │  ├─ ApkInstaller.java     兜底下载器（自建 HTTP + 安装）
│     │  └─ ApkFileProvider.java  极简 FileProvider（不依赖 androidx）
│     └─ com\fyt\updater\         vendored 自 District1655/gh-updater
│        ├─ GhUpdater.java
│        ├─ ReleaseInfo.java
│        ├─ UpdaterConfig.java
│        └─ UpdateCallback.java
├─ build.ps1                      一键构建（离线：aapt2→javac→d8→zipalign→apksigner）
├─ keystore\debug.jks             自签名密钥（口令 android，不入库）
├─ build\                         中间产物（不入库）
└─ out\                           最终 APK（不入库，走 Release 分发）
```

## 重新构建

```powershell
cd CarWifiCapTest
pwsh -ExecutionPolicy Bypass -File .\build.ps1
```

用的就是本机 D 盘的 SDK 与 JDK，不联网、不走 Gradle。改代码直接重跑。

## 安装

U 盘拷贝后点开安装（需允许未知来源）；或

```powershell
D:\android-sdk\platform-tools\adb.exe install -r "out\CarWifiCapTest_v1.0.0.apk"
```

## 检测页

### 1. 连接诊断

自动刷新（1.5 秒），核心三样：热点广播带宽（AP 自己声明的）、协商速率（本机实际协商到的 PHY 速率）、自动结论。

### 2. 5G 扫描

列出周边 5GHz 热点：信道、带宽、频率、信号、11n/ac 标记，当前连接标绿。Android 10 起扫描需要定位权限 + 系统定位开关。

### 3. P2P 直连

CarWith / ICCOA 投屏走 Wi-Fi Direct，普通 Wi-Fi 此时断开是正常的。这页读 P2P 组：角色、工作频率、信道、DFS、客户端数。

### 4. 吞吐实测

两台设备装同一 APK，连同一张网：车机做服务端记 IP，手机做客户端填 IP，跑 8 秒取平均 Mbps。≥60 说明 80MHz 链路真的可用；20~45 基本是 40MHz。

## 在线更新模块

接入的是自家 [gh-updater](https://github.com/District1655/gh-updater)，检查 → 下载 → 安装三步，全部在「连接诊断」页底部那张卡片里：

- 启动 3 秒后静默检查一次，有新版才提示；
- 卡片显示当前版本 / 新版本号 / 更新说明，点「下载并安装」走 DownloadManager；
- 若车机固件精简掉了 DownloadProvider（`DownloadManager` 不可用），自动降级到 `ApkInstaller` 自建 HTTP 下载 + 自建 `ApkFileProvider` 调起安装；
- Android 8+ 未授权「安装未知应用」时，会直接跳到系统授权页。

约定（与 gh-updater 一致）：

| 项目 | 规则 |
| --- | --- |
| tag | `vX.Y.Z`，如 `v1.0.1` |
| versionCode | `X*10000 + Y*100 + Z`，v1.0.1 → 10001 |
| Release 附件 | 文件名以 `CarWifiCapTest` 开头，如 `CarWifiCapTest_v1.0.1.apk` |
| 仓库可见性 | **必须公开**，App 内不带 token，私有仓库的 API 会 404 |

发新版本的流程：

```powershell
# 1. 改 AppConfig.VERSION_CODE / VERSION_NAME 与 manifest 的 versionCode/versionName
# 2. 构建
pwsh -File .\build.ps1
# 3. 打 tag + 发 Release（附件名要对上）
git tag v1.0.1; git push origin v1.0.1
gh release create v1.0.1 "out\CarWifiCapTest_v1.0.1.apk" --title "v1.0.1" --notes "更新说明"
```

## 判定标准（Wi-Fi 5、2 天线）

| 频宽 | 协商速率 |
| --- | --- |
| 20MHz | 72 ~ 173 Mbps |
| 40MHz | 150 ~ 400 Mbps |
| 80MHz | 325 ~ 866 Mbps |
| 160MHz | 650 ~ 1733 Mbps |

单天线减半：80MHz 单天线 ≈ 433，与 40MHz 双天线 400 很近，所以落在重叠区间时要靠吞吐实测区分。

## 验证流程

1. 手机开 5G 热点，频宽 80MHz、信道 153；
2. 车机连上，看「协商速率」：≥650 基本确认 80MHz；
3. 再跑一次吞吐实测，≥60Mbps 才算真能吃下 1080P 投屏；
4. 投屏状态下看实际信道，切「P2P 直连」页。

## 已知限制

- 编译、签名、包内结构均已校验，但**未在真机（7862）上运行过**；代码里做了全局 try/catch 兜底，若有页面空白或闪退，截图发我按报错改。
- P2P 只能读到频率/信道，Android 没有公开接口读 P2P 协商频宽，只能靠吞吐实测代替。
- 部分深度定制固件屏蔽 Wi-Fi Direct 服务，P2P 页会给出提示。
- 扫描受系统限流，点太频繁可能返回空列表。
- 只签 v2/v3，Android 7 以下装不上。

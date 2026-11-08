package com.fyt.updater;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 通用 GitHub Release 在线更新器。
 *
 * <p>用法（3 步）：
 * <ol>
 *   <li>构建配置：{@code UpdaterConfig config = new UpdaterConfig.Builder("owner/repo").appName("xxx").currentVersion(code, name).build();}</li>
 *   <li>检查更新：{@code new GhUpdater(config).checkUpdate(callback);}</li>
 *   <li>下载安装：{@code updater.downloadAndInstall(context);}</li>
 * </ol>
 *
 * <p>或者一行式：{@code GhUpdater.checkAndInstall(context, config, callback);}
 *
 * <p>线程：{@link #checkUpdate} 在子线程执行网络请求，回调在主线程。
 * {@link #downloadAndInstall} 异步下载，完成后弹安装界面。
 */
public final class GhUpdater {

    private static final String GITHUB_API_BASE = "https://api.github.com/repos/";

    private final UpdaterConfig config;
    private final Handler mainHandler;
    private ReleaseInfo latestRelease;

    public GhUpdater(UpdaterConfig config) {
        this.config = config;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ------------------------------------------------------------------
    // 检查更新
    // ------------------------------------------------------------------

    /**
     * 异步检查最新 Release，回调在主线程。
     */
    public void checkUpdate(final UpdateCallback callback) {
        new Thread(() -> {
            final ReleaseInfo release = checkLatestSync();
            mainHandler.post(() -> {
                if (release == null) {
                    callback.onError("检查失败（网络不通或仓库无 Release）");
                    return;
                }
                latestRelease = release;
                if (isNewer(release)) {
                    callback.onUpdateAvailable(release);
                } else {
                    callback.onUpToDate();
                }
            });
        }).start();
    }

    /**
     * 同步检查（需在子线程调用）。
     */
    public ReleaseInfo checkLatestSync() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GITHUB_API_BASE + config.repo + "/releases/latest");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", config.appName);
            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(config.logTag, "检查更新 HTTP " + code);
                return null;
            }
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            String tag = root.optString("tag_name", "");
            String body = root.optString("body", "");
            int versionCode = parseTagVersion(tag);
            String downloadUrl = null;
            JSONArray assets = root.optJSONArray("assets");
            if (assets != null) {
                String fallback = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String name = a.optString("name", "");
                    if (!name.endsWith(".apk")) {
                        continue;
                    }
                    if (fallback == null) {
                        fallback = a.optString("browser_download_url", "");
                    }
                    if (config.appName != null && !config.appName.isEmpty()
                            && name.startsWith(config.appName)) {
                        downloadUrl = a.optString("browser_download_url", "");
                        break;
                    }
                }
                if (downloadUrl == null) {
                    downloadUrl = fallback;
                }
            }
            return new ReleaseInfo(tag, body, downloadUrl, versionCode);
        } catch (Throwable t) {
            Log.e(config.logTag, "检查更新失败", t);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 判断 Release 是否比当前版本新。
     */
    public boolean isNewer(ReleaseInfo release) {
        return release != null && release.hasDownload()
                && release.versionCode > config.currentVersionCode;
    }

    // ------------------------------------------------------------------
    // 下载与安装
    // ------------------------------------------------------------------

    /**
     * 下载最新 Release 并自动安装。需先调用 {@link #checkUpdate} 成功。
     *
     * @return downloadId（-1 表示失败）
     */
    public long downloadAndInstall(final Context context) {
        if (latestRelease == null || !latestRelease.hasDownload()) {
            Log.w(config.logTag, "未检查到新版本，无法下载");
            return -1;
        }
        return downloadAndInstall(context, latestRelease.downloadUrl);
    }

    /**
     * 下载指定 URL 的 APK 并自动安装（下载完成后弹安装界面）。
     *
     * @return downloadId（-1 表示失败）
     */
    public long downloadAndInstall(final Context context, String downloadUrl) {
        final long downloadId = downloadApk(context, downloadUrl);
        if (downloadId < 0) {
            Log.e(config.logTag, "发起下载失败");
            return -1;
        }
        registerDownloadReceiver(context, downloadId);
        return downloadId;
    }

    /**
     * 通过 DownloadManager 下载 APK。
     */
    public long downloadApk(Context context, String url) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(config.appName + " 更新");
            req.setDescription("正在下载新版本 APK");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String filename = (config.appName != null && !config.appName.isEmpty())
                    ? config.appName + ".apk" : "update.apk";
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            return dm.enqueue(req);
        } catch (Throwable t) {
            Log.e(config.logTag, "发起下载失败", t);
            return -1;
        }
    }

    /**
     * 注册下载完成广播接收器，下载完成后自动弹安装界面。
     */
    public void registerDownloadReceiver(Context context, final long downloadId) {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) {
                    return;
                }
                ctx.unregisterReceiver(this);
                Uri uri = getDownloadedApkUri(ctx, downloadId);
                if (uri != null) {
                    installApk(ctx, uri);
                }
            }
        }, filter);
    }

    /**
     * 获取下载完成的 APK Uri。
     */
    public Uri getDownloadedApkUri(Context context, long downloadId) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = dm.getUriForDownloadedFile(downloadId);
            if (uri != null) {
                return uri;
            }
            String filename = (config.appName != null && !config.appName.isEmpty())
                    ? config.appName + ".apk" : "update.apk";
            return Uri.fromFile(new java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    filename));
        } catch (Throwable t) {
            Log.e(config.logTag, "获取下载文件失败", t);
            return null;
        }
    }

    /**
     * 安装 APK。
     */
    public void installApk(Context context, Uri apkUri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            Log.i(config.logTag, "发起安装: " + apkUri);
        } catch (Throwable t) {
            Log.e(config.logTag, "发起安装失败（请在系统设置允许未知来源安装）", t);
        }
    }

    /**
     * 查询下载状态（供 UI 显示进度）。
     *
     * @return DownloadManager.STATUS_* 之一，-1 表示查询失败
     */
    public int queryDownloadStatus(Context context, long downloadId) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(downloadId);
            Cursor c = dm.query(q);
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                c.close();
                return status;
            }
        } catch (Throwable t) {
            Log.e(config.logTag, "queryDownloadStatus", t);
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 一行式便捷方法
    // ------------------------------------------------------------------

    /**
     * 一行式：检查 + 如果有更新就自动下载安装。
     *
     * <pre>
     * GhUpdater.checkAndInstall(this, config, new UpdateCallback() {
     *     public void onUpdateAvailable(ReleaseInfo r) { Toast.makeText(this, "发现新版本 v"+r.tagName, 0).show(); }
     * });
     * </pre>
     */
    public static void checkAndInstall(final Context context, UpdaterConfig config, UpdateCallback callback) {
        final GhUpdater updater = new GhUpdater(config);
        updater.checkUpdate(new UpdateCallback() {
            @Override
            public void onUpdateAvailable(ReleaseInfo release) {
                callback.onUpdateAvailable(release);
                updater.downloadAndInstall(context);
            }

            @Override
            public void onUpToDate() {
                callback.onUpToDate();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    /**
     * 解析 tag（如 "v1.2.3"）为 versionCode（1*10000 + 2*100 + 3 = 10203）。
     */
    public static int parseTagVersion(String tag) {
        try {
            String v = tag.replaceAll("[^0-9.]", "");
            String[] parts = v.split("\\.");
            int code = 0;
            int[] weight = {10000, 100, 1};
            for (int i = 0; i < parts.length && i < weight.length; i++) {
                code += Integer.parseInt(parts[i]) * weight[i];
            }
            return code;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 获取最近一次 checkUpdate 的结果（可能为 null）。
     */
    public ReleaseInfo getLatestRelease() {
        return latestRelease;
    }
}

package com.fyt.wificap;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 兜底下载器：车机固件里如果没有 DownloadProvider（DownloadManager 不可用），
 * 就走这里自建 HTTP 下载 + 自建 FileProvider 安装，避免更新模块在部分公版固件上直接失效。
 */
public class ApkInstaller {

    public interface Callback {
        void onProgress(long done, long total);
        void onDone(File apk);
        void onError(String msg);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static File apkDir(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) {
            base = ctx.getFilesDir();
        }
        File dir = new File(base, "update");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void downloadAsync(final Context ctx, final String url, final String fileName,
                                     final Callback cb) {
        Thread th = new Thread(new Runnable() {
            public void run() {
                HttpURLConnection conn = null;
                FileOutputStream fos = null;
                InputStream in = null;
                try {
                    URL u = new URL(url);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", AppConfig.APP_NAME);
                    int code = conn.getResponseCode();
                    if (code != 200) {
                        throw new RuntimeException("HTTP " + code);
                    }
                    long total = conn.getContentLength();
                    File dir = apkDir(ctx);
                    File f = new File(dir, fileName);
                    if (f.exists() && !f.delete()) {
                        // 删不掉就换个名字，避免写不进去
                        f = new File(dir, "update_" + System.currentTimeMillis() + ".apk");
                    }
                    in = conn.getInputStream();
                    fos = new FileOutputStream(f);
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    long lastReport = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastReport > 400) {
                            lastReport = now;
                            postProgress(cb, done, total);
                        }
                    }
                    fos.flush();
                    fos.close();
                    fos = null;
                    in.close();
                    in = null;
                    if (total > 0 && done < total * 0.9) {
                        throw new RuntimeException("下载不完整");
                    }
                    postDone(cb, f);
                } catch (Throwable t) {
                    postError(cb, "下载失败：" + t);
                } finally {
                    try {
                        if (fos != null) fos.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                    try {
                        if (in != null) in.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                    if (conn != null) conn.disconnect();
                }
            }
        }, "apk-downloader");
        th.start();
    }

    public static void install(Context ctx, File apk) {
        Uri uri = ApkFileProvider.uriFor(ctx, apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(i);
    }

    private static void postProgress(final Callback cb, final long done, final long total) {
        UI.post(new Runnable() {
            public void run() {
                if (cb != null) cb.onProgress(done, total);
            }
        });
    }

    private static void postDone(final Callback cb, final File f) {
        UI.post(new Runnable() {
            public void run() {
                if (cb != null) cb.onDone(f);
            }
        });
    }

    private static void postError(final Callback cb, final String msg) {
        UI.post(new Runnable() {
            public void run() {
                if (cb != null) cb.onError(msg);
            }
        });
    }
}

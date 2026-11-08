package com.fyt.updater;

/**
 * GitHub Release 信息（不可变）。
 */
public final class ReleaseInfo {
    public final String tagName;
    public final String body;
    public final String downloadUrl;
    public final int versionCode;

    ReleaseInfo(String tag, String body, String url, int code) {
        this.tagName = tag;
        this.body = body;
        this.downloadUrl = url;
        this.versionCode = code;
    }

    public boolean hasDownload() {
        return downloadUrl != null && !downloadUrl.isEmpty();
    }

    @Override
    public String toString() {
        return "ReleaseInfo{tag=" + tagName + ", code=" + versionCode
                + ", url=" + downloadUrl + "}";
    }
}

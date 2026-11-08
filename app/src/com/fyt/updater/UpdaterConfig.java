package com.fyt.updater;

/**
 * 通用 GitHub Release 更新配置（不可变，Builder 模式）。
 *
 * 用法：
 * <pre>
 * UpdaterConfig config = new UpdaterConfig.Builder("District1655/fytTirePlus")
 *     .appName("fytTirePlus")
 *     .currentVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
 *     .logTag("fytTirePlus")
 *     .build();
 *
 * GhUpdater updater = new GhUpdater(config);
 * updater.checkUpdate(callback);          // 异步检查
 * updater.downloadAndInstall(context);   // 下载并安装（需先 checkUpdate）
 * </pre>
 */
public final class UpdaterConfig {

    public final String repo;          // "owner/repo"
    public final String appName;       // Release asset 名称前缀，如 "fytTirePlus"
    public final int currentVersionCode;
    public final String currentVersionName;
    public final String logTag;        // 日志前缀

    private UpdaterConfig(Builder b) {
        this.repo = b.repo;
        this.appName = b.appName;
        this.currentVersionCode = b.currentVersionCode;
        this.currentVersionName = b.currentVersionName;
        this.logTag = b.logTag;
    }

    public static final class Builder {
        private final String repo;
        private String appName = "";
        private int currentVersionCode = 0;
        private String currentVersionName = "";
        private String logTag = "GhUpdater";

        /**
         * @param repo GitHub 仓库全名，如 "District1655/fytTirePlus"
         */
        public Builder(String repo) {
            if (repo == null || repo.isEmpty() || !repo.contains("/")) {
                throw new IllegalArgumentException("repo must be 'owner/repo'");
            }
            this.repo = repo;
        }

        /**
         * @param appName Release asset 文件名前缀，如 "fytTirePlus"。
         *                 库会优先选择以此开头的 .apk 附件。
         */
        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        /**
         * @param versionCode 当前版本 code（与 Release tag 解析出的 code 比较）
         * @param versionName 当前版本名（仅用于日志）
         */
        public Builder currentVersion(int versionCode, String versionName) {
            this.currentVersionCode = versionCode;
            this.currentVersionName = versionName;
            return this;
        }

        /**
         * @param tag 日志前缀，默认 "GhUpdater"
         */
        public Builder logTag(String tag) {
            this.logTag = tag;
            return this;
        }

        public UpdaterConfig build() {
            return new UpdaterConfig(this);
        }
    }
}

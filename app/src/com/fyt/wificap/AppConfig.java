package com.fyt.wificap;

/**
 * 应用级常量。versionCode 必须遵循 X*10000 + Y*100 + Z（v1.2.3 → 10203），
 * 这样才能和 gh-updater 从 Release tag 解析出来的版本号对上。
 */
public final class AppConfig {

    /** GitHub 仓库（owner/repo），在线更新从这里取 latest release */
    public static final String REPO = "District1655/CarWifiCapTest";

    /** Release 附件名前缀，APK 文件名要以它开头 */
    public static final String APP_NAME = "CarWifiCapTest";

    public static final int VERSION_CODE = 10000;
    public static final String VERSION_NAME = "1.0.0";

    public static final String LOG_TAG = "CarWifiCapTest";

    private AppConfig() {
    }
}

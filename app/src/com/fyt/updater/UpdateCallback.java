package com.fyt.updater;

/**
 * 更新回调（所有方法在主线程回调）。
 */
public interface UpdateCallback {
    /**
     * 发现新版本（release.downloadUrl 非空）。
     */
    void onUpdateAvailable(ReleaseInfo release);

    /**
     * 当前已是最新版本，或仓库无 Release。
     */
    default void onUpToDate() {}

    /**
     * 检查失败（网络不通、仓库不存在、JSON 解析异常等）。
     */
    default void onError(String message) {}
}

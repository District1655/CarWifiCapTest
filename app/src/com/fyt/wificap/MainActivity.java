package com.fyt.wificap;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.fyt.updater.GhUpdater;
import com.fyt.updater.ReleaseInfo;
import com.fyt.updater.UpdateCallback;
import com.fyt.updater.UpdaterConfig;

public class MainActivity extends Activity {

    private static final int C_BG = 0xFF0B1220;
    private static final int C_CARD = 0xFF141E31;
    private static final int C_CARD2 = 0xFF1C2942;
    private static final int C_TEXT = 0xFFE8EEF8;
    private static final int C_DIM = 0xFF93A3BA;
    private static final int C_GREEN = 0xFF3DDC84;
    private static final int C_YEL = 0xFFFFB020;
    private static final int C_RED = 0xFFFF5A52;
    private static final int C_BLUE = 0xFF4C8DFF;
    private static final int C_LINE = 0xFF24344F;

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private static final int TAB_CONNECT = 0;
    private static final int TAB_SCAN = 1;
    private static final int TAB_P2P = 2;
    private static final int TAB_SPEED = 3;

    private final LinearLayout[] pages = new LinearLayout[4];
    private final ScrollView[] scrolls = new ScrollView[4];
    private final TextView[] tabs = new TextView[4];
    private int curTab = TAB_CONNECT;

    private TextView tvVerdict;
    private TextView tvVerdictSub;
    private LinearLayout infoBox;

    private TextView tvScanState;
    private LinearLayout scanBox;

    private TextView tvP2pState;
    private LinearLayout p2pBox;

    private TextView tvSpeedState;
    private TextView tvSpeedNow;
    private TextView tvSpeedDetail;
    private TextView tvServerIp;
    private EditText etHost;
    private TextView btnRoleServer;
    private TextView btnRoleClient;
    private TextView btnDirUp;
    private TextView btnDirDown;
    private TextView btnSpeedStart;

    private boolean speedIsServer = true;
    private boolean speedUplink = true;

    private WifiManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Probe lastProbe;
    private double maxLinkSpeed = 0;
    private String lastReport = "";

    private TextView tvUpdateState;
    private TextView tvUpdateBody;
    private TextView btnUpdateAction;
    private GhUpdater updater;
    private ReleaseInfo pendingRelease;
    private boolean updateBusy = false;

    private final SpeedTest speedTest = new SpeedTest();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(C_BG);
        } catch (Throwable t) {
            // ignore
        }
        try {
            wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        } catch (Throwable t) {
            wm = null;
        }
        buildUi();
        requestPerms();
        refreshConnection();
        ui.postDelayed(new Runnable() {
            public void run() {
                if (curTab == TAB_CONNECT) {
                    refreshConnection();
                }
                ui.postDelayed(this, 1500);
            }
        }, 1500);

        // 启动后静默查一次更新，查到新版本才在卡片上提示
        ui.postDelayed(new Runnable() {
            public void run() {
                checkUpdate(true);
            }
        }, 3200);
    }

    @Override
    protected void onDestroy() {
        speedTest.stop();
        super.onDestroy();
    }

    // ==================== UI 基础 ====================

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView label(String s, int color, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private GradientDrawable box(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            g.setStroke(dp(strokeDp), strokeColor);
        }
        return g;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(C_CARD, 12, 0, 0));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(5), 0, dp(5));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView section(String s) {
        TextView t = label(s, C_BLUE, 13, true);
        t.setPadding(0, dp(2), 0, dp(4));
        return t;
    }

    private void addRow(LinearLayout parent, String k, String v) {
        addRow(parent, k, v, C_TEXT);
    }

    private void addRow(LinearLayout parent, String k, String v, int vColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView kk = label(k, C_DIM, 13, false);
        kk.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView vv = label(v, vColor, 13, false);
        vv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        row.addView(kk);
        row.addView(vv);
        parent.addView(row);

        View line = new View(this);
        line.setBackgroundColor(C_LINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(1)));
        parent.addView(line);
    }

    private TextView btn(String text, int bgColor, int fgColor, final Runnable action) {
        TextView t = label(text, fgColor, 14, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(11), dp(10), dp(11));
        t.setBackground(box(bgColor, 10, 0, 0));
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    action.run();
                } catch (Throwable t2) {
                    toast("操作失败：" + t2);
                }
            }
        });
        return t;
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            // ignore
        }
    }

    // ==================== 页面装配 ====================

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setPadding(dp(16), dp(10), dp(16), dp(8));
        setContentView(root);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("WiFi 带宽能力检测", C_TEXT, 19, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(title);
        head.addView(label("v1.0", C_DIM, 12, false));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabRowLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tabRowLp.setMargins(0, dp(8), 0, dp(6));
        root.addView(tabRow, tabRowLp);

        String[] names = {"连接诊断", "5G 扫描", "P2P 直连", "吞吐实测"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = label(names[i], C_DIM, 14, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(9), 0, dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    switchTab(idx);
                }
            });
            tabs[i] = t;
            tabRow.addView(t);
        }

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        pages[TAB_CONNECT] = buildConnectPage();
        pages[TAB_SCAN] = buildScanPage();
        pages[TAB_P2P] = buildP2pPage();
        pages[TAB_SPEED] = buildSpeedPage();

        for (int i = 0; i < 4; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            sv.addView(pages[i], new FrameLayout.LayoutParams(MATCH, WRAP));
            content.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
            scrolls[i] = sv;
        }

        switchTab(TAB_CONNECT);
    }

    private void switchTab(int idx) {
        curTab = idx;
        for (int i = 0; i < 4; i++) {
            scrolls[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            if (i == idx) {
                tabs[i].setBackground(box(0xFF22406E, 10, C_BLUE, 1));
                tabs[i].setTextColor(0xFFFFFFFF);
            } else {
                tabs[i].setBackground(box(C_CARD, 10, 0, 0));
                tabs[i].setTextColor(C_DIM);
            }
        }
        if (idx == TAB_CONNECT) {
            refreshConnection();
        } else if (idx == TAB_P2P) {
            refreshP2p();
        }
    }

    // ==================== 页1：连接诊断 ====================

    private LinearLayout buildConnectPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout vcard = card();
        tvVerdict = label("检测中…", C_TEXT, 18, true);
        tvVerdict.setPadding(0, 0, 0, dp(6));
        vcard.addView(tvVerdict);
        tvVerdictSub = label("", C_DIM, 13, false);
        tvVerdictSub.setLineSpacing(dp(3), 1f);
        vcard.addView(tvVerdictSub);
        page.addView(vcard);

        LinearLayout icard = card();
        icard.addView(section("连接详情"));
        infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        icard.addView(infoBox);
        page.addView(icard);

        LinearLayout brow = new LinearLayout(this);
        brow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, WRAP);
        blp.setMargins(0, dp(4), 0, dp(4));
        page.addView(brow, blp);

        TextView bRefresh = btn("刷新", C_CARD2, C_TEXT, new Runnable() {
            public void run() {
                refreshConnection();
            }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp1.setMargins(0, 0, dp(6), 0);
        brow.addView(bRefresh, lp1);

        TextView bCopy = btn("复制报告", C_CARD2, C_TEXT, new Runnable() {
            public void run() {
                copyReport();
            }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp2.setMargins(dp(6), 0, 0, 0);
        brow.addView(bCopy, lp2);

        LinearLayout ucard = card();
        ucard.addView(section("在线更新 · GitHub Release"));
        tvUpdateState = label("当前 v" + AppConfig.VERSION_NAME
                + "（code " + AppConfig.VERSION_CODE + "）", C_TEXT, 13, false);
        ucard.addView(tvUpdateState);
        tvUpdateBody = label("", C_DIM, 12, false);
        tvUpdateBody.setLineSpacing(dp(2), 1f);
        tvUpdateBody.setPadding(0, dp(5), 0, 0);
        tvUpdateBody.setVisibility(View.GONE);
        ucard.addView(tvUpdateBody);
        btnUpdateAction = btn("检查更新", C_CARD2, C_TEXT, new Runnable() {
            public void run() {
                onUpdateButton();
            }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(MATCH, WRAP);
        ulp.setMargins(0, dp(8), 0, 0);
        ucard.addView(btnUpdateAction, ulp);
        page.addView(ucard);

        LinearLayout tip = card();
        tip.addView(section("协商速率 → 带宽 对照"));
        String[] tips = {
                "20MHz：72 ~ 173 Mbps",
                "40MHz：150 ~ 400 Mbps",
                "80MHz：325 ~ 866 Mbps",
                "160MHz：650 ~ 1733 Mbps",
                "",
                "测法：手机开 5G 热点、频宽手动设 80MHz，车机连上后看本页「协商速率」。",
                "速率 ≥ 650 基本可确认 80MHz（双天线）；433 多为 80MHz 单天线；400 及以下基本是 40MHz。",
                "想坐实结论，用「吞吐实测」跑一次：60Mbps 以上才真正吃得下 1080P 投屏。",
                "CarWith 投屏期间普通 Wi-Fi 会断开，此时看「P2P 直连」页的信道。"
        };
        for (int i = 0; i < tips.length; i++) {
            if (tips[i].length() == 0) {
                TextView blank = label("", C_DIM, 6, false);
                tip.addView(blank);
                continue;
            }
            TextView l = label(tips[i], C_DIM, 12, false);
            l.setPadding(0, dp(3), 0, dp(3));
            l.setLineSpacing(dp(2), 1f);
            tip.addView(l);
        }
        page.addView(tip);
        return page;
    }

    private void refreshConnection() {
        if (tvVerdict == null) {
            return;
        }
        try {
            Probe p = Probe.read(this);
            lastProbe = p;
            if (p.linkSpeed > maxLinkSpeed) {
                maxLinkSpeed = p.linkSpeed;
            }
            renderVerdict(p);
            renderInfo(p);
            buildReport(p);
        } catch (Throwable t) {
            tvVerdict.setText("读取失败：" + t);
            tvVerdict.setTextColor(C_RED);
        }
    }

    private void renderVerdict(Probe p) {
        int color;
        String title;
        String sub;
        int ls = p.linkSpeed;
        boolean ap80 = (p.apWidth == 2 || p.apWidth == 3 || p.apWidth == 4);
        boolean ap40 = (p.apWidth == 1);

        if (ls >= 600) {
            color = C_GREEN;
            title = "疑似已跑在 80MHz";
            sub = "协商速率 " + ls + " Mbps，对应 80MHz×2 天线（650~866）区间。";
        } else if (ls >= 380) {
            color = C_YEL;
            title = "落在 40/80MHz 重叠区间";
            sub = "协商速率 " + ls + " Mbps：可能是 80MHz 单天线(433)，也可能是 40MHz 双天线(400)，需用吞吐实测确认。";
        } else if (ls > 0) {
            color = C_RED;
            title = "疑似被限制在 40MHz 及以下";
            sub = "协商速率仅 " + ls + " Mbps，属于 20/40MHz 典型区间（72~400）。";
        } else {
            color = C_DIM;
            title = "没拿到连接信息";
            sub = "可能没连 Wi-Fi，也可能正处于 Wi-Fi Direct 直连（CarWith 投屏）状态，那属于正常现象。";
        }

        StringBuilder sb = new StringBuilder(sub);
        sb.append("\n");
        if (p.scanOk) {
            if (ap80) {
                sb.append("热点广播带宽：").append(p.widthName)
                        .append("（热点自身是 80MHz，若本机速率上不去，就是车机侧被限）");
            } else if (ap40) {
                sb.append("热点广播带宽：40MHz（先把手机热点改成 80MHz 再复测，否则测不出车机上限）");
            } else if (p.apWidth >= 0) {
                sb.append("热点广播带宽：").append(p.widthName);
            } else {
                sb.append("扫描结果里没匹配到当前 BSSID，带宽只能按速率推断");
            }
        } else {
            sb.append("没拿到扫描结果（需定位权限 + 系统定位开关打开），当前结论只依据协商速率");
        }

        tvVerdict.setText(title);
        tvVerdict.setTextColor(color);
        tvVerdictSub.setText(sb.toString());
    }

    private void renderInfo(Probe p) {
        infoBox.removeAllViews();
        addRow(infoBox, "设备", p.model);
        addRow(infoBox, "系统", p.system);
        addRow(infoBox, "SSID", p.ssid);
        addRow(infoBox, "BSSID", p.bssid.length() == 0 ? "未知" : p.bssid);
        addRow(infoBox, "频段", p.bandName);
        addRow(infoBox, "信道", p.channel > 0 ? String.valueOf(p.channel) : "未知");
        addRow(infoBox, "中心频率", p.freq > 0 ? p.freq + " MHz" : "未知");
        addRow(infoBox, "热点广播带宽", p.widthName + (p.apCenter.length() > 0 ? "（" + p.apCenter + "）" : ""));
        addRow(infoBox, "协商速率", p.linkSpeed > 0 ? p.linkSpeed + " Mbps" : "未知",
                p.linkSpeed >= 600 ? C_GREEN : (p.linkSpeed > 0 ? C_YEL : C_DIM));
        if (p.maxTx > 0) {
            addRow(infoBox, "本机最大 TX", p.maxTx + " Mbps");
        }
        addRow(infoBox, "本次最高观测", maxLinkSpeed > 0 ? ((int) maxLinkSpeed) + " Mbps" : "-");
        addRow(infoBox, "信号", p.rssi != 0 ? p.rssi + " dBm" : "未知");
        addRow(infoBox, "Wi-Fi 标准", p.standardName);
        addRow(infoBox, "DFS 信道", p.dfs ? "是（车载易跳频）" : "否", p.dfs ? C_YEL : C_TEXT);
        addRow(infoBox, "本机 IP", p.ip);
        addRow(infoBox, "周边 AP 数", p.scanOk ? String.valueOf(p.scanCount) : "无扫描权限");
    }

    private void buildReport(Probe p) {
        StringBuilder sb = new StringBuilder();
        sb.append("WiFi 带宽检测报告 ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                .append("\n");
        sb.append("设备：").append(p.model).append("\n");
        sb.append("系统：").append(p.system).append("\n");
        sb.append("SSID：").append(p.ssid).append("\n");
        sb.append("BSSID：").append(p.bssid).append("\n");
        sb.append("频段：").append(p.bandName).append("\n");
        sb.append("信道：").append(p.channel).append("\n");
        sb.append("中心频率：").append(p.freq).append(" MHz\n");
        sb.append("热点广播带宽：").append(p.widthName).append("\n");
        sb.append("协商速率：").append(p.linkSpeed).append(" Mbps\n");
        sb.append("本机最大TX：").append(p.maxTx).append(" Mbps\n");
        sb.append("本次最高观测：").append((int) maxLinkSpeed).append(" Mbps\n");
        sb.append("信号：").append(p.rssi).append(" dBm\n");
        sb.append("标准：").append(p.standardName).append("\n");
        sb.append("DFS：").append(p.dfs).append("\n");
        sb.append("IP：").append(p.ip).append("\n");
        sb.append("结论：").append(tvVerdict == null ? "" : tvVerdict.getText().toString()).append("\n");
        lastReport = sb.toString();
    }

    private void copyReport() {
        try {
            if (lastReport == null || lastReport.length() == 0) {
                toast("还没有报告，先点刷新");
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("wifi-report", lastReport));
            toast("报告已复制到剪贴板");
        } catch (Throwable t) {
            toast("复制失败：" + t);
        }
    }

    // ==================== 在线更新 ====================

    private void checkUpdate(final boolean silent) {
        if (tvUpdateState == null) {
            return;
        }
        updateBusy = true;
        if (!silent) {
            tvUpdateState.setText("检查中…");
            tvUpdateState.setTextColor(C_TEXT);
            tvUpdateBody.setVisibility(View.GONE);
            btnUpdateAction.setText("检查中…");
            btnUpdateAction.setEnabled(false);
        }
        try {
            if (updater == null) {
                UpdaterConfig cfg = new UpdaterConfig.Builder(AppConfig.REPO)
                        .appName(AppConfig.APP_NAME)
                        .currentVersion(AppConfig.VERSION_CODE, AppConfig.VERSION_NAME)
                        .logTag(AppConfig.LOG_TAG)
                        .build();
                updater = new GhUpdater(cfg);
            }
            updater.checkUpdate(new UpdateCallback() {
                public void onUpdateAvailable(final ReleaseInfo release) {
                    ui.post(new Runnable() {
                        public void run() {
                            updateBusy = false;
                            pendingRelease = release;
                            tvUpdateState.setText("发现新版本 " + release.tagName);
                            tvUpdateState.setTextColor(C_GREEN);
                            String body = release.body == null ? "" : release.body.trim();
                            if (body.length() > 240) {
                                body = body.substring(0, 240) + "…";
                            }
                            tvUpdateBody.setText(body);
                            tvUpdateBody.setVisibility(body.length() > 0 ? View.VISIBLE : View.GONE);
                            btnUpdateAction.setText("下载并安装");
                            btnUpdateAction.setBackground(box(C_BLUE, 10, 0, 0));
                            btnUpdateAction.setTextColor(0xFFFFFFFF);
                            btnUpdateAction.setEnabled(true);
                        }
                    });
                }

                public void onUpToDate() {
                    ui.post(new Runnable() {
                        public void run() {
                            updateBusy = false;
                            tvUpdateState.setText("已是最新版本（v" + AppConfig.VERSION_NAME + "）");
                            tvUpdateState.setTextColor(C_TEXT);
                            btnUpdateAction.setText("再查一次");
                            btnUpdateAction.setBackground(box(C_CARD2, 10, 0, 0));
                            btnUpdateAction.setTextColor(C_TEXT);
                            btnUpdateAction.setEnabled(true);
                        }
                    });
                }

                public void onError(final String message) {
                    ui.post(new Runnable() {
                        public void run() {
                            updateBusy = false;
                            tvUpdateState.setText(message);
                            tvUpdateState.setTextColor(C_YEL);
                            btnUpdateAction.setText("重试");
                            btnUpdateAction.setBackground(box(C_CARD2, 10, 0, 0));
                            btnUpdateAction.setTextColor(C_TEXT);
                            btnUpdateAction.setEnabled(true);
                        }
                    });
                }
            });
        } catch (Throwable t) {
            updateBusy = false;
            tvUpdateState.setText("更新模块异常：" + t);
            tvUpdateState.setTextColor(C_RED);
            btnUpdateAction.setText("重试");
            btnUpdateAction.setEnabled(true);
        }
    }

    private void onUpdateButton() {
        if (pendingRelease == null) {
            checkUpdate(false);
            return;
        }
        startInstall();
    }

    private void startInstall() {
        try {
            if (pendingRelease == null || !pendingRelease.hasDownload()) {
                toast("没有可下载的 APK");
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                toast("请先允许本应用安装未知来源应用");
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
            tvUpdateState.setText("正在下载 " + pendingRelease.tagName + " …");
            tvUpdateState.setTextColor(C_BLUE);
            btnUpdateAction.setText("下载中…");
            btnUpdateAction.setEnabled(false);
            long id = updater.downloadAndInstall(this);
            if (id < 0) {
                downloadBySelf();
                return;
            }
            toast("已开始下载，完成后会弹出安装界面");
        } catch (Throwable t) {
            toast("更新失败：" + t);
            btnUpdateAction.setEnabled(true);
            btnUpdateAction.setText("重试");
        }
    }

    private void downloadBySelf() {
        if (pendingRelease == null || !pendingRelease.hasDownload()) {
            return;
        }
        String clean = pendingRelease.tagName == null
                ? "latest" : pendingRelease.tagName.replaceAll("[^0-9A-Za-z._]", "");
        String fileName = AppConfig.APP_NAME + "_" + clean + ".apk";
        ApkInstaller.downloadAsync(this, pendingRelease.downloadUrl, fileName,
                new ApkInstaller.Callback() {
                    public void onProgress(long done, long total) {
                        if (tvUpdateState == null) {
                            return;
                        }
                        if (total > 0) {
                            tvUpdateState.setText("下载中 " + (done * 100 / total) + "%（"
                                    + String.format("%.1f", done / 1048576.0) + " / "
                                    + String.format("%.1f", total / 1048576.0) + " MB）");
                        } else {
                            tvUpdateState.setText("下载中 "
                                    + String.format("%.1f", done / 1048576.0) + " MB");
                        }
                    }

                    public void onDone(File apk) {
                        if (tvUpdateState != null) {
                            tvUpdateState.setText("下载完成，正在调起安装…");
                            tvUpdateState.setTextColor(C_GREEN);
                        }
                        try {
                            ApkInstaller.install(MainActivity.this, apk);
                        } catch (Throwable t) {
                            toast("调起安装失败：" + t);
                        }
                        if (btnUpdateAction != null) {
                            btnUpdateAction.setText("重新检查");
                            btnUpdateAction.setEnabled(true);
                        }
                    }

                    public void onError(String msg) {
                        if (tvUpdateState != null) {
                            tvUpdateState.setText(msg);
                            tvUpdateState.setTextColor(C_RED);
                        }
                        if (btnUpdateAction != null) {
                            btnUpdateAction.setText("重试");
                            btnUpdateAction.setEnabled(true);
                        }
                    }
                });
    }

    // ==================== 页2：5G 扫描 ====================

    private LinearLayout buildScanPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c = card();
        tvScanState = label("还没扫描", C_TEXT, 14, true);
        c.addView(tvScanState);
        TextView note = label("Android 10 起，扫描要求定位权限且系统定位开关打开；否则列表为空。", C_DIM, 12, false);
        note.setPadding(0, dp(6), 0, 0);
        c.addView(note);
        page.addView(c);

        LinearLayout brow = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, WRAP);
        blp.setMargins(0, dp(4), 0, dp(4));
        page.addView(brow, blp);
        TextView b = btn("扫描 5GHz 热点", C_BLUE, 0xFFFFFFFF, new Runnable() {
            public void run() {
                doScan();
            }
        });
        brow.addView(b, new LinearLayout.LayoutParams(MATCH, WRAP));

        scanBox = new LinearLayout(this);
        scanBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(scanBox);

        LinearLayout tip = card();
        tip.addView(section("看什么"));
        String[] tips = {
                "列表里每条的“带宽”是该热点自己在广播里声明的，不是车机协商出来的。",
                "所以：先确认手机热点那条是 80MHz，再看连接页的协商速率，两者一起才能定位是谁的限制。",
                "优先选 149/153/157/161 这类非 DFS 信道，车载移动时不会因雷达检测跳频。"
        };
        for (int i = 0; i < tips.length; i++) {
            TextView l = label(tips[i], C_DIM, 12, false);
            l.setPadding(0, dp(3), 0, dp(3));
            l.setLineSpacing(dp(2), 1f);
            tip.addView(l);
        }
        page.addView(tip);
        return page;
    }

    private void doScan() {
        if (tvScanState == null) {
            return;
        }
        try {
            if (wm == null) {
                tvScanState.setText("系统没有 WIFI_SERVICE，无法扫描");
                return;
            }
            if (!Probe.locationOn(this)) {
                tvScanState.setText("系统定位开关是关闭的，扫描结果会是空的，请先打开定位");
            } else {
                tvScanState.setText("扫描中…（约 2 秒）");
            }
            wm.startScan();
            ui.postDelayed(new Runnable() {
                public void run() {
                    readScans();
                }
            }, 2200);
        } catch (Throwable t) {
            tvScanState.setText("扫描失败：" + t);
        }
    }

    private void readScans() {
        if (scanBox == null) {
            return;
        }
        scanBox.removeAllViews();
        List<ScanResult> rs;
        try {
            if (wm == null) {
                tvScanState.setText("系统没有 WIFI_SERVICE");
                return;
            }
            rs = wm.getScanResults();
        } catch (Throwable t) {
            tvScanState.setText("读不到扫描结果（缺定位权限或定位开关）：" + t);
            return;
        }
        if (rs == null || rs.isEmpty()) {
            tvScanState.setText("没有扫描到任何 AP，先打开定位再试");
            return;
        }

        List<ScanResult> five = new ArrayList<ScanResult>();
        for (int i = 0; i < rs.size(); i++) {
            ScanResult r = rs.get(i);
            if (r != null && r.frequency >= 4900) {
                five.add(r);
            }
        }
        Collections.sort(five, new Comparator<ScanResult>() {
            public int compare(ScanResult a, ScanResult b) {
                return b.level - a.level;
            }
        });

        tvScanState.setText("共 " + rs.size() + " 个 AP，其中 5GHz " + five.size() + " 个");

        String curBssid = (lastProbe == null || lastProbe.bssid == null) ? "" : lastProbe.bssid;
        for (int i = 0; i < five.size(); i++) {
            addScanItem(five.get(i), curBssid);
        }
    }

    private void addScanItem(ScanResult r, String curBssid) {
        LinearLayout c = card();
        c.setPadding(dp(12), dp(9), dp(12), dp(9));

        String ssid = (r.SSID == null || r.SSID.length() == 0) ? "(隐藏 SSID)" : r.SSID;
        boolean current = curBssid.length() > 0 && r.BSSID != null && r.BSSID.equalsIgnoreCase(curBssid);
        if (current) {
            ssid = "● " + ssid + "（当前连接）";
        }
        TextView name = label(ssid, current ? C_GREEN : C_TEXT, 14, true);
        c.addView(name);

        int w = Probe.widthOf(r);
        int ch = Probe.channelOf(r.frequency);
        String std = "";
        String cap = r.capabilities == null ? "" : r.capabilities;
        if (cap.indexOf("VHT") >= 0) {
            std = "11ac";
        } else if (cap.indexOf("HT") >= 0) {
            std = "11n";
        }
        String info = "信道 " + ch + " · " + Probe.widthName(w) + " · " + r.frequency + "MHz · "
                + r.level + "dBm" + (std.length() > 0 ? " · " + std : "");
        TextView sub = label(info, C_DIM, 12, false);
        sub.setPadding(0, dp(3), 0, 0);
        c.addView(sub);

        TextView bss = label(r.BSSID == null ? "" : r.BSSID, 0xFF5B6C86, 11, false);
        bss.setPadding(0, dp(1), 0, 0);
        c.addView(bss);

        scanBox.addView(c);
    }

    // ==================== 页3：P2P 直连 ====================

    private LinearLayout buildP2pPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c = card();
        c.addView(section("Wi-Fi Direct 状态"));
        tvP2pState = label("读取中…", C_TEXT, 13, false);
        tvP2pState.setLineSpacing(dp(2), 1f);
        c.addView(tvP2pState);
        p2pBox = new LinearLayout(this);
        p2pBox.setOrientation(LinearLayout.VERTICAL);
        p2pBox.setPadding(0, dp(6), 0, 0);
        c.addView(p2pBox);
        page.addView(c);

        LinearLayout brow = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, WRAP);
        blp.setMargins(0, dp(4), 0, dp(4));
        page.addView(brow, blp);
        TextView b = btn("刷新 P2P 状态", C_BLUE, 0xFFFFFFFF, new Runnable() {
            public void run() {
                refreshP2p();
            }
        });
        brow.addView(b, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout tip = card();
        tip.addView(section("说明"));
        String[] tips = {
                "CarWith / ICCOA 投屏走的是 Wi-Fi Direct 直连，不是普通热点，所以连接页可能显示“没连 Wi-Fi”。",
                "P2P 组建立后，这里会显示工作信道和频率，可以看出投屏实际占的是哪个频段。",
                "要验证带宽，请两家设备都装本 App，在投屏连接状态下到「吞吐实测」页跑一遍。"
        };
        for (int i = 0; i < tips.length; i++) {
            TextView l = label(tips[i], C_DIM, 12, false);
            l.setPadding(0, dp(3), 0, dp(3));
            l.setLineSpacing(dp(2), 1f);
            tip.addView(l);
        }
        page.addView(tip);
        return page;
    }

    private void refreshP2p() {
        if (tvP2pState == null) {
            return;
        }
        tvP2pState.setText("读取中…");
        if (p2pBox != null) {
            p2pBox.removeAllViews();
        }
        P2pProbe.read(this, new P2pProbe.Callback() {
            public void onResult(P2pProbe r) {
                if (r == null) {
                    tvP2pState.setText("读取失败");
                    return;
                }
                if (!r.supported) {
                    tvP2pState.setText(r.message);
                    return;
                }
                if (!r.connected) {
                    tvP2pState.setText(r.message);
                    return;
                }
                tvP2pState.setText("已建立 P2P 直连组");
                addRow(p2pBox, "组名", r.groupName == null || r.groupName.length() == 0 ? "(无)" : r.groupName);
                addRow(p2pBox, "本机角色", r.isOwner ? "组主 (GO)" : "客户端 (Client)",
                        r.isOwner ? C_YEL : C_TEXT);
                addRow(p2pBox, "工作频率", r.freq > 0 ? r.freq + " MHz" : "未知");
                addRow(p2pBox, "信道", r.channel > 0 ? String.valueOf(r.channel) : "未知");
                addRow(p2pBox, "所在频段", r.freq > 0 ? Probe.bandName(r.freq) : "未知");
                addRow(p2pBox, "DFS", r.freq > 0 && Probe.isDfs(r.freq) ? "是（易跳频）" : "否",
                        r.freq > 0 && Probe.isDfs(r.freq) ? C_YEL : C_TEXT);
                addRow(p2pBox, "客户端数", String.valueOf(r.clients));
                if (r.ifName != null && r.ifName.length() > 0) {
                    addRow(p2pBox, "接口", r.ifName);
                }
                if (r.ownerAddr != null && r.ownerAddr.length() > 0) {
                    addRow(p2pBox, "组主地址", r.ownerAddr);
                }
            }
        });
    }

    // ==================== 页4：吞吐实测 ====================

    private LinearLayout buildSpeedPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c = card();
        c.addView(section("角色"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        c.addView(row);

        btnRoleServer = btn("本机做服务端", C_BLUE, 0xFFFFFFFF, new Runnable() {
            public void run() {
                speedIsServer = true;
                updateSpeedUi();
            }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp1.setMargins(0, 0, dp(5), 0);
        row.addView(btnRoleServer, lp1);

        btnRoleClient = btn("本机做客户端", C_CARD2, C_TEXT, new Runnable() {
            public void run() {
                speedIsServer = false;
                updateSpeedUi();
            }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp2.setMargins(dp(5), 0, 0, 0);
        row.addView(btnRoleClient, lp2);

        tvServerIp = label("", C_DIM, 12, false);
        tvServerIp.setPadding(0, dp(4), 0, 0);
        c.addView(tvServerIp);

        etHost = new EditText(this);
        etHost.setHint("对端 IP，例如 192.168.49.1");
        etHost.setTextColor(C_TEXT);
        etHost.setHintTextColor(C_DIM);
        etHost.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etHost.setInputType(InputType.TYPE_CLASS_TEXT);
        etHost.setBackground(box(C_CARD2, 8, C_LINE, 1));
        etHost.setPadding(dp(8), dp(6), dp(8), dp(6));
        etHost.setSingleLine(true);
        c.addView(etHost, new LinearLayout.LayoutParams(MATCH, WRAP));
        page.addView(c);

        LinearLayout c2 = card();
        c2.addView(section("方向"));
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(4), 0, dp(4));
        c2.addView(row2);

        btnDirUp = btn("上行（客户端→服务端）", C_BLUE, 0xFFFFFFFF, new Runnable() {
            public void run() {
                speedUplink = true;
                updateSpeedUi();
            }
        });
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp3.setMargins(0, 0, dp(5), 0);
        row2.addView(btnDirUp, lp3);

        btnDirDown = btn("下行（服务端→客户端）", C_CARD2, C_TEXT, new Runnable() {
            public void run() {
                speedUplink = false;
                updateSpeedUi();
            }
        });
        LinearLayout.LayoutParams lp4 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp4.setMargins(dp(5), 0, 0, 0);
        row2.addView(btnDirDown, lp4);
        page.addView(c2);

        LinearLayout c3 = card();
        tvSpeedNow = label("-- Mbps", C_TEXT, 24, true);
        c3.addView(tvSpeedNow);
        tvSpeedState = label("未开始", C_DIM, 13, false);
        tvSpeedState.setPadding(0, dp(4), 0, 0);
        c3.addView(tvSpeedState);
        tvSpeedDetail = label("", C_DIM, 12, false);
        tvSpeedDetail.setPadding(0, dp(2), 0, 0);
        c3.addView(tvSpeedDetail);
        page.addView(c3);

        btnSpeedStart = btn("开始测试", C_BLUE, 0xFFFFFFFF, new Runnable() {
            public void run() {
                toggleSpeedTest();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(MATCH, WRAP);
        slp.setMargins(0, dp(4), 0, dp(4));
        page.addView(btnSpeedStart, slp);

        LinearLayout tip = card();
        tip.addView(section("怎么用"));
        String[] tips = {
                "两台设备（手机 + 车机）都装这个 APK，先连到同一张 Wi-Fi（普通热点或 Wi-Fi Direct 都行）。",
                "车机选「本机做服务端」，看它显示的 IP；手机选「本机做客户端」，把那个 IP 填进来。",
                "跑 8 秒取平均值：60Mbps 以上 → 80MHz 链路可用；20~45Mbps → 基本是 40MHz 水平。"
        };
        for (int i = 0; i < tips.length; i++) {
            TextView l = label(tips[i], C_DIM, 12, false);
            l.setPadding(0, dp(3), 0, dp(3));
            l.setLineSpacing(dp(2), 1f);
            tip.addView(l);
        }
        page.addView(tip);

        updateSpeedUi();
        return page;
    }

    private void updateSpeedUi() {
        if (btnRoleServer == null) {
            return;
        }
        btnRoleServer.setBackground(box(speedIsServer ? C_BLUE : C_CARD2, 10, 0, 0));
        btnRoleServer.setTextColor(speedIsServer ? 0xFFFFFFFF : C_TEXT);
        btnRoleClient.setBackground(box(speedIsServer ? C_CARD2 : C_BLUE, 10, 0, 0));
        btnRoleClient.setTextColor(speedIsServer ? C_TEXT : 0xFFFFFFFF);

        btnDirUp.setBackground(box(speedUplink ? C_BLUE : C_CARD2, 10, 0, 0));
        btnDirUp.setTextColor(speedUplink ? 0xFFFFFFFF : C_TEXT);
        btnDirDown.setBackground(box(speedUplink ? C_CARD2 : C_BLUE, 10, 0, 0));
        btnDirDown.setTextColor(speedUplink ? C_TEXT : 0xFFFFFFFF);

        if (speedIsServer) {
            etHost.setVisibility(View.GONE);
            tvServerIp.setVisibility(View.VISIBLE);
            tvServerIp.setText("本机 IP：" + Probe.localIp() + "   端口：" + SpeedTest.PORT);
        } else {
            etHost.setVisibility(View.VISIBLE);
            tvServerIp.setVisibility(View.GONE);
        }
    }

    private final SpeedTest.Listener speedListener = new SpeedTest.Listener() {
        public void onState(String msg) {
            if (tvSpeedState != null) {
                tvSpeedState.setText(msg);
            }
        }

        public void onLive(double mbps, double elapsed) {
            if (tvSpeedNow != null) {
                tvSpeedNow.setText(String.format("%.1f Mbps", mbps));
            }
            if (tvSpeedDetail != null) {
                tvSpeedDetail.setText(String.format("已跑 %.1f 秒", elapsed));
            }
        }

        public void onDone(double mbps, long bytes, double elapsed) {
            if (tvSpeedNow != null) {
                tvSpeedNow.setText(String.format("%.1f Mbps", mbps));
                tvSpeedNow.setTextColor(mbps >= 60 ? C_GREEN : (mbps >= 20 ? C_YEL : C_RED));
            }
            if (tvSpeedDetail != null) {
                tvSpeedDetail.setText(String.format("完成：%.1f 秒，传输 %.1f MB，平均 %.1f Mbps",
                        elapsed, bytes / 1048576.0, mbps));
            }
            if (btnSpeedStart != null) {
                btnSpeedStart.setText("开始测试");
            }
        }

        public void onError(String msg) {
            if (tvSpeedState != null) {
                tvSpeedState.setText("出错：" + msg);
            }
            if (tvSpeedNow != null) {
                tvSpeedNow.setText("-- Mbps");
                tvSpeedNow.setTextColor(C_TEXT);
            }
            if (btnSpeedStart != null) {
                btnSpeedStart.setText("开始测试");
            }
        }
    };

    private void toggleSpeedTest() {
        if (speedTest.isRunning()) {
            speedTest.stop();
            if (tvSpeedState != null) {
                tvSpeedState.setText("已手动停止");
            }
            if (btnSpeedStart != null) {
                btnSpeedStart.setText("开始测试");
            }
            return;
        }
        if (tvSpeedNow != null) {
            tvSpeedNow.setTextColor(C_TEXT);
            tvSpeedNow.setText("-- Mbps");
        }
        if (tvSpeedDetail != null) {
            tvSpeedDetail.setText("");
        }
        if (speedIsServer) {
            speedTest.startServer(speedUplink, speedListener);
        } else {
            String host = etHost == null ? "" : etHost.getText().toString().trim();
            if (host.length() == 0) {
                toast("先填对端 IP");
                return;
            }
            speedTest.startClient(host, speedUplink, speedListener);
        }
        if (btnSpeedStart != null) {
            btnSpeedStart.setText("停止");
        }
    }

    // ==================== 权限 ====================

    private void requestPerms() {
        try {
            if (Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean ok = grantResults != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            toast(ok ? "定位权限已授予" : "没有定位权限，SSID 与扫描会不可用");
            refreshConnection();
        }
    }
}

package com.fyt.wificap;

import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

/**
 * 采集当前 Wi-Fi 连接信息，尽量兼容 Android 7 ~ 14。
 * 关键点：ScanResult.channelWidth 是公开字段（API 23+），可用反射安全读取；
 * 协商速率只能来自 WifiInfo.getLinkSpeed()，这是判断"本机实际跑多宽"的唯一可靠口径。
 */
public class Probe {

    public String ssid = "未获取";
    public String bssid = "";
    public String ip = "未知";
    public String model = "";
    public String system = "";
    public String bandName = "未知";
    public String widthName = "未扫到";
    public String standardName = "未知";
    public String apCenter = "";

    public int freq = 0;
    public int channel = -1;
    public int rssi = 0;
    public int linkSpeed = 0;
    public int maxTx = 0;
    public int maxRx = 0;
    public int apWidth = -1;
    public int apCenter0 = 0;
    public int apCenter1 = 0;
    public int standard = -1;
    public int scanCount = 0;

    public boolean dfs = false;
    public boolean scanOk = false;
    public boolean locationOn = true;
    public boolean connected = false;

    public static Probe read(Context ctx) {
        Probe p = new Probe();
        p.model = Build.MANUFACTURER + " " + Build.MODEL;
        p.system = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        p.ip = localIp();
        p.locationOn = locationOn(ctx);

        WifiManager wm = null;
        try {
            wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        } catch (Throwable t) {
            p.ssid = "系统无 WIFI_SERVICE";
            return p;
        }
        if (wm == null) {
            p.ssid = "系统无 WIFI_SERVICE";
            return p;
        }

        try {
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null) {
                String s = wi.getSSID();
                if (s != null) p.ssid = s.replace("\"", "").trim();
                String b = wi.getBSSID();
                if (b != null) p.bssid = b;
                p.freq = wi.getFrequency();
                p.rssi = wi.getRssi();
                try {
                    p.linkSpeed = wi.getLinkSpeed();
                } catch (Throwable t) {
                    p.linkSpeed = 0;
                }
                p.channel = channelOf(p.freq);
                p.dfs = isDfs(p.freq);
                p.bandName = bandName(p.freq);
                p.connected = p.freq > 0;

                Object v = call(wi, "getMaxSupportedTxLinkSpeedMbps");
                if (v instanceof Integer) p.maxTx = (Integer) v;
                v = call(wi, "getMaxSupportedRxLinkSpeedMbps");
                if (v instanceof Integer) p.maxRx = (Integer) v;
                v = call(wi, "getWifiStandard");
                if (v instanceof Integer) p.standard = (Integer) v;
            }
        } catch (Throwable t) {
            // 忽略，继续尝试扫描
        }

        try {
            List<ScanResult> rs = wm.getScanResults();
            if (rs != null) {
                p.scanOk = true;
                p.scanCount = rs.size();
                for (ScanResult r : rs) {
                    if (r == null) continue;
                    if (r.BSSID != null && p.bssid.length() > 0 && r.BSSID.equalsIgnoreCase(p.bssid)) {
                        p.apWidth = widthOf(r);
                        p.apCenter0 = centerOf(r, "centerFreq0");
                        p.apCenter1 = centerOf(r, "centerFreq1");
                        Object v = call(r, "getWifiStandard");
                        if (v instanceof Integer) p.standard = (Integer) v;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            p.scanOk = false;
        }

        p.widthName = p.apWidth >= 0 ? widthName(p.apWidth) : "未扫到";
        if (p.apCenter0 > 0) p.apCenter = p.apCenter0 + " MHz";
        p.standardName = standardName(p.standard);
        return p;
    }

    // ---------- 基础换算 ----------

    public static int channelOf(int f) {
        if (f == 2484) return 14;
        if (f >= 2400 && f < 2500) return (f - 2407) / 5;
        if (f >= 4900 && f < 5925) return (f - 5000) / 5;
        if (f >= 5925 && f < 7125) return (f - 5950) / 5;
        return -1;
    }

    public static boolean isDfs(int f) {
        return (f >= 5250 && f < 5350) || (f >= 5470 && f < 5725);
    }

    public static String bandName(int f) {
        if (f <= 0) return "未知";
        if (f >= 2400 && f < 2500) return "2.4GHz";
        if (f >= 4900 && f < 5250) return "5GHz U-NII-1 (36-48)";
        if (f >= 5250 && f < 5350) return "5GHz U-NII-2A (DFS 52-64)";
        if (f >= 5470 && f < 5725) return "5GHz U-NII-2C (DFS 100-144)";
        if (f >= 5725 && f < 5850) return "5GHz U-NII-3 (149-165 非DFS)";
        if (f >= 5925) return "6GHz";
        return "其他 " + f + "MHz";
    }

    public static String widthName(int w) {
        switch (w) {
            case 0: return "20MHz";
            case 1: return "40MHz";
            case 2: return "80MHz";
            case 3: return "160MHz";
            case 4: return "80+80MHz";
            case 5: return "2160MHz";
            default: return "未知";
        }
    }

    public static String standardName(int s) {
        switch (s) {
            case 0: return "未知";
            case 1: return "802.11 legacy";
            case 4: return "Wi-Fi 4 (11n)";
            case 5: return "Wi-Fi 5 (11ac)";
            case 6: return "Wi-Fi 6 (11ax)";
            case 7: return "802.11ad";
            case 8: return "Wi-Fi 7 (11be)";
            default: return "未知(" + s + ")";
        }
    }

    // ---------- 反射取值 ----------

    public static int widthOf(ScanResult r) {
        try {
            Field f = ScanResult.class.getField("channelWidth");
            return f.getInt(r);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static int centerOf(ScanResult r, String name) {
        try {
            Field f = ScanResult.class.getField(name);
            return f.getInt(r);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static Object call(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- 环境 ----------

    public static boolean locationOn(Context ctx) {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String localIp() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (ni == null || ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String host = a.getHostAddress();
                        String n = ni.getName() == null ? "" : ni.getName();
                        if (n.startsWith("wlan") || n.startsWith("p2p")) return host;
                        if (fallback == null) fallback = host;
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return fallback == null ? "未知" : fallback;
    }
}

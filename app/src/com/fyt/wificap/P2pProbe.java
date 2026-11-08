package com.fyt.wificap;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;

/**
 * 读取当前 Wi-Fi Direct（P2P）组的真实工作信道。
 * CarWith / ICCOA 这类投屏走的就是 P2P 直连，这条信息比普通热点更有参考价值：
 * 它说明"车机与手机在直连状态下工作在哪个信道"，配合吞吐实测可以看出实际带宽。
 */
public class P2pProbe {

    public boolean supported = false;
    public boolean connected = false;
    public boolean isOwner = false;
    public int freq = 0;
    public int channel = -1;
    public int clients = 0;
    public String groupName = "";
    public String ifName = "";
    public String ownerAddr = "";
    public String message = "";

    public interface Callback {
        void onResult(P2pProbe result);
    }

    public static void read(Context ctx, final Callback cb) {
        final P2pProbe r = new P2pProbe();
        try {
            final WifiP2pManager m = (WifiP2pManager) ctx.getApplicationContext()
                    .getSystemService(Context.WIFI_P2P_SERVICE);
            if (m == null) {
                r.message = "该系统版本没有 Wi-Fi Direct 服务";
                cb.onResult(r);
                return;
            }
            r.supported = true;
            WifiP2pManager.Channel ch = m.initialize(ctx.getApplicationContext(),
                    ctx.getMainLooper(), new WifiP2pManager.ChannelListener() {
                        public void onChannelDisconnected() {
                        }
                    });
            if (ch == null) {
                r.message = "无法初始化 P2P 通道（可能系统禁用了 Wi-Fi Direct）";
                cb.onResult(r);
                return;
            }
            m.requestGroupInfo(ch, new WifiP2pManager.GroupInfoListener() {
                public void onGroupInfoAvailable(WifiP2pGroup g) {
                    try {
                        if (g == null) {
                            r.connected = false;
                            r.message = "当前没有 P2P 直连组（未投屏，或系统隐藏了该接口）";
                        } else {
                            r.connected = true;
                            r.groupName = g.getNetworkName();
                            r.isOwner = g.isGroupOwner();
                            r.clients = (g.getClientList() == null) ? 0 : g.getClientList().size();
                            try {
                                r.freq = g.getFrequency();
                            } catch (Throwable t) {
                                Object v = Probe.call(g, "getFrequency");
                                if (v instanceof Integer) r.freq = (Integer) v;
                            }
                            try {
                                r.ifName = g.getInterface();
                            } catch (Throwable t) {
                                r.ifName = "";
                            }
                            WifiP2pDevice owner = g.getOwner();
                            if (owner != null && owner.deviceAddress != null) {
                                r.ownerAddr = owner.deviceAddress;
                            }
                            r.channel = Probe.channelOf(r.freq);
                            r.message = "已读取 P2P 组信息";
                        }
                    } catch (Throwable t) {
                        r.message = "读取 P2P 组信息异常：" + t;
                    }
                    cb.onResult(r);
                }
            });
        } catch (Throwable t) {
            r.message = "调用 Wi-Fi Direct 失败：" + t;
            cb.onResult(r);
        }
    }
}

package com.fyt.wificap;

import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 极简 TCP 吞吐实测：一端做服务端，另一端做客户端，跑 8 秒算平均 Mbps。
 * 两台设备装同一个 APK，在同一张网里（普通热点或 Wi-Fi Direct 都行）。
 * 这是唯一不会被"厂商上报参数"骗到的验证手段。
 */
public class SpeedTest {

    public static final int PORT = 8899;
    private static final int DURATION_MS = 8000;
    private static final int BUF_SIZE = 256 * 1024;

    public interface Listener {
        void onState(String msg);
        void onLive(double mbps, double elapsed);
        void onDone(double mbps, long bytes, double elapsed);
        void onError(String msg);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private ServerSocket server;
    private Socket socket;

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Throwable t) {
            // ignore
        }
        try {
            if (server != null) server.close();
        } catch (Throwable t) {
            // ignore
        }
        socket = null;
        server = null;
    }

    public void startServer(final boolean uplink, final Listener l) {
        stop();
        running = true;
        Thread th = new Thread(new Runnable() {
            public void run() {
                try {
                    ServerSocket ss = new ServerSocket();
                    ss.setReuseAddress(true);
                    ss.bind(new InetSocketAddress(PORT));
                    server = ss;
                    postState(l, "服务端已就绪（端口 " + PORT + "），等待另一端连接…");
                    while (running) {
                        Socket s = ss.accept();
                        socket = s;
                        try {
                            s.setSoTimeout(9000);
                            InputStream in = s.getInputStream();
                            int cmd = in.read();
                            boolean clientWrites = (cmd == 'U');
                            runLoop(s, !clientWrites, l);
                        } catch (Throwable t) {
                            if (running) postError(l, "本次测试中断：" + t);
                        } finally {
                            try {
                                s.close();
                            } catch (Throwable t2) {
                                // ignore
                            }
                            socket = null;
                        }
                        if (running) postState(l, "测试完成，继续等待下一次连接…");
                    }
                } catch (Throwable t) {
                    if (running) postError(l, "服务端启动失败：" + t);
                } finally {
                    postState(l, "服务端已停止");
                    running = false;
                }
            }
        }, "speedtest-server");
        th.start();
    }

    public void startClient(final String host, final boolean uplink, final Listener l) {
        stop();
        running = true;
        Thread th = new Thread(new Runnable() {
            public void run() {
                Socket s = null;
                try {
                    postState(l, "正在连接 " + host + ":" + PORT + " …");
                    s = new Socket();
                    s.connect(new InetSocketAddress(host, PORT), 4000);
                    socket = s;
                    s.setSoTimeout(9000);
                    OutputStream out = s.getOutputStream();
                    out.write(uplink ? 'U' : 'D');
                    out.flush();
                    runLoop(s, uplink, l);
                } catch (Throwable t) {
                    if (running) postError(l, "连接失败：" + t);
                } finally {
                    try {
                        if (s != null) s.close();
                    } catch (Throwable t2) {
                        // ignore
                    }
                    socket = null;
                    running = false;
                }
            }
        }, "speedtest-client");
        th.start();
    }

    private void runLoop(Socket s, boolean writer, Listener l) throws Exception {
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        byte[] buf = new byte[BUF_SIZE];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) (i & 0xFF);
        }
        long start = System.nanoTime();
        long total = 0;
        long last = start;
        postState(l, "测试中（" + (DURATION_MS / 1000) + " 秒）…");

        while (running) {
            long now = System.nanoTime();
            if ((now - start) / 1000000L >= DURATION_MS) break;
            if (writer) {
                out.write(buf);
                total += buf.length;
            } else {
                int n;
                try {
                    n = in.read(buf);
                } catch (SocketTimeoutException ste) {
                    if ((System.nanoTime() - start) / 1000000L >= DURATION_MS) break;
                    continue;
                }
                if (n < 0) break;
                total += n;
            }
            now = System.nanoTime();
            if (now - last > 400000000L) {
                double sec = (now - start) / 1e9;
                postLive(l, sec > 0 ? (total * 8.0 / sec / 1e6) : 0, sec);
                last = now;
            }
        }

        double sec = (System.nanoTime() - start) / 1e9;
        if (sec <= 0) sec = 0.001;
        double mbps = total * 8.0 / sec / 1e6;
        postDone(l, mbps, total, sec);
        running = false;
    }

    private void postState(final Listener l, final String msg) {
        ui.post(new Runnable() {
            public void run() {
                if (l != null) l.onState(msg);
            }
        });
    }

    private void postLive(final Listener l, final double mbps, final double sec) {
        ui.post(new Runnable() {
            public void run() {
                if (l != null) l.onLive(mbps, sec);
            }
        });
    }

    private void postDone(final Listener l, final double mbps, final long bytes, final double sec) {
        ui.post(new Runnable() {
            public void run() {
                if (l != null) l.onDone(mbps, bytes, sec);
            }
        });
    }

    private void postError(final Listener l, final String msg) {
        ui.post(new Runnable() {
            public void run() {
                if (l != null) l.onError(msg);
            }
        });
    }
}

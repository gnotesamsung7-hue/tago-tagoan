package com.markdave.tagotagoan;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.webkit.WebView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Finds games on the same Wi-Fi: the host shouts its name every second over UDP, other phones listen and list them. */
public class LanBridge {
    static final int DISC_PORT = 47731;
    static final String TAG = "TAGOTAGOAN|";

    private final Context ctx;
    private final WebView web;
    private volatile boolean broadcasting, listening;
    private Thread bThread, lThread;
    private DatagramSocket listenSocket;
    private WifiManager.MulticastLock lock;
    private final Map<String, String> names = new ConcurrentHashMap<>();
    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    LanBridge(Context ctx, WebView web) {
        this.ctx = ctx.getApplicationContext();
        this.web = web;
    }

    synchronized void startBroadcast(final String name) {
        stopBroadcast();
        broadcasting = true;
        final byte[] data = (TAG + name).getBytes(StandardCharsets.UTF_8);
        bThread = new Thread(() -> {
            DatagramSocket s = null;
            try {
                s = new DatagramSocket();
                s.setBroadcast(true);
                while (broadcasting) {
                    for (InetAddress a : broadcastAddresses()) {
                        try { s.send(new DatagramPacket(data, data.length, a, DISC_PORT)); } catch (Exception ignored) { }
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {
            } finally {
                if (s != null) s.close();
            }
        }, "tago-broadcast");
        bThread.setDaemon(true);
        bThread.start();
    }

    synchronized void stopBroadcast() {
        broadcasting = false;
        if (bThread != null) bThread.interrupt();
        bThread = null;
    }

    synchronized void startListen() {
        if (listening) return;
        listening = true;
        names.clear();
        seen.clear();
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("tago-discovery");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch (Exception ignored) { }
        lThread = new Thread(() -> {
            long lastPush = 0;
            try {
                DatagramSocket s = new DatagramSocket(null);
                s.setReuseAddress(true);
                s.setBroadcast(true);
                s.bind(new InetSocketAddress(DISC_PORT));
                s.setSoTimeout(800);
                listenSocket = s;
                byte[] buf = new byte[512];
                while (listening) {
                    try {
                        DatagramPacket pk = new DatagramPacket(buf, buf.length);
                        s.receive(pk);
                        String msg = new String(pk.getData(), 0, pk.getLength(), StandardCharsets.UTF_8);
                        if (msg.startsWith(TAG) && pk.getAddress() != null) {
                            String ip = pk.getAddress().getHostAddress();
                            names.put(ip, msg.substring(TAG.length()));
                            seen.put(ip, System.currentTimeMillis());
                        }
                    } catch (SocketTimeoutException ignored) { }
                    long now = System.currentTimeMillis();
                    if (now - lastPush > 1000) { lastPush = now; push(now); }
                }
            } catch (Exception ignored) {
            } finally {
                DatagramSocket s = listenSocket;
                listenSocket = null;
                if (s != null) s.close();
            }
        }, "tago-listen");
        lThread.setDaemon(true);
        lThread.start();
    }

    synchronized void stopListen() {
        listening = false;
        DatagramSocket s = listenSocket;
        if (s != null) s.close();
        if (lThread != null) lThread.interrupt();
        lThread = null;
        try { if (lock != null && lock.isHeld()) lock.release(); } catch (Exception ignored) { }
        lock = null;
    }

    private void push(long now) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() > 4000) { names.remove(e.getKey()); it.remove(); continue; }
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"ip\":\"").append(esc(e.getKey())).append("\",\"name\":\"").append(esc(names.get(e.getKey()))).append("\"}");
        }
        sb.append(']');
        final String js = "window.lanRooms&&window.lanRooms(" + sb + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20 || c == '<' || c == '>') b.append(' ');
            else b.append(c);
        }
        return b.toString();
    }

    private static List<InetAddress> broadcastAddresses() {
        List<InetAddress> out = new ArrayList<>();
        try { out.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) { }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null && !out.contains(b)) out.add(b);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String localIp() {
        String fallback = "", any = "";
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName() == null ? "" : ni.getName();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("softap")) return ip;
                    if (a.isSiteLocalAddress() && fallback.isEmpty()) fallback = ip;
                    if (any.isEmpty()) any = ip;
                }
            }
        } catch (Exception ignored) { }
        return fallback.isEmpty() ? any : fallback;
    }
}

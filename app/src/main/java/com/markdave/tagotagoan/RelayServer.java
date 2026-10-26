package com.markdave.tagotagoan;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/** Tiny Wi-Fi relay: every phone connects here; each message is forwarded to everyone else, tagged with the sender's id. */
public class RelayServer extends WebSocketServer {
    private final AtomicInteger next = new AtomicInteger(1);

    public RelayServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
        setConnectionLostTimeout(15);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        int id = next.getAndIncrement();
        conn.setAttachment(id);
        conn.send("{\"sys\":\"welcome\",\"id\":" + id + "}");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Integer id = conn.getAttachment();
        if (id != null) sendToOthers(null, "{\"sys\":\"left\",\"id\":" + id + "}");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Integer id = conn.getAttachment();
        if (id == null || message == null || message.isEmpty() || message.length() > 400000) return;
        char c = message.charAt(0);
        if (c != '{') return;
        sendToOthers(conn, "{\"from\":" + id + ",\"m\":" + message + "}");
    }

    private void sendToOthers(WebSocket except, String text) {
        for (WebSocket c : getConnections()) {
            if (c != except && c.isOpen()) {
                try { c.send(text); } catch (Exception ignored) { }
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) { }

    @Override
    public void onStart() { }
}

package org.stickbadminton.gamecomponent.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 行文本协议服务器，匹配 NetworkClient:
 * - 客户端可发送：HELLO:<p1|p2>（可选），ACTION:<playerId>:<COMMAND>，KEY:PRESS/RELEASE:<KEYCODE>，PING
 * - 服务器会：分配 ASSIGN:<p1|p2|watcherN>，并将收到的 ACTION/KEY 转发给其他客户端
 *
 * 补充：
 * - 当 p1/p2 的占用状态变化时，广播 INFO:PLAYER_STATE:<p1|p2>:READY|WAITING
 * - 新客户端分配后，推送当前 p1/p2 状态快照
 */
public class GameServer {

    private final int port;
    private volatile boolean running = false;

    private ServerSocket serverSocket;

    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private volatile ClientHandler p1Holder = null;
    private volatile ClientHandler p2Holder = null;

    private final AtomicInteger watcherSeq = new AtomicInteger(1);

    public GameServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        serverSocket = new ServerSocket(port);
        System.out.println("[Server] listening on " + port);

        new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    new Thread(handler, "Client-" + socket.getRemoteSocketAddress()).start();
                } catch (IOException e) {
                    if (running) {
                        System.out.println("[Server] accept error: " + e.getMessage());
                    }
                }
            }
        }, "Acceptor").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        for (ClientHandler c : clients) {
            c.close();
        }
        clients.clear();
        p1Holder = null;
        p2Holder = null;
        System.out.println("[Server] stopped");
    }

    private void broadcast(String line) {
        for (ClientHandler c : clients) {
            c.send(line);
        }
    }

    private void sendPresenceSnapshot(ClientHandler target) {
        target.send("INFO:PLAYER_STATE:p1:" + (p1Holder != null ? "READY" : "WAITING"));
        target.send("INFO:PLAYER_STATE:p2:" + (p2Holder != null ? "READY" : "WAITING"));
    }

    private synchronized String assignSlot(ClientHandler handler, String desired) {
        if (handler.assignedId != null) {
            return handler.assignedId;
        }

        String request = desired != null ? desired.trim().toLowerCase() : "";

        if ("p1".equals(request) && p1Holder == null) {
            p1Holder = handler;
            handler.assignedId = "p1";
        } else if ("p2".equals(request) && p2Holder == null) {
            p2Holder = handler;
            handler.assignedId = "p2";
        } else {
            if (p1Holder == null) {
                p1Holder = handler;
                handler.assignedId = "p1";
            } else if (p2Holder == null) {
                p2Holder = handler;
                handler.assignedId = "p2";
            } else {
                handler.assignedId = "watcher" + watcherSeq.getAndIncrement();
            }
        }

        System.out.println("[Server] " + handler.remote() + " assigned as " + handler.assignedId +
                (desired != null ? " (desired=" + desired + ")" : ""));

        // 给新客户端推送当前快照
        sendPresenceSnapshot(handler);

        // p1/p2 占位 -> 广播 READY
        if ("p1".equals(handler.assignedId) || "p2".equals(handler.assignedId)) {
            broadcast("INFO:PLAYER_STATE:" + handler.assignedId + ":READY");
        }

        return handler.assignedId;
    }

    private synchronized void releaseSlot(ClientHandler handler) {
        if (Objects.equals(p1Holder, handler)) {
            p1Holder = null;
            System.out.println("[Server] release p1");
        } else if (Objects.equals(p2Holder, handler)) {
            p2Holder = null;
            System.out.println("[Server] release p2");
        }
    }

    private void relayToOthers(ClientHandler from, String line) {
        for (ClientHandler c : clients) {
            if (c != from) {
                c.send(line);
            }
        }
    }

    private final class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        private volatile boolean alive = true;

        private volatile String assignedId = null;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        String remote() {
            return String.valueOf(socket.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                System.out.println("[Server] connected: " + remote());

                String line;
                while (alive && (line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    System.out.println("[Server] RX from " + remote() + " -> " + line);

                    if (line.startsWith("HELLO:")) {
                        String desired = line.substring("HELLO:".length()).trim();
                        String id = assignSlot(this, desired);
                        send("ASSIGN:" + id);
                        continue;
                    }

                    if (assignedId == null) {
                        assignSlot(this, null);
                        send("ASSIGN:" + assignedId);
                    }

                    if ("PING".equalsIgnoreCase(line)) {
                        send("PONG");
                        continue;
                    }

                    if (line.startsWith("KEY:")) {
                        relayToOthers(this, line);
                        continue;
                    }

                    if (line.startsWith("ACTION:")) {
                        int firstColon = line.indexOf(':');
                        int secondColon = line.indexOf(':', firstColon + 1);
                        if (firstColon > 0 && secondColon > firstColon) {
                            String command = line.substring(secondColon + 1).trim();
                            relayToOthers(this, "ACTION:" + assignedId + ":" + command);
                        } else {
                            send("ERROR:BAD_ACTION_FORMAT");
                        }
                        continue;
                    }

                    send("ERROR:UNKNOWN_CMD " + line);
                }
            } catch (IOException e) {
                System.out.println("[Server] IO error from " + remote() + ": " + e.getMessage());
            } finally {
                close();
            }
        }

        void send(String line) {
            try {
                if (out != null) {
                    out.println(line);
                    out.flush();
                }
            } catch (Exception ignored) {}
        }

        void close() {
            if (!alive) return;
            alive = false;
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}

            clients.remove(this);

            String id = assignedId;
            releaseSlot(this);

            if (id != null && (id.equals("p1") || id.equals("p2"))) {
                relayToOthers(this, "INFO:PLAYER_LEFT:" + id);
                broadcast("INFO:PLAYER_STATE:" + id + ":WAITING");
            }

            System.out.println("[Server] disconnected: " + remote());
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8888;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        GameServer server = new GameServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "ShutdownHook"));
        server.start();
    }
}
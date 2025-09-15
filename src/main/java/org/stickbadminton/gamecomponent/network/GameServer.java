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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文本协议服务器：
 * 客户端 -> 服务器：
 * - HELLO:<p1|p2>(可选)
 * - KEY:PRESS/RELEASE:<KEYCODE>
 * - ACTION:<playerId>:<COMMAND>
 * - SELECT:<characterId>
 * - READY:<0|1>
 * - START
 *
 * 服务器 -> 客户端：
 * - ASSIGN:<p1|p2|watcherN>
 * - INFO:PLAYER_STATE:<p1|p2>:READY|WAITING   // 占位/离线状态（存在性）
 * - INFO:PLAYER_LEFT:<p1|p2>
 * - KEY:..., ACTION:...                       // 转发
 * - SELECT:<p1|p2>:<characterId>             // 选择（0 表示清空）
 * - READY:<p1|p2>:<0|1>                       // 就绪状态
 * - START:<ct1>:<ct2>                         // 开始游戏
 */
public class GameServer {

    private final int port;
    private volatile boolean running = false;

    private ServerSocket serverSocket;

    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private volatile ClientHandler p1Holder = null;
    private volatile ClientHandler p2Holder = null;

    private volatile boolean p1Ready = false;
    private volatile boolean p2Ready = false;

    private volatile int p1Select = 0;
    private volatile int p2Select = 0;
    // 长按集合 + 白名单
    private final Set<String> pressedP1 = new HashSet<>();
    private final Set<String> pressedP2 = new HashSet<>();

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
        // 心跳：广播当前按下集合，修复长按不同步
        new Thread(() -> {
            while (running) {
                try {
                    broadcast("KEY_STATE:p1:" + String.join(",", pressedP1));
                    broadcast("KEY_STATE:p2:" + String.join(",", pressedP2));
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        }, "KeyHeartbeat").start();
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
        p1Ready = p2Ready = false;
        p1Select = p2Select = 0;
        pressedP1.clear();
        pressedP2.clear();
        System.out.println("[Server] stopped");
    }

    private void broadcast(String line) {
        for (ClientHandler c : clients) {
            c.send(line);
        }
    }

    private void relayToOthers(ClientHandler from, String line) {
        for (ClientHandler c : clients) {
            if (c != from) c.send(line);
        }
    }

    private void sendPresenceSnapshot(ClientHandler target) {
        target.send("INFO:PLAYER_STATE:p1:" + (p1Holder != null ? "READY" : "WAITING"));
        target.send("INFO:PLAYER_STATE:p2:" + (p2Holder != null ? "READY" : "WAITING"));
        // 同步就绪与选择
        target.send("READY:p1:" + (p1Ready ? "1" : "0"));
        target.send("READY:p2:" + (p2Ready ? "1" : "0"));
        target.send("SELECT:p1:" + p1Select);
        target.send("SELECT:p2:" + p2Select);
        // 初始推送按键状态，避免第一帧错位
        target.send("KEY_STATE:p1:" + String.join(",", pressedP1));
        target.send("KEY_STATE:p2:" + String.join(",", pressedP2));
    }

    private synchronized String assignSlot(ClientHandler handler, String desired) {
        if (handler.assignedId != null) return handler.assignedId;

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

        // 给新客户端推送快照
        sendPresenceSnapshot(handler);

        // 占位广播（存在性）
        if ("p1".equals(handler.assignedId) || "p2".equals(handler.assignedId)) {
            broadcast("INFO:PLAYER_STATE:" + handler.assignedId + ":READY");
            // 初始就绪默认为 0
            if ("p1".equals(handler.assignedId)) {
                p1Ready = false;
                broadcast("READY:p1:0");
            } else if ("p2".equals(handler.assignedId)) {
                p2Ready = false;
                broadcast("READY:p2:0");
            }
        }

        return handler.assignedId;
    }

    private synchronized void releaseSlot(ClientHandler handler) {
        if (Objects.equals(p1Holder, handler)) {
            p1Holder = null;
            p1Ready = false;
            p1Select = 0; pressedP1.clear();
            System.out.println("[Server] release p1");
            broadcast("READY:p1:0");
            broadcast("SELECT:p1:0");
        } else if (Objects.equals(p2Holder, handler)) {
            p2Holder = null;
            p2Ready = false;
            p2Select = 0; pressedP2.clear();
            System.out.println("[Server] release p2");
            broadcast("READY:p2:0");
            broadcast("SELECT:p2:0");
        }
    }

    private synchronized void setReady(String id, boolean ready) {
        if ("p1".equals(id)) {
            p1Ready = ready;
            broadcast("READY:p1:" + (ready ? "1" : "0"));
        } else if ("p2".equals(id)) {
            p2Ready = ready;
            broadcast("READY:p2:" + (ready ? "1" : "0"));
        }
    }

    private synchronized void setSelect(String id, int characterId) {
        if ("p1".equals(id)) {
            p1Select = characterId;
            broadcast("SELECT:p1:" + characterId);
        } else if ("p2".equals(id)) {
            p2Select = characterId;
            broadcast("SELECT:p2:" + characterId);
        }
    }

    private synchronized boolean canStart() {
        return p1Holder != null && p2Holder != null
                && p1Ready && p2Ready
                && p1Select > 0 && p2Select > 0;
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
                        // KEY:PRESS/RELEASE:CODE（客户端只发送自己的物理输入）
                        String payload = line.substring("KEY:".length()).trim();
                        int colonIdx = payload.indexOf(':');
                        if (colonIdx <= 0 || colonIdx >= payload.length() - 1) {
                            send("ERROR:BAD_KEY_FORMAT");
                            continue;
                        }
                        String action = payload.substring(0, colonIdx).trim().toUpperCase();
                        String keyCode = payload.substring(colonIdx + 1).trim().toUpperCase();
                        boolean allowed = ("p1".equals(assignedId) && java.util.Set.of("Q","W","E","A","S","D").contains(keyCode))
                                || ("p2".equals(assignedId) && java.util.Set.of("U","I","O","J","K","L").contains(keyCode));
                        if (!allowed) { send("ERROR:KEY_NOT_ALLOWED"); continue; }
                        if ("PRESS".equals(action)) {
                            if ("p1".equals(assignedId)) pressedP1.add(keyCode); else if ("p2".equals(assignedId)) pressedP2.add(keyCode);
                        } else if ("RELEASE".equals(action)) {
                            if ("p1".equals(assignedId)) pressedP1.remove(keyCode); else if ("p2".equals(assignedId)) pressedP2.remove(keyCode);
                        } else { send("ERROR:BAD_KEY_ACTION"); continue; }
                        broadcast("KEY:" + assignedId + ":" + action + ":" + keyCode);
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

                    if (line.startsWith("SELECT:")) {
                        String s = line.substring("SELECT:".length()).trim();
                        int cid = 0;
                        try { cid = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
                        setSelect(assignedId, cid);
                        continue;
                    }

                    if (line.startsWith("READY:")) {
                        String s = line.substring("READY:".length()).trim();
                        boolean r = "1".equals(s) || "true".equalsIgnoreCase(s);
                        setReady(assignedId, r);
                        continue;
                    }

                    if ("START".equalsIgnoreCase(line)) {
                        if (canStart()) {
                            broadcast("START:" + p1Select + ":" + p2Select);
                        } else {
                            System.out.println("[Server] reject START: "
                                    + "p1Present=" + (p1Holder != null) + ", p2Present=" + (p2Holder != null)
                                    + ", p1Ready=" + p1Ready + ", p2Ready=" + p2Ready
                                    + ", p1Select=" + p1Select + ", p2Select=" + p2Select);
                            send("ERROR:START_CONDITION_NOT_MET");
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

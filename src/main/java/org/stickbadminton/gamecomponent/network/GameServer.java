package org.stickbadminton.gamecomponent.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 行文本协议的多人服务器（与 NetworkClient 匹配）：
 *
 * 协议（每行一条，UTF-8）:
 * - 客户端 -> 服务器：
 *   - HELLO:<playerId>                 请求占用玩家身份（例如 p1 / p2）
 *   - KEY:PRESS:<KEYCODE>              按键按下
 *   - KEY:RELEASE:<KEYCODE>            按键松开
 *   - ACTION:<playerId>:<COMMAND>      动作指令（例如 MOVE_LEFT_ON / JUMP / LIGHT_HIT_UP 等）
 *
 * - 服务器 -> 客户端：
 *   - ASSIGN:<playerId>                通知客户端它被分配/确认的 playerId
 *   - KEY:/ACTION:                     广播其他客户端的输入指令
 *   - ERROR:<CODE>                     错误（如 ERROR:PLAYER_TAKEN / ERROR:UNKNOWN_CMD）
 *
 * 说明：
 * - 首次连接会自动分配 playerId：优先 p1、p2，之后为 anonN。
 * - 客户端可发送 HELLO:p1 或 HELLO:p2 来申请/切换身份；若已被占用，返回 ERROR:PLAYER_TAKEN。
 * - 收到 KEY:/ACTION: 的任意行会原样广播给所有已连接客户端（包含发送者），便于旁观或镜像输入。
 * - 控制台支持：
 *   - 输入 /list 查看连接与占位情况
 *   - 直接输入 KEY:/ACTION: 行可广播（用于人工测试）
 */
public class GameServer {

    private final int port;
    private volatile boolean running;

    private ServerSocket serverSocket;

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, ClientHandler> playerMap = new HashMap<>();
    private final Set<String> reserved = new HashSet<>();
    private final AtomicInteger anonCounter = new AtomicInteger(1);

    public GameServer() {
        this(9000);
    }

    public GameServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        serverSocket = new ServerSocket(port);
        log("Listening on " + port);

        Thread acceptThread = new Thread(this::acceptLoop, "GameServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        Thread consoleThread = new Thread(this::consoleLoop, "GameServer-Console");
        consoleThread.setDaemon(true);
        consoleThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "GameServer-Stop"));
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        for (ClientHandler ch : clients) ch.close();
        clients.clear();
        synchronized (playerMap) {
            playerMap.clear();
            reserved.clear();
        }
        log("Stopped.");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                s.setTcpNoDelay(true);
                ClientHandler ch = new ClientHandler(s);
                clients.add(ch);
                new Thread(ch, "GameServer-Client").start();
            } catch (IOException e) {
                if (running) err("accept error: " + e.getMessage());
            }
        }
    }

    private void consoleLoop() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = br.readLine()) != null) {
                line = sanitize(line);
                if (line.isEmpty()) continue;
                if ("/list".equalsIgnoreCase(line)) {
                    printStatus();
                } else {
                    // 允许从控制台直接广播 KEY:/ACTION: 进行测试
                    broadcast(line);
                }
            }
        } catch (IOException ignored) {}
    }

    private void printStatus() {
        System.out.println("=== Clients ===");
        for (ClientHandler ch : clients) {
            System.out.printf("- %s player=%s open=%s%n", ch.remote(), ch.playerId, ch.isOpen());
        }
        synchronized (playerMap) {
            System.out.println("=== Players ===");
            for (Map.Entry<String, ClientHandler> e : playerMap.entrySet()) {
                System.out.printf("- %s -> %s%n", e.getKey(), e.getValue().remote());
            }
        }
    }

    private void broadcast(String line) {
        String msg = sanitize(line);
        for (ClientHandler ch : clients) {
            ch.send(msg);
        }
    }

    private String sanitize(String line) {
        return line.replace("\r", "").replace("\n", "");
    }

    private String autoAssign() {
        synchronized (playerMap) {
            if (!reserved.contains("p1")) { reserved.add("p1"); return "p1"; }
            if (!reserved.contains("p2")) { reserved.add("p2"); return "p2"; }
            String id = "anon" + anonCounter.getAndIncrement();
            reserved.add(id);
            return id;
        }
    }

    private void log(String s) { System.out.println("[Server] " + s); }
    private void err(String s) { System.err.println("[Server] " + s); }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String playerId;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        String remote() {
            try { return socket.getRemoteSocketAddress().toString(); }
            catch (Exception e) { return "unknown"; }
        }

        boolean isOpen() {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // 初次分配 playerId
                playerId = autoAssign();
                synchronized (playerMap) { playerMap.put(playerId, this); }
                send("ASSIGN:" + playerId);
                log("Connected " + remote() + " -> " + playerId);

                String line;
                while ((line = in.readLine()) != null) {
                    line = sanitize(line);
                    if (line.isEmpty()) continue;

                    if (line.startsWith("HELLO:")) {
                        String want = line.substring("HELLO:".length()).trim();
                        if (!want.isEmpty()) {
                            synchronized (playerMap) {
                                ClientHandler owner = playerMap.get(want);
                                if (owner == null || owner == this) {
                                    // 释放旧 ID
                                    if (playerId != null) {
                                        playerMap.remove(playerId, this);
                                        reserved.remove(playerId);
                                    }
                                    playerId = want;
                                    playerMap.put(playerId, this);
                                    reserved.add(playerId);
                                    send("ASSIGN:" + playerId);
                                    log(remote() + " reassigned to " + playerId);
                                } else {
                                    send("ERROR:PLAYER_TAKEN");
                                }
                            }
                        }
                        continue;
                    }

                    // 只转发 KEY:/ACTION: 开头的协议行，其他报错
                    if (line.startsWith("KEY:") || line.startsWith("ACTION:")) {
                        broadcast(line);
                    } else {
                        send("ERROR:UNKNOWN_CMD");
                    }
                }
            } catch (IOException e) {
                log("Disconnected " + remote() + " (" + playerId + ")");
            } finally {
                close();
            }
        }

        void send(String msg) {
            if (out != null) {
                out.println(msg);
                out.flush();
            }
        }

        void close() {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}

            clients.remove(this);
            synchronized (playerMap) {
                if (playerId != null) {
                    playerMap.remove(playerId, this);
                    reserved.remove(playerId);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 9000;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        GameServer server = new GameServer(port);
        server.start();
        Thread.currentThread().join();
    }
}
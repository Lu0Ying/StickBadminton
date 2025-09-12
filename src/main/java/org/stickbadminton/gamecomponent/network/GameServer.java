package org.stickbadminton.gamecomponent.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的多客户端 TCP 服务器：
 * - 行文本协议（与 NetworkClient 配套）
 * - 自动分配玩家 ID（p1/p2；超出则 anonX）
 * - 支持客户端用 HELLO:<playerId> 主动声明玩家 ID
 * - 将收到的 KEY:/ACTION: 指令广播给所有已连接客户端
 *
 * 协议：
 * - 客户端 -> 服务端：
 *   - HELLO:<playerId>
 *   - KEY:PRESS:<KEYCODE> / KEY:RELEASE:<KEYCODE>
 *   - ACTION:<playerId>:<COMMAND>
 * - 服务端 -> 客户端：
 *   - ASSIGN:<playerId>       分配或确认玩家 ID
 *   - 广播 KEY:/ACTION: 行
 */
public class GameServer {

    private final int port;
    private volatile boolean running;

    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, ClientHandler> playerMap = new HashMap<>();
    private final Set<String> reserved = new HashSet<>();
    private final AtomicInteger anonCounter = new AtomicInteger(1);

    public GameServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        serverSocket = new ServerSocket(port);
        System.out.println("[Server] Listening on " + port);
        new Thread(this::acceptLoop, "GameServer-Accept").start();

        // 控制台：直接输入一行即广播；/list 查看状态
        new Thread(this::consoleLoop, "GameServer-Console").start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "GameServer-Stop"));
    }

    public void stop() {
        if (!running) return;
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        for (ClientHandler ch : clients) ch.close();
        clients.clear();
        synchronized (playerMap) {
            playerMap.clear();
            reserved.clear();
        }
        System.out.println("[Server] Stopped.");
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
                if (running) System.err.println("[Server] accept error: " + e.getMessage());
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

                // 初次分配
                playerId = autoAssign();
                synchronized (playerMap) { playerMap.put(playerId, this); }
                send("ASSIGN:" + playerId);
                System.out.printf("[Server] Connected %s -> %s%n", remote(), playerId);

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
                                    System.out.printf("[Server] %s reassigned to %s%n", remote(), playerId);
                                } else {
                                    send("ERROR:PLAYER_TAKEN");
                                }
                            }
                        }
                        continue;
                    }

                    // 只转发 KEY:/ACTION:
                    if (line.startsWith("KEY:") || line.startsWith("ACTION:")) {
                        broadcast(line);
                    } else {
                        send("ERROR:UNKNOWN_CMD");
                    }
                }
            } catch (IOException e) {
                System.out.printf("[Server] Disconnected %s (%s)%n", remote(), playerId);
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
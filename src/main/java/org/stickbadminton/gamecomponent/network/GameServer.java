// GameServer.java
package org.stickbadminton.gamecomponent.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

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

    // 服务端权威的按下集合
    private final Set<String> pressedP1 = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> pressedP2 = Collections.synchronizedSet(new HashSet<>());

    private final AtomicInteger watcherSeq = new AtomicInteger(1);

    // 硬校验白名单（与客户端软白名单一致）
    private static final Set<String> P1_WHITELIST = Set.of("Q", "W", "E", "A", "S", "D");
    private static final Set<String> P2_WHITELIST = Set.of("U", "I", "O", "J", "K", "L");

    // 游戏状态机
    private volatile String gameStatus = "LOBBY"; // LOBBY | IN_PROGRESS

    // —— 新增：比赛权威快照（由服务端维护）—— //
    private volatile int scoreLeft = 0;
    private volatile int scoreRight = 0;
    private volatile int serveSide = 1; // 1=左发球, -1=右发球

    // —— 新增：球的权威状态 —— //
    private volatile BallState ball = new BallState();
    private volatile long lastBallUpdateTime = System.currentTimeMillis();
    private static final double BADMINTON_GRAVITY = 980; // 重力加速度
    private static final double FRAME_TIME = 0.016; // 约60FPS
    private static final double PLAY_FIELD_LEFT = 0;
    private static final double PLAY_FIELD_RIGHT = 900;
    private static final double FLOOR_BALL_Y = 500;
    private static final double NET_HEIGHT = 100;
    private static final double NET_POSITION = 450;
    private static final double POWER_DISTANCE = 100; // 假设值，需要从 GameProperties 获取
    // 假设 GameProperties.powerDistance = 100;

    // 球状态类
    private static class BallState {
        double x = 450, y = 250;
        double speedX = 0, speedY = 0;
        boolean isFrozen = true;
        boolean isTouchedGround = false;
        int touchedTime = 11;
        boolean isShotable = true;
        double rotation = 0;
        boolean isHitted = false;
    }

    public GameServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) return;
        resetStateToLobby(); // 启动即重置到 LOBBY
        running = true;
        serverSocket = new ServerSocket(port);
        System.out.println("[Server] listening on " + port);

        // 接收线程
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

        // —— 新增：球状态更新线程 —— //
        new Thread(() -> {
            while (running) {
                try {
                    updateBallState();
                    broadcastBallState();
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException ignored) {}
            }
        }, "BallUpdate").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        clients.forEach(ClientHandler::close);
        clients.clear();
    }

    private void resetStateToLobby() {
        p1Holder = null;
        p2Holder = null;
        p1Ready = false;
        p2Ready = false;
        p1Select = 0;
        p2Select = 0;
        pressedP1.clear();
        pressedP2.clear();
        gameStatus = "LOBBY";
        scoreLeft = 0;
        scoreRight = 0;
        serveSide = 1;
        ball = new BallState();
        broadcast("GAME_STATUS:LOBBY");
        broadcast("SCORE:0:0");
        broadcast("SERVE:1");
        broadcastBallState();
    }

    private void setGameStatus(String newStatus) {
        if (newStatus.equals(gameStatus)) return;
        gameStatus = newStatus;
        broadcast("GAME_STATUS:" + newStatus);
        if ("LOBBY".equals(newStatus)) {
            resetStateToLobby();
        }
    }

    private void tryStartIfReady() {
        if (p1Holder == null || p2Holder == null) return;
        if (!p1Ready || !p2Ready) return;
        if (p1Select <= 0 || p2Select <= 0) return;
        if ("IN_PROGRESS".equals(gameStatus)) return;

        setGameStatus("IN_PROGRESS");
        broadcast("START:" + p1Select + ":" + p2Select);
        resetBallAfterScore(serveSide); // 初始化球
    }

    private void resetBallAfterScore(int newServeSide) {
        serveSide = newServeSide;
        broadcast("SERVE:" + serveSide);
        ball = new BallState();
        ball.isFrozen = true;
        ball.isTouchedGround = false;
        ball.touchedTime = 11;
        ball.isShotable = true;
        ball.rotation = serveSide == 1 ? 225 : -225;
        ball.isHitted = false;
        ball.y = 419; // 假设值 500 - 100 -11 +30
        if (serveSide == 1) {
            ball.x = 221; // 200 +21
        } else {
            ball.x = 700;
        }
        ball.speedX = 0;
        ball.speedY = 0;
        broadcastBallState();
    }

    private void broadcast(String msg) {
        clients.forEach(c -> c.send(msg));
    }

    private void broadcastBallState() {
        String msg = "BALL:" + ball.x + ":" + ball.y + ":" + ball.speedX + ":" + ball.speedY + ":" + ball.rotation + ":"
                + ball.isFrozen + ":" + ball.isTouchedGround + ":" + ball.touchedTime + ":" + ball.isShotable + ":" + ball.isHitted;
        broadcast(msg);
    }

    private void updateBallState() {
        if (!"IN_PROGRESS".equals(gameStatus)) return;

        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastBallUpdateTime) / 1000.0;
        lastBallUpdateTime = currentTime;

        if (ball.isFrozen) {
            // 发球状态处理
            ball.speedY = 0;
            ball.speedX = 0;
            if (serveSide == 1) {
                ball.x = 221;
                ball.y = 419;
            } else {
                ball.x = 700;
                ball.y = 419;
            }
            return;
        }

        // 更新球位置
        double newX = ball.x + ball.speedX * deltaTime;
        double newY = ball.y + ball.speedY * deltaTime;

        // 触网判断
        if (newY >= FLOOR_BALL_Y - NET_HEIGHT + 20
                && ((newX >= NET_POSITION - 18 && ball.x <= NET_POSITION - 18)
                || (newX <= NET_POSITION - 8 && ball.x >= NET_POSITION - 8))) {
            ball.touchedTime = 0;
            broadcast("NET_CRASH");
            if (ball.y < FLOOR_BALL_Y - NET_HEIGHT + 25) {
                newY = FLOOR_BALL_Y - NET_HEIGHT + 20;
                ball.speedY *= 0.1;
                ball.speedX *= 0.8;
            } else {
                if (ball.speedX > 0) {
                    newX = NET_POSITION - 23;
                } else {
                    newX = NET_POSITION - 3;
                }
                ball.speedY *= 0.4;
                ball.speedX = -ball.speedX * 0.4;
            }
        }

        // 落地判断
        if (newY >= FLOOR_BALL_Y) {
            ball.isTouchedGround = true;
            newY = FLOOR_BALL_Y;
            if (ball.speedY >= 400) {
                ball.speedY = - (ball.speedY * 0.4);
                ball.speedX *= 0.3;
            } else if (ball.speedY > 50) {
                ball.speedY = - (ball.speedY * 0.4);
                ball.speedX *= 0.5;
            } else {
                ball.speedY = 0;
                ball.speedX = 0;
                if (ball.isShotable) {
                    ball.isShotable = false;
                    int side = newX > NET_POSITION ? 1 : -1;
                    if (side == 1) {
                        scoreLeft++;
                    } else {
                        scoreRight++;
                    }
                    broadcast("SCORE:" + scoreLeft + ":" + scoreRight);
                    resetBallAfterScore(-side); // 对方发球
                }
            }
        } else {
            ball.isTouchedGround = false;
            double airResistance = 0.00001 * (Math.pow(ball.speedX, 2) + Math.pow(ball.speedY, 2));
            ball.speedY += BADMINTON_GRAVITY * deltaTime - 0.5 * airResistance *
                    (ball.speedY / Math.sqrt(Math.pow(ball.speedX, 2) + Math.pow(ball.speedY, 2)));
            ball.speedX -= 2.7 * airResistance *
                    (ball.speedX / Math.sqrt(Math.pow(ball.speedX, 2) + Math.pow(ball.speedY, 2)));
        }

        // 触墙判断
        if (newX <= PLAY_FIELD_LEFT || newX >= PLAY_FIELD_RIGHT) {
            ball.touchedTime = 0;
            newX = Math.max(PLAY_FIELD_LEFT, Math.min(newX, PLAY_FIELD_RIGHT));
            ball.speedX = -ball.speedX * 0.6;
        }

        ball.x = newX;
        ball.y = newY;

        // 方向修正
        if (!ball.isTouchedGround || Math.pow(ball.speedY, 2) > 50) {
            double targetRotation;
            double p = Math.sqrt(Math.pow(ball.speedX, 2) + Math.pow(ball.speedY, 2)) / 800;
            if (ball.speedX == 0) {
                targetRotation = ball.speedY > 0 ? 180 : 0;
            } else if (ball.speedX > 0) {
                targetRotation = 90 + Math.toDegrees(Math.atan(ball.speedY / ball.speedX));
            } else {
                targetRotation = -90 + Math.toDegrees(Math.atan(ball.speedY / ball.speedX));
            }
            if (ball.isHitted) {
                ball.rotation = targetRotation;
                ball.isHitted = false;
            } else {
                ball.rotation = targetRotation * p + ball.rotation * (1 - p);
            }
        }

        // TouchedTime
        if (ball.touchedTime <= 10) {
            ball.touchedTime++;
        }
    }

    private void handleHit(String playerId, double angle, boolean isHeavy) {
        if (ball.isFrozen || !ball.isShotable) return;

        while (angle < 0) angle += 360;
        while (angle > 360) angle -= 360;

        ball.isHitted = true;
        double speed;
        if (angle < 180) { // 扣球
            speed = isHeavy ? 2500 : 1200;
        } else if (ball.x >= NET_POSITION - POWER_DISTANCE
                && ball.x <= NET_POSITION + POWER_DISTANCE) { // 近场
            speed = isHeavy ? 900 : 500;
        } else if (ball.x <= NET_POSITION - POWER_DISTANCE * 2
                || ball.x >= NET_POSITION + POWER_DISTANCE * 2) { // 远场
            speed = isHeavy ? 1300 : 900;
        } else {
            speed = isHeavy ? 1100 : 700;
        }
        ball.speedY = speed * Math.sin(Math.toRadians(angle));
        ball.speedX = speed * Math.cos(Math.toRadians(angle));
        ball.touchedTime = 11; // 重置
        broadcast("HIT:" + playerId + ":" + (isHeavy ? "HEAVY" : "LIGHT"));
    }

    // 发送当前在线快照给新客户端
    private void sendPresenceSnapshot(ClientHandler newClient) {
        if (p1Holder != null) {
            newClient.send("PLAYER_STATE:p1:true");
            newClient.send("READY:p1:" + (p1Ready ? "1" : "0"));
            newClient.send("SELECT:p1:" + p1Select);
        } else {
            newClient.send("PLAYER_STATE:p1:false");
        }
        if (p2Holder != null) {
            newClient.send("PLAYER_STATE:p2:true");
            newClient.send("READY:p2:" + (p2Ready ? "1" : "0"));
            newClient.send("SELECT:p2:" + p2Select);
        } else {
            newClient.send("PLAYER_STATE:p2:false");
        }
        newClient.send("GAME_STATUS:" + gameStatus);
        newClient.send("SCORE:" + scoreLeft + ":" + scoreRight);
        newClient.send("SERVE:" + serveSide);
        newClient.send("BALL:" + ball.x + ":" + ball.y + ":" + ball.speedX + ":" + ball.speedY + ":" + ball.rotation + ":"
                + ball.isFrozen + ":" + ball.isTouchedGround + ":" + ball.touchedTime + ":" + ball.isShotable + ":" + ball.isHitted);
    }

    // 通知所有客户端分配变更
    private void onClientAssignedChanged() {
        broadcast("PLAYER_STATE:p1:" + (p1Holder != null ? "true" : "false"));
        broadcast("PLAYER_STATE:p2:" + (p2Holder != null ? "true" : "false"));
    }

    // 重置指定侧状态
    private void resetSide(String id) {
        if ("p1".equals(id)) {
            p1Ready = false;
            p1Select = 0;
            pressedP1.clear();
            broadcast("READY:p1:0");
            broadcast("SELECT:p1:0");
        } else if ("p2".equals(id)) {
            p2Ready = false;
            p2Select = 0;
            pressedP2.clear();
            broadcast("READY:p2:0");
            broadcast("SELECT:p2:0");
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private volatile boolean alive = true;
        private volatile String id; // "p1" | "p2" | "w<seq>"

        ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                close();
            }
        }

        @Override
        public void run() {
            try {
                String line;
                while (alive && (line = in.readLine()) != null) {
                    handleLine(line.trim());
                }
            } catch (Exception e) {
                System.out.println("[Server] Client error: " + e.getMessage());
            } finally {
                onClose();
            }
        }

        private void assignRole(String desired) {
            if ("p1".equalsIgnoreCase(desired) && p1Holder == null) {
                p1Holder = this;
                id = "p1";
            } else if ("p2".equalsIgnoreCase(desired) && p2Holder == null) {
                p2Holder = this;
                id = "p2";
            } else if (p1Holder == null) {
                p1Holder = this;
                id = "p1";
            } else if (p2Holder == null) {
                p2Holder = this;
                id = "p2";
            } else {
                id = "w" + watcherSeq.getAndIncrement();
            }

            if ("p1".equals(id) || "p2".equals(id)) {
                setGameStatus("LOBBY");
                resetSide(id);
            }

            send("ASSIGN:" + id);
            sendPresenceSnapshot(this);
            onClientAssignedChanged();
            System.out.println("[Server] assigned " + socket.getRemoteSocketAddress() + " as " + id);
        }

        private void handleLine(String line) {
            if (line.isEmpty()) return;

            try {
                if (line.startsWith("HELLO")) {
                    String desired = line.startsWith("HELLO:") ? line.substring("HELLO:".length()).trim() : null;
                    assignRole(desired);
                    return;
                }

                if (line.startsWith("HIT:")) {
                    if (!isPlayer()) return;
                    String[] parts = line.split(":");
                    if (parts.length == 3) {
                        double angle = Double.parseDouble(parts[1]);
                        boolean isHeavy = "HEAVY".equals(parts[2].toUpperCase());
                        handleHit(id, angle, isHeavy);
                    }
                    return;
                }

                if (line.startsWith("SERVE")) {
                    if (!isPlayer()) return;
                    if (("p1".equals(id) && serveSide == 1) || ("p2".equals(id) && serveSide == -1)) {
                        ball.isFrozen = false;
                        ball.speedX = 250 * serveSide;
                        ball.speedY = 300;
                    }
                    return;
                }

                if (line.startsWith("READY:")) {
                    if (!isPlayer()) return;
                    boolean ready = "1".equals(line.substring("READY:".length()));
                    if ("p1".equals(id)) p1Ready = ready; else p2Ready = ready;
                    broadcast("READY:" + id + ":" + (ready ? "1" : "0"));
                    tryStartIfReady();
                    return;
                }

                if (line.startsWith("SELECT:")) {
                    if (!isPlayer()) return;
                    String val = line.substring("SELECT:".length());
                    try {
                        int c = Integer.parseInt(val);
                        if ("p1".equals(id)) p1Select = c; else p2Select = c;
                        broadcast("SELECT:" + id + ":" + c);
                        tryStartIfReady();
                    } catch (NumberFormatException ignored) {}
                    return;
                }

                if (line.startsWith("KEY_DOWN:") || line.startsWith("KEY_UP:")) {
                    if (!isPlayer()) return;
                    boolean down = line.startsWith("KEY_DOWN:");
                    String key = line.substring(down ? "KEY_DOWN:".length() : "KEY_UP:".length()).trim().toUpperCase(Locale.ROOT);
                    if (key.isEmpty()) return;

                    Set<String> whitelist = "p1".equals(id) ? P1_WHITELIST : P2_WHITELIST;
                    if (whitelist.contains(key)) {
                        applyKey(id, key, down);
                    }
                    return;
                }
            } catch (Exception ex) {
                System.out.println("[Server] parse error: " + line + " -> " + ex.getMessage());
            }
        }

        private void applyKey(String pid, String key, boolean down) {
            Set<String> pressed = "p1".equals(pid) ? pressedP1 : pressedP2;
            if (down) {
                if (pressed.add(key)) {
                    broadcast("KEY_DOWN:" + pid + ":" + key);
                }
            } else {
                if (pressed.remove(key)) {
                    broadcast("KEY_UP:" + pid + ":" + key);
                }
            }
        }

        private boolean isPlayer() {
            return "p1".equals(id) || "p2".equals(id);
        }

        void send(String line) {
            if (out != null) {
                out.println(line);
                out.flush();
            }
        }

        void close() {
            alive = false;
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }

        private void onClose() {
            close();
            clients.remove(this);
            boolean changed = false;
            if (this == p1Holder) {
                p1Holder = null;
                resetSide("p1");
                broadcast("PLAYER_LEFT:p1");
                changed = true;
            } else if (this == p2Holder) {
                p2Holder = null;
                resetSide("p2");
                broadcast("PLAYER_LEFT:p2");
                changed = true;
            }
            if (changed) {
                onClientAssignedChanged();
                setGameStatus("LOBBY");
                scoreLeft = 0;
                scoreRight = 0;
                serveSide = 1;
                ball = new BallState();
            }
            System.out.println("[Server] client closed: " + id);
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8888;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        GameServer server = new GameServer(port);
        server.start();
        System.out.println("[Server] started. Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "ServerShutdown"));

        new CountDownLatch(1).await();
    }
}
// GameServer.java
package org.stickbadminton.gamecomponent.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import org.stickbadminton.gamecomponent.GameProperties;
/**
 * 权威 GameServer：
 * - 负责分配 p1/p2/watchers
 * - 校验按键白名单并广播 KEY_DOWN/KEY_UP
 * - 周期广播 KEY_STATE（心跳纠偏）
 * - 广播 READY/SELECT/START/GAME_STATUS/PLAYER_STATE/PLAYER_LEFT
 * - 新增：模拟羽毛球物理，广播 BALL_STATE，处理 HIT 请求，广播事件如 GROUND_HIT/NET_CRASH
 */
public class GameServer {
    private final int port;
    private volatile boolean running = false;

    private ServerSocket serverSocket;

    private static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

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

    // 新增：羽毛球模拟器
    private BadmintonSimulator ballSimulator;

    // 新增：模拟线程
    private Thread simulationThread;

    // 新增：上次击球时间，用于防重复击球
    private long lastHitTime = 0;
    private static final long HIT_COOLDOWN_MS = 200; // 0.2秒冷却

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
        if (simulationThread != null) {
            simulationThread.interrupt();
        }
        resetStateToLobby();
        System.out.println("[Server] stopped");
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
        setGameStatus("LOBBY");
        ballSimulator = null;
    }

    private void resetSide(String pid) {
        if ("p1".equals(pid)) {
            p1Ready = false;
            p1Select = 0;
            pressedP1.clear();
            broadcast("READY:p1:0");
            broadcast("SELECT:p1:0");
            broadcast("KEY_STATE:p1:");
        } else if ("p2".equals(pid)) {
            p2Ready = false;
            p2Select = 0;
            pressedP2.clear();
            broadcast("READY:p2:0");
            broadcast("SELECT:p2:0");
            broadcast("KEY_STATE:p2:");
        }
    }

    private static void broadcast(String line) {
        for (ClientHandler c : clients) {
            c.send(line);
        }
    }

    private void sendPresenceSnapshot(ClientHandler target) {
        target.send("INFO:PLAYER_STATE:p1:" + (p1Holder != null ? "READY" : "WAITING"));
        target.send("INFO:PLAYER_STATE:p2:" + (p2Holder != null ? "READY" : "WAITING"));
        target.send("READY:p1:" + (p1Ready ? "1" : "0"));
        target.send("READY:p2:" + (p2Ready ? "1" : "0"));
        target.send("SELECT:p1:" + p1Select);
        target.send("SELECT:p2:" + p2Select);
        // 当前按键状态快照
        target.send("KEY_STATE:p1:" + String.join(",", pressedP1));
        target.send("KEY_STATE:p2:" + String.join(",", pressedP2));
        // 同步游戏状态
        target.send("GAME_STATUS:" + gameStatus);
    }

    private void onClientAssignedChanged() {
        // 当 p1/p2 占位变化时告知所有人
        broadcast("INFO:PLAYER_STATE:p1:" + (p1Holder != null ? "READY" : "WAITING"));
        broadcast("INFO:PLAYER_STATE:p2:" + (p2Holder != null ? "READY" : "WAITING"));
    }

    private void setGameStatus(String status) {
        if (!Objects.equals(gameStatus, status)) {
            gameStatus = status;
            broadcast("GAME_STATUS:" + gameStatus);
            System.out.println("[Server] Game status -> " + status);
            if ("IN_PROGRESS".equals(status)) {
                startSimulation();
            }
        }
    }

    private void tryStartIfReady() {
        // 仅在 LOBBY -> IN_PROGRESS 转换时触发
        if (!"LOBBY".equals(gameStatus)) return;
        boolean canStart = (p1Holder != null && p2Holder != null && p1Ready && p2Ready && p1Select > 0 && p2Select > 0);
        if (canStart) {
            broadcast("START:" + p1Select + ":" + p2Select);
            setGameStatus("IN_PROGRESS");
        }
    }

    // 新增：启动羽毛球模拟
    private void startSimulation() {
        ballSimulator = new BadmintonSimulator();
        ballSimulator.init(0); // 初始发球方，根据游戏逻辑设置

        simulationThread = new Thread(() -> {
            long lastTime = System.nanoTime();
            int broadcastCounter = 0;
            final int broadcastInterval = 3; // 每3帧广播一次状态（约50ms）

            while ("IN_PROGRESS".equals(gameStatus) && running) {
                long currentTime = System.nanoTime();
                double dt = (currentTime - lastTime) / 1_000_000_000.0;
                lastTime = currentTime;

                ballSimulator.update(dt, pressedP1, pressedP2);

                // 广播状态
                broadcastCounter++;
                if (broadcastCounter >= broadcastInterval) {
                    broadcast(ballSimulator.getStateMessage());
                    broadcastCounter = 0;
                }

                try {
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException ignored) {}
            }
        }, "BallSimulation");
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    // ============== 客户端处理 ==============

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private volatile boolean alive = true;

        private String id; // "p1" | "p2" | "wN"

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(socket.getOutputStream(), true);

                // 第一句可能是 HELLO 或携带期望身份
                String first = in.readLine();
                String desired = null;
                if (first != null && first.startsWith("HELLO")) {
                    int idx = first.indexOf(':');
                    if (idx > 0 && idx + 1 < first.length()) {
                        desired = first.substring(idx + 1).trim();
                    }
                }

                assignRole(desired);

                String line;
                while (alive && (line = in.readLine()) != null) {
                    handleLine(line.trim());
                }
            } catch (SocketException se) {
                // 正常断开
            } catch (IOException e) {
                System.out.println("[Server] client IO error: " + e.getMessage());
            } finally {
                onClose();
            }
        }

        private void assignRole(String desired) {
            // 简单分配策略：优先满足 p1/p2 请求；否则填空位；否则 watcher
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

            // 新分配到 p1/p2：强制拉回 LOBBY，并重置该侧状态
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
                if (line.startsWith("READY:")) {
                    // READY:<0|1>
                    if (!isPlayer()) return;
                    boolean ready = "1".equals(line.substring("READY:".length()));
                    if ("p1".equals(id)) p1Ready = ready;
                    else p2Ready = ready;
                    broadcast("READY:" + id + ":" + (ready ? "1" : "0"));
                    tryStartIfReady();
                    return;
                }
                if (line.startsWith("SELECT:")) {
                    // SELECT:<int>
                    if (!isPlayer()) return;
                    String val = line.substring("SELECT:".length());
                    try {
                        int c = Integer.parseInt(val);
                        if ("p1".equals(id)) p1Select = c;
                        else p2Select = c;
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

                    if ("p1".equals(id)) {
                        if (P1_WHITELIST.contains(key)) {
                            applyKey("p1", key, down);
                        }
                    } else {
                        if (P2_WHITELIST.contains(key)) {
                            applyKey("p2", key, down);
                        }
                    }
                    return;
                }
                // 新增：处理击球请求 HIT:<type>:<angle>
                if (line.startsWith("HIT:")) {
                    if (!isPlayer() || ballSimulator == null) return;
                    String[] parts = line.split(":", 3);
                    if (parts.length == 3) {
                        String type = parts[1];
                        double angle;
                        try {
                            angle = Double.parseDouble(parts[2]);
                        } catch (NumberFormatException ignored) {
                            return;
                        }
                        // 校验是否可以击球：球在该玩家侧，冷却时间等
                        long now = System.currentTimeMillis();
                        if (now - lastHitTime < HIT_COOLDOWN_MS) return;
                        boolean isP1 = "p1".equals(id);
                        double centerX = ballSimulator.getCenterX();
                        if ((isP1 && centerX < GameProperties.netPosition) || (!isP1 && centerX > GameProperties.netPosition)) {
                            if (!ballSimulator.isFrozen && !ballSimulator.isTouchedGround) {
                                if ("light".equals(type)) {
                                    ballSimulator.lightHit(angle);
                                } else if ("heavy".equals(type)) {
                                    ballSimulator.heavyHit(angle);
                                }
                                lastHitTime = now;
                            }
                        }
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
            try {
                if (in != null) in.close();
            } catch (IOException ignored) {}
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {}
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }

        private void onClose() {
            close();

            clients.remove(this);

            boolean changed = false;
            if (this == p1Holder) {
                p1Holder = null;
                p1Ready = false;
                p1Select = 0;
                pressedP1.clear();
                broadcast("PLAYER_LEFT:p1");
                changed = true;
            } else if (this == p2Holder) {
                p2Holder = null;
                p2Ready = false;
                p2Select = 0;
                pressedP2.clear();
                broadcast("PLAYER_LEFT:p2");
                changed = true;
            }
            if (changed) {
                onClientAssignedChanged();
                // 任何一侧离开都回到 LOBBY
                setGameStatus("LOBBY");
            }
            System.out.println("[Server] client closed: " + id);
        }
    }

    // 新增：羽毛球模拟类（纯Java物理模拟）
    private static class BadmintonSimulator {
        public double x, y, speedX, speedY, rotation;
        public boolean isFrozen = true;
        public boolean isTouchedGround = false;
        public boolean isHitted = false;
        public int TouchedTime = 11;
        public boolean isShotable = true;
        public int sideServe = 0; // 初始发球方

        // 其他需要的常量，从GameProperties复制
        private static final double badmintonGravity = GameProperties.badmintonGravity; // 假设值
        private static final double floorBallY = GameProperties.floorBallY;
        private static final double playFieldLeft = GameProperties.playFieldLeft;
        private static final double playFieldRight = GameProperties.playFieldRight;
        private static final double netPosition = GameProperties.netPosition;
        private static final double netHeight = GameProperties.netHeight;

        public void init(int _sideServe) {
            sideServe = _sideServe;
            speedY = -100;
            // 初始位置根据发球方
            double initialX = (sideServe == 1) ? 200 + 21 : 700;
            double initialY = GameProperties.floorY - GameProperties.playerHeight - 11 + 30;
            setPosition(initialX, initialY);
            rotation = (sideServe == 1) ? 225 : -225;
        }

        public void update(double dt, Set<String> pressedP1, Set<String> pressedP2) {
            if (isFrozen) {
                // 检查发球键（假设 Q/E 为 p1 发球键，U/O 为 p2）
                Set<String> servePressed = (sideServe == 1) ? pressedP1 : pressedP2;
                boolean serveKeyPressed = (sideServe == 1) ? (servePressed.contains("Q") || servePressed.contains("E")) : (servePressed.contains("U") || servePressed.contains("O"));
                if (serveKeyPressed) {
                    isFrozen = false;
                    speedX = 250 * sideServe;
                    speedY = 300;
                }
                // 位置固定到初始（近似，不模拟球员位置）
                return;
            }

            // 复制 Badminton.onUpdate 的物理逻辑
            double airResistance = 0;
            if (speedY == 0 && speedX == 0) {
                speedY += badmintonGravity;
            } else {
                airResistance = 0.00001 * (Math.pow(speedX, 2) + Math.pow(speedY, 2));
                speedY += badmintonGravity - 0.5 * airResistance * (speedY / Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)));
                speedX -= 2.7 * airResistance * (speedX / Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)));
            }

            // 落地判断
            if (y + speedY * dt >= floorBallY) {
                isTouchedGround = true;
                if (isShotable) {
                    isShotable = false;
                    int side = (getCenterX() > 450) ? 1 : -1;
                    broadcast("GROUND_HIT:" + side);
                    // 可在此重置球状态，根据游戏逻辑
                    // 如 isFrozen = true; init(newSideServe);
                }
                y = floorBallY;
                if (speedY >= 400) {
                    speedY = -(speedY * 0.4);
                    speedX *= 0.3;
                } else if (speedY > 50) {
                    speedY = -(speedY * 0.4);
                    speedX *= 0.5;
                } else {
                    speedY = 0;
                    speedX = 0;
                }
            } else {
                isTouchedGround = false;
            }

            // 触墙判断
            if (x + speedX * dt <= playFieldLeft || x + speedX * dt >= playFieldRight) {
                TouchedTime = 0;
                x = (x - playFieldLeft < playFieldRight - x) ? playFieldLeft : playFieldRight;
                speedX = -speedX * 0.6;
            }

            // 触网判断
            if (y + speedY * dt >= floorBallY - netHeight + 20 && ((x + speedX * dt >= netPosition - 18 && x <= netPosition - 18) || (x + speedX * dt <= netPosition - 8 && x >= netPosition - 8))) {
                TouchedTime = 0;
                broadcast("NET_CRASH");
                if (y < floorBallY - netHeight + 25) {
                    y = floorBallY - netHeight + 20;
                    speedY = speedY * 0.1;
                    speedX = speedX * 0.8;
                } else {
                    if (speedX > 0) {
                        x = netPosition - 23;
                    } else {
                        x = netPosition - 3;
                    }
                    speedY = speedY * 0.4;
                    speedX = -speedX * 0.4;
                }
            }

            // 更新位置
            x += speedX * dt;
            y += speedY * dt;

            // 方向修正
            if (!isTouchedGround || Math.pow(speedY, 2) > 50) {
                double targetRotation;
                if (speedX == 0) {
                    targetRotation = speedY > 0 ? 180 : 0;
                } else if (speedX > 0) {
                    targetRotation = 90 + Math.toDegrees(Math.atan(speedY / speedX));
                } else {
                    targetRotation = -90 + Math.toDegrees(Math.atan(speedY / speedX));
                }
                double p = Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)) / 800;
                if (isHitted) {
                    rotation = targetRotation;
                    isHitted = false;
                } else {
                    rotation = targetRotation * p + rotation * (1 - p);
                }
            }
        }

        public String getStateMessage() {
            return "BALL_STATE:" + x + ":" + y + ":" + speedX + ":" + speedY + ":" + rotation;
        }

        public double getCenterX() {
            return x + 10.5;
        }

        public double getCenterY() {
            return y + 3;
        }

        public void setPosition(double _x, double _y) {
            x = _x;
            y = _y;
        }

        public void lightHit(double angle) {
            // 复制 Badminton.lightHit 逻辑
            while (angle < 0) angle += 360;
            while (angle > 360) angle -= 360;
            isHitted = true;
            double speed;
            double centerX = getCenterX();
            if (angle < 180) speed = 1200;
            else if (centerX >= netPosition - GameProperties.powerDistance && centerX <= netPosition + GameProperties.powerDistance) speed = 500;
            else if (centerX <= netPosition - GameProperties.powerDistance * 2 || centerX >= netPosition + GameProperties.powerDistance * 2) speed = 900;
            else speed = 700;
            speedY = speed * Math.sin(Math.toRadians(angle));
            speedX = speed * Math.cos(Math.toRadians(angle));
            // 假设无粒子
            // onHit(); 但服务器无视觉
        }

        public void heavyHit(double angle) {
            // 类似复制 heavyHit
            while (angle < 0) angle += 360;
            while (angle > 360) angle -= 360;
            isHitted = true;
            double speed;
            double centerX = getCenterX();
            if (angle < 180) speed = 2500;
            else if (centerX >= netPosition - GameProperties.powerDistance && centerX <= netPosition + GameProperties.powerDistance) speed = 900;
            else if (centerX <= netPosition - GameProperties.powerDistance * 2 || centerX >= netPosition + GameProperties.powerDistance * 2) speed = 1300;
            else speed = 1100;
            speedY = speed * Math.sin(Math.toRadians(angle));
            speedX = speed * Math.cos(Math.toRadians(angle));
            // 假设无粒子
        }
    }

    // ============== 独立运行入口 ==============

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

        // 优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "ServerShutdown"));

        // 阻塞主线程
        new CountDownLatch(1).await();
    }
}
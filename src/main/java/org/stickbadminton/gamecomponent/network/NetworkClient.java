package org.stickbadminton.gamecomponent.network;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 文本协议客户端（服务端权威，同步回显）：
 *
 * 发送：
 * - KEY:PRESS/RELEASE:<KEYCODE>
 * - ACTION:<任意占位>:<COMMAND>（服务端会盖章为你的身份）
 * - SELECT:<characterId>，READY:<0|1>，START
 *
 * 接收（仅以此驱动游戏状态，拦截本地物理按键，防止本地自算）：
 * - KEY:<p1|p2>:PRESS/RELEASE:<KEYCODE>
 * - ACTION:<p1|p2>:<COMMAND>
 * - SELECT:<playerId>:<characterId>，READY:<playerId>:<0|1>，START:<ct1>:<ct2>
 *
 * 输入限制（客户端侧也做软约束，服务端有强校验）：
 * - p1 仅采集/发送 q w e a s d
 * - p2 仅采集/发送 u i o j k l
 * - watcher 不发送 KEY/ACTION
 */
public class NetworkClient {

    public enum Action {
        MOVE_LEFT_ON, MOVE_LEFT_OFF,
        MOVE_RIGHT_ON, MOVE_RIGHT_OFF,
        JUMP,
        LIGHT_HIT, HEAVY_HIT,
        LIGHT_HIT_UP, LIGHT_HIT_DOWN,
        HEAVY_HIT_UP, HEAVY_HIT_DOWN
    }

    // 网络状态/大厅事件监听
    public interface ConnectionListener {
        default void onAssigned(String playerId) {}
        default void onPlayerState(String playerId, boolean present) {} // 连接占位（READY/WAITING）
        default void onPlayerLeft(String playerId) {}

        // 新增：就绪与选人、开始
        default void onReadyState(String playerId, boolean ready) {}
        default void onSelected(String playerId, int characterId) {}
        default void onStartGame(int ct1, int ct2) {}
        default void onGameStatusChanged(String status) {} // STARTED, ENDED 等
    }

    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    public void addConnectionListener(ConnectionListener l) { if (l != null) connectionListeners.add(l); }
    public void removeConnectionListener(ConnectionListener l) { connectionListeners.remove(l); }

    private void runOnFxThreadOrNow(Runnable r) {
        try {
            if (Platform.isFxApplicationThread()) r.run();
            else Platform.runLater(r);
        } catch (IllegalStateException e) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    private void notifyAssigned(String id) {
        this.assignedId = id;
        System.out.println("[Client] Assigned as: " + id);
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onAssigned(id); } catch (Exception ignored) {}
        }));
    }

    private void notifyPlayerState(String id, boolean present) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onPlayerState(id, present); } catch (Exception ignored) {}
        }));
    }

    private void notifyPlayerLeft(String id) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onPlayerLeft(id); } catch (Exception ignored) {}
        }));
    }

    private void notifyReady(String id, boolean ready) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onReadyState(id, ready); } catch (Exception ignored) {}
        }));
    }

    private void notifySelected(String id, int characterId) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onSelected(id, characterId); } catch (Exception ignored) {}
        }));
    }

    private void notifyStart(int ct1, int ct2) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onStartGame(ct1, ct2); } catch (Exception ignored) {}
        }));
    }

    private void notifyGameStatus(String status) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onGameStatusChanged(status); } catch (Exception ignored) {}
        }));
    }

    private final String host;
    private final int port;
    private final String desiredPlayerId;

    private volatile boolean running = false;
    private Thread ioThread;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // 由服务端回显注入的按键集合，用于区分物理输入与网络注入，避免回显->本地转发
    private final Set<KeyCode> pressedByServer = Collections.synchronizedSet(new HashSet<>());
    // 本地物理按下状态，用于只在第一次按下时发送 PRESS，释放时发送 RELEASE
    private final Set<KeyCode> pressedLocally = Collections.synchronizedSet(new HashSet<>());

    private final Map<String, Map<Action, KeyCode>> perPlayerKeyMap = new ConcurrentHashMap<>();
    private final Set<Scene> attachedScenes = Collections.newSetFromMap(new IdentityHashMap<>());

    private volatile String assignedId;

    // 本地键位白名单（客户端侧软校验）
    private static final EnumSet<KeyCode> P1_KEYS = EnumSet.of(KeyCode.Q, KeyCode.W, KeyCode.E, KeyCode.A, KeyCode.S, KeyCode.D);
    private static final EnumSet<KeyCode> P2_KEYS = EnumSet.of(KeyCode.U, KeyCode.I, KeyCode.O, KeyCode.J, KeyCode.K, KeyCode.L);

    public NetworkClient(String host, int port) {
        this(host, port, null);
    }

    public NetworkClient(String host, int port, String desiredPlayerId) {
        this.host = host;
        this.port = port;
        this.desiredPlayerId = desiredPlayerId;
        initDefaultKeyMaps();
    }

    public String getAssignedId() { return assignedId; }

    private void initDefaultKeyMaps() {
        Map<Action, KeyCode> p1 = new EnumMap<>(Action.class);
        p1.put(Action.MOVE_LEFT_ON, KeyCode.A);
        p1.put(Action.MOVE_LEFT_OFF, KeyCode.A);
        p1.put(Action.MOVE_RIGHT_ON, KeyCode.D);
        p1.put(Action.MOVE_RIGHT_OFF, KeyCode.D);
        p1.put(Action.JUMP, KeyCode.W);
        p1.put(Action.LIGHT_HIT, KeyCode.Q);
        p1.put(Action.HEAVY_HIT, KeyCode.E);
        p1.put(Action.LIGHT_HIT_UP, KeyCode.Q);
        p1.put(Action.LIGHT_HIT_DOWN, KeyCode.S); // 修改为S键
        p1.put(Action.HEAVY_HIT_UP, KeyCode.E);
        p1.put(Action.HEAVY_HIT_DOWN, KeyCode.S); // 修改为S键

        Map<Action, KeyCode> p2 = new EnumMap<>(Action.class);
        p2.put(Action.MOVE_LEFT_ON, KeyCode.J);
        p2.put(Action.MOVE_LEFT_OFF, KeyCode.J);
        p2.put(Action.MOVE_RIGHT_ON, KeyCode.L);
        p2.put(Action.MOVE_RIGHT_OFF, KeyCode.L);
        p2.put(Action.JUMP, KeyCode.I);
        p2.put(Action.LIGHT_HIT, KeyCode.U);
        p2.put(Action.HEAVY_HIT, KeyCode.O);
        p2.put(Action.LIGHT_HIT_UP, KeyCode.U);
        p2.put(Action.LIGHT_HIT_DOWN, KeyCode.K); // 修改为K键
        p2.put(Action.HEAVY_HIT_UP, KeyCode.O);
        p2.put(Action.HEAVY_HIT_DOWN, KeyCode.K); // 修改为K键

        perPlayerKeyMap.put("p1", p1);
        perPlayerKeyMap.put("p2", p2);
    }

    public void setPlayerKeyMap(String playerId, Map<Action, KeyCode> map) {
        if (playerId == null || map == null) return;
        perPlayerKeyMap.put(playerId, new EnumMap<>(map));
    }

    // 大厅发送
    public void sendSelect(int characterId) {
        // 允许发送 0（撤销）
        sendLine("SELECT:" + characterId);
    }

    public void sendReady(boolean ready) {
        sendLine("READY:" + (ready ? "1" : "0"));
    }

    public void sendStartRequest() {
        sendLine("START");
    }

    private boolean isController() {
        return "p1".equalsIgnoreCase(assignedId) || "p2".equalsIgnoreCase(assignedId);
    }

    private boolean isKeyAllowedForThisClient(KeyCode code) {
        if (!isController()) {
            System.out.println("[Client] Not a controller (" + assignedId + "), rejecting key: " + code);
            return false;
        }
        if ("p1".equalsIgnoreCase(assignedId)) {
            boolean allowed = P1_KEYS.contains(code);
            if (!allowed) {
                System.out.println("[Client] P1 key not allowed: " + code + " (allowed: " + P1_KEYS + ")");
            }
            return allowed;
        }
        if ("p2".equalsIgnoreCase(assignedId)) {
            boolean allowed = P2_KEYS.contains(code);
            if (!allowed) {
                System.out.println("[Client] P2 key not allowed: " + code + " (allowed: " + P2_KEYS + ")");
            }
            return allowed;
        }
        return false;
    }

    // 按键转发绑定（拦截物理输入，避免客户端自算；仅发送到服务器）
    public void attachToScene(Scene scene) {
        if (scene == null) return;
        if (attachedScenes.contains(scene)) return;
        attachedScenes.add(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode code = ev.getCode();
            if (code == null) return;

            // 如果是由服务端注入的网络回显事件，则放行给游戏，不发送、不消费
            if (pressedByServer.contains(code)) {
                System.out.println("[Client] Server key PRESS passed through: " + code);
                return;
            }

            // watcher 或未分配 或 非法键位：消费事件，禁止本地生效，不发送
            if (!isKeyAllowedForThisClient(code)) {
                System.out.println("[Client] Local key PRESS blocked: " + code + " (assignedId=" + assignedId + ")");
                ev.consume();
                return;
            }

            // 仅首次按下发送 PRESS
            if (pressedLocally.add(code)) {
                System.out.println("[Client] Sending local key PRESS: " + code);
                sendKeyPress(code);
            }
            // 消费物理事件，防止本地立即生效
            ev.consume();
        });

        scene.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
            KeyCode code = ev.getCode();
            if (code == null) return;

            // 回显释放事件：放行
            if (pressedByServer.contains(code)) {
                System.out.println("[Client] Server key RELEASE passed through: " + code);
                return;
            }

            // watcher/未分配/非法键位：消费，且清理可能残留状态
            if (!isKeyAllowedForThisClient(code)) {
                pressedLocally.remove(code);
                System.out.println("[Client] Local key RELEASE blocked: " + code + " (assignedId=" + assignedId + ")");
                ev.consume();
                return;
            }

            // 只在本地记为按下过时发送 RELEASE
            if (pressedLocally.remove(code)) {
                System.out.println("[Client] Sending local key RELEASE: " + code);
                sendKeyRelease(code);
            }
            // 同样消费物理事件，防止本地立即生效
            ev.consume();
        });

        System.out.println("[Client] input attached to Scene@" + Integer.toHexString(System.identityHashCode(scene)));
    }

    public boolean attachToPrimaryScene() {
        Scene s = getScene();
        if (s != null) {
            attachToScene(s);
            return true;
        }
        return false;
    }

    public void attachToPrimaryStageAuto() {
        runOnFxThreadOrNow(() -> {
            Stage stage;
            try { stage = FXGL.getPrimaryStage(); }
            catch (Exception e) { System.out.println("[Client] getPrimaryStage failed: " + e.getMessage()); return; }
            if (stage == null) return;

            Scene current = stage.getScene();
            if (current != null) attachToScene(current);

            stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) attachToScene(newScene);
            });
            System.out.println("[Client] primary stage auto-attach enabled");
        });
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        ioThread = new Thread(this::runLoop, "NetworkClient-IO");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public synchronized void stop() {
        running = false;
        closeSilently();
        if (ioThread != null) {
            try { ioThread.join(1000); } catch (InterruptedException ignored) {}
            ioThread = null;
        }
        flushAllPressedKeys();
        pressedLocally.clear();
        assignedId = null;
        System.out.println("[Client] stopped");
    }

    public void sendKeyPress(KeyCode code) {
        if (code == null) return;
        if (!isKeyAllowedForThisClient(code)) return; // 软限制
        sendLine("KEY:PRESS:" + code.name());
    }

    public void sendKeyRelease(KeyCode code) {
        if (code == null) return;
        if (!isKeyAllowedForThisClient(code)) return; // 软限制
        sendLine("KEY:RELEASE:" + code.name());
    }

    public void sendAction(Action action) {
        if (action == null) return;
        if (!isController()) return; // watcher 不发送
        // 发送的 playerId 服务端会覆盖为实际分配的 assignedId，这里传什么都可以
        String pid = assignedId != null ? assignedId : (desiredPlayerId != null ? desiredPlayerId : "p1");
        sendLine("ACTION:" + pid + ":" + action.name());
    }

    public void sendLine(String line) {
        PrintWriter writer = out;
        if (writer != null) {
            writer.println(line);
            writer.flush();
            System.out.println("[Client] TX " + line);
        } else {
            System.out.println("[Client] TX drop (not connected): " + line);
        }
    }

    private void runLoop() {
        while (running) {
            try {
                connect();
                System.out.println("[Client] connected to " + host + ":" + port);
                if (desiredPlayerId != null && !desiredPlayerId.isEmpty()) {
                    sendLine("HELLO:" + desiredPlayerId);
                }
                String line;
                while (running && (line = in.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        System.out.println("[Client] RX " + line);
                        handleLine(line);
                    }
                }
                System.out.println("[Client] server closed connection");
            } catch (IOException e) {
                System.out.println("[Client] connect/read error: " + e.getMessage());
                sleep(3000); // 重连延迟增加到3秒
            } finally {
                closeSilently();
            }
        }
    }

    private void connect() throws IOException {
        System.out.println("[Client] connecting " + host + ":" + port + " ...");
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private void closeSilently() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
        out = null;
        in = null;
        socket = null;
    }

    private void handleLine(String line) {
        try {
            if (line.startsWith("KEY:")) {
                handleKeyLine(line);
            } else if (line.startsWith("ACTION:")) {
                handleActionLine(line);
            } else if (line.startsWith("ASSIGN:")) {
                String assigned = line.substring("ASSIGN:".length()).trim();
                System.out.println("[Client] assigned as " + assigned);
                notifyAssigned(assigned);
            } else if (line.startsWith("INFO:")) {
                handleInfoLine(line);
            } else if (line.startsWith("READY:")) {
                handleReadyLine(line);
            } else if (line.startsWith("SELECT:")) {
                handleSelectLine(line);
            } else if (line.startsWith("START:")) {
                handleStartLine(line);
            } else if (line.startsWith("GAME_STATUS:")) {
                handleGameStatusLine(line);
            } else if (line.startsWith("ERROR:")) {
                System.out.println("[Client] server error: " + line);
            }
        } catch (Exception e) {
            System.out.println("[Client] handle error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGameStatusLine(String line) {
        // GAME_STATUS:STARTED 或 GAME_STATUS:ENDED
        String[] parts = line.split(":", 2);
        if (parts.length >= 2) {
            String status = parts[1].trim();
            notifyGameStatus(status);
        }
    }

    private void handleInfoLine(String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) return;

        if ("INFO".equals(parts[0]) && "PLAYER_STATE".equals(parts[1]) && parts.length >= 4) {
            String playerId = parts[2].trim().toLowerCase();
            String state = parts[3].trim().toUpperCase();
            boolean present = "READY".equals(state) || "1".equals(state) || "CONNECTED".equals(state);
            notifyPlayerState(playerId, present);
            return;
        }

        if ("INFO".equals(parts[0]) && "PLAYER_LEFT".equals(parts[1]) && parts.length >= 3) {
            String playerId = parts[2].trim().toLowerCase();
            notifyPlayerLeft(playerId);
            notifyPlayerState(playerId, false);
        }
    }

    private void handleReadyLine(String line) {
        // READY:<playerId>:<0|1>
        String[] p = line.split(":");
        if (p.length != 3) return;
        String pid = p[1].trim().toLowerCase();
        String v = p[2].trim();
        boolean r = "1".equals(v) || "true".equalsIgnoreCase(v);
        notifyReady(pid, r);
    }

    private void handleSelectLine(String line) {
        // SELECT:<playerId>:<characterId>
        String[] p = line.split(":");
        if (p.length != 3) return;
        String pid = p[1].trim().toLowerCase();
        int cid = 0;
        try { cid = Integer.parseInt(p[2].trim()); } catch (NumberFormatException ignored) {}
        notifySelected(pid, cid);
    }

    private void handleStartLine(String line) {
        // START:<ct1>:<ct2>
        String[] p = line.split(":");
        if (p.length != 3) return;
        int ct1 = 0, ct2 = 0;
        try { ct1 = Integer.parseInt(p[1].trim()); } catch (NumberFormatException ignored) {}
        try { ct2 = Integer.parseInt(p[2].trim()); } catch (NumberFormatException ignored) {}
        notifyStart(ct1, ct2);
    }

    private void handleKeyLine(String line) {
        // KEY:<playerId>:PRESS/RELEASE:<KEYCODE>
        String[] parts = line.split(":");
        if (parts.length != 4) return;

        String playerId = parts[1].trim().toLowerCase();
        String op = parts[2].trim().toUpperCase();
        String keyName = parts[3].trim().toUpperCase();

        final KeyCode code;
        try {
            code = KeyCode.valueOf(keyName);
        } catch (IllegalArgumentException ex) {
            System.out.println("[Client] unknown KeyCode: " + keyName);
            return;
        }

        System.out.println("[Client] Server key command: " + playerId + " " + op + " " + code);

        switch (op) {
            case "PRESS":
                simulateServerKeyPress(code);
                break;
            case "RELEASE":
                simulateServerKeyRelease(code);
                break;
            default:
                System.out.println("[Client] unknown KEY op: " + op);
        }
    }

    private void handleActionLine(String line) {
        // ACTION:<playerId>:<COMMAND>
        String[] parts = line.split(":");
        if (parts.length != 3) return;

        String playerId = parts[1].trim();
        String cmd = parts[2].trim().toUpperCase();

        final Action action;
        try {
            action = Action.valueOf(cmd);
        } catch (IllegalArgumentException e) {
            System.out.println("[Client] unknown action: " + cmd);
            return;
        }

        Map<Action, KeyCode> map = perPlayerKeyMap.get(playerId);
        if (map == null) {
            System.out.println("[Client] no key map for playerId " + playerId);
            return;
        }

        KeyCode code = map.get(action);
        if (code == null) {
            System.out.println("[Client] no key for action " + action + " of " + playerId);
            return;
        }

        System.out.println("[Client] Server action: " + playerId + " " + action + " -> " + code);

        switch (action) {
            case MOVE_LEFT_ON:
            case MOVE_RIGHT_ON:
                simulateServerKeyPress(code);
                break;

            case MOVE_LEFT_OFF:
            case MOVE_RIGHT_OFF:
                simulateServerKeyRelease(code);
                break;

            case JUMP:
            case LIGHT_HIT:
            case HEAVY_HIT:
            case LIGHT_HIT_UP:
            case LIGHT_HIT_DOWN:
            case HEAVY_HIT_UP:
            case HEAVY_HIT_DOWN:
                simulateServerKeyPress(code);
                scheduleServerRelease(code, 50); // 增加按键持续时间
                break;
        }
    }

    private void simulateServerKeyPress(KeyCode code) {
        if (code == null) return;
        if (pressedByServer.contains(code)) return;
        pressedByServer.add(code);

        Scene scene = getScene();
        if (scene == null) {
            System.out.println("[Client] scene null on server PRESS " + code);
            return;
        }

        System.out.println("[Client] Simulating server key PRESS: " + code);
        runOnFxThreadOrNow(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
            Event.fireEvent(scene, ev);
        });
    }

    private void simulateServerKeyRelease(KeyCode code) {
        if (code == null) return;
        pressedByServer.remove(code);

        Scene scene = getScene();
        if (scene == null) {
            System.out.println("[Client] scene null on server RELEASE " + code);
            return;
        }

        System.out.println("[Client] Simulating server key RELEASE: " + code);
        runOnFxThreadOrNow(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false);
            Event.fireEvent(scene, ev);
        });
    }

    private void flushAllPressedKeys() {
        Scene scene = getScene();
        if (scene == null) {
            pressedByServer.clear();
            return;
        }
        Set<KeyCode> snapshot = new HashSet<>(pressedByServer);
        pressedByServer.clear();
        runOnFxThreadOrNow(() -> {
            for (KeyCode code : snapshot) {
                KeyEvent ev = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false);
                Event.fireEvent(scene, ev);
            }
        });
    }

    private Scene getScene() {
        try {
            return FXGL.getPrimaryStage().getScene();
        } catch (Exception e) {
            return null;
        }
    }

    private void scheduleServerRelease(KeyCode code, int delayMs) {
        new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            simulateServerKeyRelease(code);
        }, "NetworkClient-ServerReleaseDelay").start();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
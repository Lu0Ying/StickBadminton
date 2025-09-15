package org.stickbadminton.gamecomponent.network;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

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
 * 行文本协议客户端，匹配 GameServer：
 * - KEY:PRESS:<KEYCODE>
 * - KEY:RELEASE:<KEYCODE>
 * - ACTION:<playerId>:<COMMAND>
 * - ASSIGN:<playerId>（来自服务器）
 * - INFO:PLAYER_STATE:<p1|p2>:READY|WAITING（来自服务器）
 * - INFO:PLAYER_LEFT:<p1|p2>（兼容）
 *
 * 与游戏联通：
 * - attachToScene/attachToPrimaryScene：把本地按键事件转发到服务器
 * - 收到远端按键/动作时，注入到 JavaFX Scene（若 Scene 不存在则跳过注入，仅日志）
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

    // 网络状态监听
    public interface ConnectionListener {
        default void onAssigned(String playerId) {}
        default void onPlayerState(String playerId, boolean ready) {}
        default void onPlayerLeft(String playerId) {}
    }

    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    public void addConnectionListener(ConnectionListener l) {
        if (l != null) connectionListeners.add(l);
    }
    public void removeConnectionListener(ConnectionListener l) {
        connectionListeners.remove(l);
    }

    private void notifyAssigned(String id) {
        this.assignedId = id;
        Platform.runLater(() -> {
            for (ConnectionListener l : connectionListeners) {
                try { l.onAssigned(id); } catch (Exception ignored) {}
            }
        });
    }

    private void notifyPlayerState(String id, boolean ready) {
        Platform.runLater(() -> {
            for (ConnectionListener l : connectionListeners) {
                try { l.onPlayerState(id, ready); } catch (Exception ignored) {}
            }
        });
    }

    private void notifyPlayerLeft(String id) {
        Platform.runLater(() -> {
            for (ConnectionListener l : connectionListeners) {
                try { l.onPlayerLeft(id); } catch (Exception ignored) {}
            }
        });
    }

    private final String host;
    private final int port;
    private final String desiredPlayerId;

    private volatile boolean running = false;
    private Thread ioThread;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // 本客户端注入的按键（远端来的），用于避免回环转发
    private final Set<KeyCode> pressedByClient = Collections.synchronizedSet(new HashSet<>());
    // 本地用户按下（捕捉到并转发出去）的按键，防抖去重
    private final Set<KeyCode> pressedLocally = Collections.synchronizedSet(new HashSet<>());

    // 每个玩家动作到按键映射（用于 ACTION -> KeyEvent）
    private final Map<String, Map<Action, KeyCode>> perPlayerKeyMap = new ConcurrentHashMap<>();

    // 分配到的玩家位（ASSIGN 收到后设置）
    private volatile String assignedId;

    public NetworkClient(String host, int port) {
        this(host, port, null);
    }

    public NetworkClient(String host, int port, String desiredPlayerId) {
        this.host = host;
        this.port = port;
        this.desiredPlayerId = desiredPlayerId;
        initDefaultKeyMaps();
    }

    public String getAssignedId() {
        return assignedId;
    }

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
        p1.put(Action.LIGHT_HIT_DOWN, KeyCode.Q);
        p1.put(Action.HEAVY_HIT_UP, KeyCode.E);
        p1.put(Action.HEAVY_HIT_DOWN, KeyCode.E);

        Map<Action, KeyCode> p2 = new EnumMap<>(Action.class);
        p2.put(Action.MOVE_LEFT_ON, KeyCode.J);
        p2.put(Action.MOVE_LEFT_OFF, KeyCode.J);
        p2.put(Action.MOVE_RIGHT_ON, KeyCode.L);
        p2.put(Action.MOVE_RIGHT_OFF, KeyCode.L);
        p2.put(Action.JUMP, KeyCode.I);
        p2.put(Action.LIGHT_HIT, KeyCode.U);
        p2.put(Action.HEAVY_HIT, KeyCode.O);
        p2.put(Action.LIGHT_HIT_UP, KeyCode.U);
        p2.put(Action.LIGHT_HIT_DOWN, KeyCode.U);
        p2.put(Action.HEAVY_HIT_UP, KeyCode.O);
        p2.put(Action.HEAVY_HIT_DOWN, KeyCode.O);

        perPlayerKeyMap.put("p1", p1);
        perPlayerKeyMap.put("p2", p2);
    }

    public void setPlayerKeyMap(String playerId, Map<Action, KeyCode> map) {
        if (playerId == null || map == null) return;
        perPlayerKeyMap.put(playerId, new EnumMap<>(map));
    }

    // 绑定到某个 JavaFX Scene，把本地键盘事件转发到服务器
    public void attachToScene(Scene scene) {
        if (scene == null) return;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode code = ev.getCode();
            if (code == null) return;
            // 远端注入的按键，不向外转发，避免回环
            if (pressedByClient.contains(code)) return;
            // 本地防抖
            if (pressedLocally.add(code)) {
                sendKeyPress(code);
            }
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
            KeyCode code = ev.getCode();
            if (code == null) return;
            if (pressedByClient.contains(code)) return;
            if (pressedLocally.remove(code)) {
                sendKeyRelease(code);
            }
        });
    }

    // 尝试绑定到 FXGL PrimaryStage 的 Scene
    // 返回是否绑定成功
    public boolean attachToPrimaryScene() {
        Scene s = getScene();
        if (s != null) {
            attachToScene(s);
            return true;
        }
        return false;
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
        System.out.println("[Client] stopped");
    }

    public void sendKeyPress(KeyCode code) {
        if (code == null) return;
        sendLine("KEY:PRESS:" + code.name());
    }

    public void sendKeyRelease(KeyCode code) {
        if (code == null) return;
        sendLine("KEY:RELEASE:" + code.name());
    }

    public void sendAction(Action action) {
        if (action == null) return;
        String pid = assignedId != null ? assignedId : (desiredPlayerId != null ? desiredPlayerId : "p1");
        // 服务器会强制写入真实 assignedId，这里使用当前可知的 id
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
                sleep(1000); // 简单重连
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
            } else if (line.startsWith("ERROR:")) {
                System.out.println("[Client] server error: " + line);
            }
        } catch (Exception e) {
            System.out.println("[Client] handle error: " + e.getMessage());
        }
    }

    private void handleInfoLine(String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) return;

        if ("INFO".equals(parts[0]) && "PLAYER_STATE".equals(parts[1]) && parts.length >= 4) {
            String playerId = parts[2].trim().toLowerCase();
            String state = parts[3].trim().toUpperCase();
            boolean ready = "READY".equals(state) || "1".equals(state) || "CONNECTED".equals(state);
            notifyPlayerState(playerId, ready);
            return;
        }

        if ("INFO".equals(parts[0]) && "PLAYER_LEFT".equals(parts[1]) && parts.length >= 3) {
            String playerId = parts[2].trim().toLowerCase();
            notifyPlayerLeft(playerId);
            notifyPlayerState(playerId, false);
        }
    }

    private void handleKeyLine(String line) {
        // KEY:PRESS:<KEYCODE> 或 KEY:RELEASE:<KEYCODE>
        String[] parts = line.split(":");
        if (parts.length != 3) return;

        String op = parts[1].trim().toUpperCase();
        String keyName = parts[2].trim().toUpperCase();

        final KeyCode code;
        try {
            code = KeyCode.valueOf(keyName);
        } catch (IllegalArgumentException ex) {
            System.out.println("[Client] unknown KeyCode: " + keyName);
            return;
        }

        switch (op) {
            case "PRESS":
                simulateKeyPress(code);
                break;
            case "RELEASE":
                simulateKeyRelease(code);
                break;
            default:
                System.out.println("[Client] unknown KEY op: " + op);
        }
    }

    private void handleActionLine(String line) {
        // ACTION:<playerId>:<COMMAND>
        String[] parts = line.split(":");
        if (parts.length != 3) return;

        String playerId = parts[1].trim(); // "p1" / "p2"
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

        switch (action) {
            case MOVE_LEFT_ON:
            case MOVE_RIGHT_ON:
                simulateKeyPress(code);
                break;

            case MOVE_LEFT_OFF:
            case MOVE_RIGHT_OFF:
                simulateKeyRelease(code);
                break;

            case JUMP:
            case LIGHT_HIT:
            case HEAVY_HIT:
            case LIGHT_HIT_UP:
            case LIGHT_HIT_DOWN:
            case HEAVY_HIT_UP:
            case HEAVY_HIT_DOWN:
                simulateKeyPress(code);
                scheduleRelease(code, 30);
                break;
        }
    }

    private void simulateKeyPress(KeyCode code) {
        if (code == null) return;
        if (pressedByClient.contains(code)) return;
        pressedByClient.add(code);

        Scene scene = getScene();
        if (scene == null) {
            System.out.println("[Client] scene null on PRESS " + code);
            return;
        }

        Platform.runLater(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
            Event.fireEvent(scene, ev);
        });
    }

    private void simulateKeyRelease(KeyCode code) {
        if (code == null) return;
        pressedByClient.remove(code);

        Scene scene = getScene();
        if (scene == null) {
            System.out.println("[Client] scene null on RELEASE " + code);
            return;
        }

        Platform.runLater(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false);
            Event.fireEvent(scene, ev);
        });
    }

    private void flushAllPressedKeys() {
        Scene scene = getScene();
        if (scene == null) {
            pressedByClient.clear();
            return;
        }
        Set<KeyCode> snapshot = new HashSet<>(pressedByClient);
        pressedByClient.clear();
        Platform.runLater(() -> {
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

    private void scheduleRelease(KeyCode code, int delayMs) {
        new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            simulateKeyRelease(code);
        }, "NetworkClient-ReleaseDelay").start();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
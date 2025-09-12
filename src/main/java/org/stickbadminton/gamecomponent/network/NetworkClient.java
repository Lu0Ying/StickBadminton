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

/**
 * 行文本协议客户端，匹配 GameServer：
 * - KEY:PRESS:<KEYCODE>
 * - KEY:RELEASE:<KEYCODE>
 * - ACTION:<playerId>:<COMMAND>
 * - ASSIGN:<playerId>（来自服务器）
 *
 * 特性：
 * - 连接/断线/收包有详细日志，便于排查
 * - 连接成功后自动发送 HELLO:<desiredPlayerId>（如果设置了）
 * - 将 KEY/ACTION 转换为 JavaFX KeyEvent 并注入到 FXGL Scene（Event.fireEvent(scene, ev)）
 */
public class NetworkClient {

    public enum Action {
        MOVE_LEFT_ON, MOVE_LEFT_OFF,
        MOVE_RIGHT_ON, MOVE_RIGHT_OFF,
        JUMP,
        LIGHT_HIT, HEAVY_HIT,
        // 如果你使用细分上下击，可以在服务端发 LIGHT_HIT_UP / _DOWN 等；
        // 未在枚举中的命令会被忽略，不会抛异常。
        LIGHT_HIT_UP, LIGHT_HIT_DOWN,
        HEAVY_HIT_UP, HEAVY_HIT_DOWN
    }

    private final String host;
    private final int port;

    private volatile boolean running;
    private Thread ioThread;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // 避免重复注入 PRESS 的集合
    private final Set<KeyCode> pressedByClient = Collections.synchronizedSet(new HashSet<>());

    // 每个玩家的动作到按键映射
    private final Map<String, Map<Action, KeyCode>> perPlayerKeyMap = new ConcurrentHashMap<>();

    // 期望占用的玩家身份（可选：p1 / p2）
    private final String desiredPlayerId;

    public NetworkClient(String host, int port) {
        this(host, port, null);
    }

    public NetworkClient(String host, int port, String desiredPlayerId) {
        this.host = host;
        this.port = port;
        this.desiredPlayerId = desiredPlayerId;
        initDefaultKeyMaps();
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
        // 如果服务端发送 *_UP/_DOWN，也映射到同一按键，保持兼容
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

    public synchronized void start() {
        if (running) return;
        running = true;
        ioThread = new Thread(this::runLoop, "NetworkClient-IO");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (ioThread != null) {
            try { ioThread.join(1000); } catch (InterruptedException ignored) {}
        }
        flushAllPressedKeys();
        System.out.println("[Client] stopped");
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
                sleep(1000);
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
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        in = null;
        out = null;
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
            } else if (line.startsWith("ERROR:")) {
                System.out.println("[Client] server error: " + line);
            }
        } catch (Exception e) {
            System.out.println("[Client] handle error: " + e.getMessage());
        }
    }

    private void handleKeyLine(String line) {
        // KEY:PRESS:<KEYCODE> / KEY:RELEASE:<KEYCODE>
        String[] parts = line.split(":");
        if (parts.length != 3) return;
        String op = parts[1].trim().toUpperCase();
        String keyName = parts[2].trim().toUpperCase();
        KeyCode code;
        try {
            code = KeyCode.valueOf(keyName);
        } catch (IllegalArgumentException ex) {
            System.out.println("[Client] unknown KeyCode: " + keyName);
            return;
        }

        if ("PRESS".equals(op)) simulateKeyPress(code);
        else if ("RELEASE".equals(op)) simulateKeyRelease(code);
    }

    private void handleActionLine(String line) {
        // ACTION:<playerId>:<COMMAND>
        String[] parts = line.split(":");
        if (parts.length < 3) return;
        String playerId = parts[1].trim();
        String cmd = parts[2].trim().toUpperCase();

        Action action;
        try {
            action = Action.valueOf(cmd);
        } catch (IllegalArgumentException e) {
            // 未知动作直接忽略
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

    // ========== JavaFX KeyEvent 模拟 ==========
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
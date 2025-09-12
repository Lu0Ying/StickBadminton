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
 * 基于行文本协议的客户端：
 * - 接收 KEY:/ACTION: 指令并转换成 JavaFX KeyEvent 注入到 FXGL Scene，
 *   由仓库中的 KeyInput 统一捕获，从而驱动 StickMan 的行为。
 *
 * 默认玩家键位（与仓库 StickMan 逻辑保持一致）：
 * - p1（左侧玩家，side==1）：W/A/D 跳/左/右，Q/E 轻/重击
 * - p2（右侧玩家，side==-1）：I/J/L 跳/左/右，U/O 轻/重击
 */
public class NetworkClient {

    public enum Action {
        MOVE_LEFT_ON, MOVE_LEFT_OFF,
        MOVE_RIGHT_ON, MOVE_RIGHT_OFF,
        JUMP,
        LIGHT_HIT, HEAVY_HIT
    }

    private final String host;
    private final int port;

    private volatile boolean running;
    private Thread ioThread;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // 我们模拟按下的按键集合，避免重复注入 PRESS
    private final Set<KeyCode> pressedByClient = Collections.synchronizedSet(new HashSet<>());

    // 每个玩家的动作到按键映射
    private final Map<String, Map<Action, KeyCode>> perPlayerKeyMap = new ConcurrentHashMap<>();

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
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

        Map<Action, KeyCode> p2 = new EnumMap<>(Action.class);
        p2.put(Action.MOVE_LEFT_ON, KeyCode.J);
        p2.put(Action.MOVE_LEFT_OFF, KeyCode.J);
        p2.put(Action.MOVE_RIGHT_ON, KeyCode.L);
        p2.put(Action.MOVE_RIGHT_OFF, KeyCode.L);
        p2.put(Action.JUMP, KeyCode.I);
        p2.put(Action.LIGHT_HIT, KeyCode.U);
        p2.put(Action.HEAVY_HIT, KeyCode.O);

        perPlayerKeyMap.put("p1", p1);
        perPlayerKeyMap.put("p2", p2);
    }

    /**
     * 自定义某个玩家的键位映射
     */
    public void setPlayerKeyMap(String playerId, Map<Action, KeyCode> map) {
        if (playerId == null || map == null) return;
        perPlayerKeyMap.put(playerId, new EnumMap<>(map));
    }

    /**
     * 启动网络线程并连接服务器
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        ioThread = new Thread(this::runLoop, "NetworkClient-IO");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    /**
     * 停止客户端并释放我们模拟按下的按键
     */
    public synchronized void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (ioThread != null) {
            try { ioThread.join(1000); } catch (InterruptedException ignored) {}
        }
        flushAllPressedKeys();
    }

    /**
     * 发送一行协议到服务器（可选）
     */
    public void sendLine(String line) {
        PrintWriter writer = out;
        if (writer != null) {
            writer.println(line);
            writer.flush();
        }
    }

    private void runLoop() {
        while (running) {
            try {
                connect();
                String line;
                while (running && (line = in.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        handleLine(line);
                    }
                }
            } catch (IOException e) {
                sleep(1000); // 简单重连
            } finally {
                closeSilently();
            }
        }
    }

    private void connect() throws IOException {
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

    // 处理协议行
    private void handleLine(String line) {
        try {
            if (line.startsWith("KEY:")) {
                handleKeyLine(line);
            } else if (line.startsWith("ACTION:")) {
                handleActionLine(line);
            } else if (line.startsWith("ASSIGN:")) {
                // 服务器分配的玩家 ID（如需的话可记录/回显）
                // String assigned = line.substring("ASSIGN:".length()).trim();
            }
        } catch (Exception ignored) {
            // 对异常/不合法数据容错
        }
    }

    private void handleKeyLine(String line) {
        // KEY:PRESS:<KEYCODE> / KEY:RELEASE:<KEYCODE>
        String[] parts = line.split(":");
        if (parts.length != 3) return;
        String op = parts[1].trim().toUpperCase();
        String keyName = parts[2].trim().toUpperCase();
        KeyCode code = KeyCode.valueOf(keyName);

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
            return;
        }

        Map<Action, KeyCode> map = perPlayerKeyMap.get(playerId);
        if (map == null) return;

        KeyCode code = map.get(action);
        if (code == null) return;

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
                // 脉冲型按键：短按后自动释放
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
        if (scene == null) return;
        Platform.runLater(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
            Event.fireEvent(scene, ev);
        });
    }

    private void simulateKeyRelease(KeyCode code) {
        if (code == null) return;
        pressedByClient.remove(code);
        Scene scene = getScene();
        if (scene == null) return;
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
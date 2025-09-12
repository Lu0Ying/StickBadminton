package org.stickbadminton.gamecomponent.network;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通过 TCP 连接 Server，接收行分隔文本指令，并把指令转换为本地键盘事件或动作，驱动 KeyInput/角色行为。
 *
 * 协议（每行一条）:
 *   - KEY:PRESS:<KEYCODE>
 *   - KEY:RELEASE:<KEYCODE>
 *     例如: KEY:PRESS:LEFT   KEY:RELEASE:LEFT
 *
 *   - ACTION:<playerId>:<COMMAND>
 *     COMMAND ∈ {
 *       MOVE_LEFT_ON, MOVE_LEFT_OFF, MOVE_RIGHT_ON, MOVE_RIGHT_OFF,
 *       JUMP, LIGHT_HIT_UP, LIGHT_HIT_DOWN, HEAVY_HIT_UP, HEAVY_HIT_DOWN
 *     }
 *     例如: ACTION:p1:MOVE_LEFT_ON
 *
 * 说明：
 *   - 与仓库里的 KeyInput 完全兼容：本类向 FXGL 的 Scene 注入 KeyEvent（PRESS/RELEASE）来“模拟按键”，
 *     StickMan 若通过 KeyInput 查询按键状态，就能像本地玩家一样被驱动。
 *   - ACTION 指令会映射到具体按键，可用 setActionKeyMapping 调整以匹配你项目中的键位。
 */
public class NetworkClient {

    public enum Action {
        MOVE_LEFT_ON, MOVE_LEFT_OFF,
        MOVE_RIGHT_ON, MOVE_RIGHT_OFF,
        JUMP,
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

    // 已经由本客户端“按下”的按键集合（用于避免重复注入 PRESS）
    private final Set<KeyCode> pressedByClient = Collections.synchronizedSet(new HashSet<>());

    // 动作到按键的映射（用于把 ACTION 转为 KEY 事件）
    private final Map<Action, KeyCode> actionKeyMap = Collections.synchronizedMap(defaultActionKeyMap());

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 启动网络线程并尝试连接。
     */
    public synchronized void start() {
        if (running) return;
        running = true;

        ioThread = new Thread(this::runLoop, "NetworkClient-IO");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    /**
     * 停止网络线程并断开连接，释放所有由本客户端模拟按下的按键。
     */
    public synchronized void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (ioThread != null) {
            try { ioThread.join(1000); } catch (InterruptedException ignored) {}
        }
        // 释放被我们按下但尚未释放的键
        flushAllPressedKeys();
    }

    /**
     * 外部可自定义动作->按键的映射（确保与本地玩家的按键绑定一致）。
     */
    public void setActionKeyMapping(java.util.function.Consumer<Map<Action, KeyCode>> customizer) {
        synchronized (actionKeyMap) {
            customizer.accept(actionKeyMap);
        }
    }

    /**
     * 向服务器发送一行消息（可选）。
     */
    public void sendLine(String line) {
        PrintWriter writer = out;
        if (writer != null) {
            writer.println(line);
            writer.flush();
        }
    }

    // ============== 核心 I/O 循环 ==============

    private void runLoop() {
        while (running) {
            try {
                connect();
                String line;
                while (running && (line = in.readLine()) != null) {
                    final String msg = line.trim();
                    if (!msg.isEmpty()) {
                        dispatch(msg);
                    }
                }
            } catch (IOException e) {
                // 简单重连策略：稍等再试
                sleepSilently(1000);
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

    // ============== 消息分发与执行 ==============

    private void dispatch(String line) {
        // 允许的格式：
        // KEY:PRESS:<KEYCODE>
        // KEY:RELEASE:<KEYCODE>
        // ACTION:<playerId>:<COMMAND>
        try {
            if (line.startsWith("KEY:")) {
                handleKeyCommand(line);
            } else if (line.startsWith("ACTION:")) {
                handleActionCommand(line);
            } else {
                // 兼容极简格式：PRESS LEFT / RELEASE LEFT
                String[] parts = line.split("\\s+");
                if (parts.length == 2 && ("PRESS".equalsIgnoreCase(parts[0]) || "RELEASE".equalsIgnoreCase(parts[0]))) {
                    boolean press = "PRESS".equalsIgnoreCase(parts[0]);
                    KeyCode code = KeyCode.valueOf(parts[1].toUpperCase());
                    if (press) simulateKeyPress(code); else simulateKeyRelease(code);
                }
            }
        } catch (Exception ignored) {
            // 对不合法指令容错
        }
    }

    private void handleKeyCommand(String line) {
        // KEY:PRESS:LEFT
        // KEY:RELEASE:LEFT
        String[] parts = line.split(":");
        if (parts.length != 3) return;
        String op = parts[1].trim().toUpperCase();
        String keyName = parts[2].trim().toUpperCase();
        KeyCode code = KeyCode.valueOf(keyName);
        if ("PRESS".equals(op)) {
            simulateKeyPress(code);
        } else if ("RELEASE".equals(op)) {
            simulateKeyRelease(code);
        }
    }

    private void handleActionCommand(String line) {
        // ACTION:<playerId>:<COMMAND>
        // 示例：ACTION:p1:MOVE_LEFT_ON
        String[] parts = line.split(":");
        if (parts.length < 3) return;
        // String playerId = parts[1].trim(); // 预留：如需区分不同玩家，可在这里使用 playerId
        String cmd = parts[2].trim().toUpperCase();

        Action action;
        try {
            action = Action.valueOf(cmd);
        } catch (IllegalArgumentException e) {
            return;
        }

        KeyCode mapped = actionKeyMap.get(action);
        if (mapped == null) return;

        switch (action) {
            case MOVE_LEFT_ON:
            case MOVE_RIGHT_ON:
                simulateKeyPress(mapped);
                break;
            case MOVE_LEFT_OFF:
            case MOVE_RIGHT_OFF:
                simulateKeyRelease(mapped);
                break;
            case JUMP:
                // 跳跃通常是一次性动作： PRESS 然后短延时 RELEASE
                simulateKeyPress(mapped);
                scheduleOnFXThread(() -> simulateKeyRelease(mapped), 30);
                break;
            case LIGHT_HIT_UP:
            case LIGHT_HIT_DOWN:
            case HEAVY_HIT_UP:
            case HEAVY_HIT_DOWN:
                // 同样视为一次性动作
                simulateKeyPress(mapped);
                scheduleOnFXThread(() -> simulateKeyRelease(mapped), 30);
                break;
        }
    }

    // ============== 键盘事件模拟 ==============

    private void simulateKeyPress(KeyCode code) {
        if (code == null) return;
        if (pressedByClient.contains(code)) return; // 已经按下则不重复注入
        pressedByClient.add(code);
        Scene scene = getSceneOrNull();
        if (scene == null) return;
        Platform.runLater(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
            scene.fireEvent(ev);
        });
    }

    private void simulateKeyRelease(KeyCode code) {
        if (code == null) return;
        if (!pressedByClient.contains(code)) {
            // 不是我们按下的，也允许释放一次以保持一致
        } else {
            pressedByClient.remove(code);
        }
        Scene scene = getSceneOrNull();
        if (scene == null) return;
        Platform.runLater(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false);
            scene.fireEvent(ev);
        });
    }

    private void flushAllPressedKeys() {
        Scene scene = getSceneOrNull();
        if (scene == null) {
            pressedByClient.clear();
            return;
        }
        Set<KeyCode> snapshot = new HashSet<>(pressedByClient);
        pressedByClient.clear();
        Platform.runLater(() -> {
            for (KeyCode code : snapshot) {
                KeyEvent ev = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false);
                scene.fireEvent(ev);
            }
        });
    }

    // ============== 工具方法 ==============

    private static Map<Action, KeyCode> defaultActionKeyMap() {
        Map<Action, KeyCode> m = new EnumMap<>(Action.class);
        // 默认用方向键 + 常见击球键位（请按你的项目实际键位修改或在运行时 setActionKeyMapping）
        m.put(Action.MOVE_LEFT_ON, KeyCode.LEFT);
        m.put(Action.MOVE_LEFT_OFF, KeyCode.LEFT);
        m.put(Action.MOVE_RIGHT_ON, KeyCode.RIGHT);
        m.put(Action.MOVE_RIGHT_OFF, KeyCode.RIGHT);
        m.put(Action.JUMP, KeyCode.UP);
        m.put(Action.LIGHT_HIT_UP, KeyCode.J);
        m.put(Action.LIGHT_HIT_DOWN, KeyCode.K);
        m.put(Action.HEAVY_HIT_UP, KeyCode.U);
        m.put(Action.HEAVY_HIT_DOWN, KeyCode.I);
        return m;
    }

    private Scene getSceneOrNull() {
        try {
            return FXGL.getPrimaryStage().getScene();
        } catch (Exception e) {
            return null;
        }
    }

    private static void scheduleOnFXThread(Runnable r, int delayMillis) {
        // 简易延时释放：用 JavaFX 定时，避免阻塞
        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ignored) {}
                Platform.runLater(r);
            }, "NetworkClient-Delay").start();
        });
    }

    private static void sleepSilently(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
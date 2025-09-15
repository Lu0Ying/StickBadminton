package org.stickbadminton.gamecomponent.network;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.event.Event;
import org.stickbadminton.KeyInput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 重写后的 NetworkClient：
 * - 不在本地推断任何权威状态
 * - 捕获本地物理输入，仅向服务端上报
 * - 仅根据服务端的广播/回显来注入按键和派发连接/游戏事件
 *
 * 为了兼容仓库里的旧调用，保留 start()/sendReady()/sendSelect()/sendStartRequest()/attachToPrimaryStageAuto() 的别名或空实现。
 */
public class NetworkClient {

    // 连接配置
    private final String host;
    private final int port;
    private final String desiredPlayerId; // 可为空，非空时用于请求 p1/p2

    // I/O
    private volatile boolean running = false;
    private Thread ioThread;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // 身份
    private volatile String assignedId; // "p1" | "p2" | "w<seq>"

    // 事件监听（保持原事件接口）
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    // 注入去重：由服务端注入的按键集合，避免被本地物理输入过滤器再次上送造成回显循环
    private final Set<KeyCode> pressedByServer = Collections.synchronizedSet(new HashSet<>());
    // 本地物理按下状态，仅控制首次 PRESS/RELEASE 上送
    private final Set<KeyCode> pressedLocally = Collections.synchronizedSet(new HashSet<>());

    // 记录每个玩家当前“服务端认定”的按下集合，用于处理 KEY_STATE 心跳纠偏
    private final Map<String, Set<KeyCode>> serverPressed = new ConcurrentHashMap<>();

    // 已挂接的 Scene（通过它们注入 KeyEvent）
    private final Set<Scene> attachedScenes = Collections.newSetFromMap(new IdentityHashMap<>());

    // 本地键位白名单（仅用于客户端软约束；服务端仍会进行硬校验）
    private static final EnumSet<KeyCode> P1_KEYS = EnumSet.of(KeyCode.Q, KeyCode.W, KeyCode.E, KeyCode.A, KeyCode.S, KeyCode.D);
    private static final EnumSet<KeyCode> P2_KEYS = EnumSet.of(KeyCode.U, KeyCode.I, KeyCode.O, KeyCode.J, KeyCode.K, KeyCode.L);

    public NetworkClient(String host, int port) {
        this(host, port, null);
    }

    public NetworkClient(String host, int port, String desiredPlayerId) {
        this.host = host;
        this.port = port;
        this.desiredPlayerId = desiredPlayerId;
        serverPressed.put("p1", Collections.synchronizedSet(new HashSet<>()));
        serverPressed.put("p2", Collections.synchronizedSet(new HashSet<>()));
    }

    public String getAssignedId() {
        return assignedId;
    }

    // ============== 对外 API ==============

    public void connect() throws IOException {
        if (running) return;
        running = true;

        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            closeQuietly();
            throw e;
        }

        // HELLO（可带期望身份）
        if (desiredPlayerId != null && !desiredPlayerId.isBlank()) {
            sendLine("HELLO:" + desiredPlayerId.trim());
        } else {
            sendLine("HELLO");
        }

        ioThread = new Thread(this::ioLoop, "NetworkClient-IO");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public void disconnect() {
        running = false;
        closeQuietly();
    }

    public void addConnectionListener(ConnectionListener l) {
        if (l != null) connectionListeners.add(l);
    }

    public void removeConnectionListener(ConnectionListener l) {
        connectionListeners.remove(l);
    }

    /**
     * 绑定一个 Scene：
     * - 本地物理按下/抬起仅上送服务端，并消费事件
     * - 真实注入事件均来自服务端回显或心跳
     */
    public void attachScene(Scene scene) {
        if (scene == null || attachedScenes.contains(scene)) return;
        attachedScenes.add(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!running) return;
            if (isWatcher()) return; // 旁观者不发送任何输入

            final KeyCode code = e.getCode();
            // 服务器注入事件：仅清理标记，允许事件传递
            if (pressedByServer.contains(code)) {
                pressedByServer.remove(code);
                return;
            }

            // 客户端白名单软校验：避免无效/越权按键淹没网络（服务端仍会硬校验）
            if (!isKeyAllowedForSelf(code)) {
                return;
            }

            // 避免重复发送
            if (pressedLocally.add(code)) {
                sendLine("KEY_DOWN:" + code.name());
            }
            // 物理输入不直接作用于本地游戏，由服务端回显为准，故消费事件防止本地与权威状态分叉
            e.consume();
        });

        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (!running) return;
            if (isWatcher()) return;

            final KeyCode code = e.getCode();
            if (pressedByServer.contains(code)) {
                pressedByServer.remove(code);
                return;
            }
            if (!isKeyAllowedForSelf(code)) {
                return;
            }
            if (pressedLocally.remove(code)) {
                sendLine("KEY_UP:" + code.name());
            }
            e.consume();
        });
    }

    // 游戏房间/人的操作，上送服务器，由服务器广播权威结果
    public void setReady(boolean ready) {
        sendLine("READY:" + (ready ? "1" : "0"));
    }

    public void selectCharacter(int characterId) {
        sendLine("SELECT:" + characterId);
    }

    // ============== 兼容旧 API，避免“直接粘贴报错” ==============

    // 旧调用：netClient.start()
    public void start() throws IOException { connect(); }

    // 旧调用：netClient.sendReady(x)
    public void sendReady(boolean ready) { setReady(ready); }

    // 旧调用：netClient.sendSelect(x)
    public void sendSelect(int characterId) { selectCharacter(characterId); }

    // 旧调用：netClient.sendStartRequest() —— 现由服务端自动判定，保持空实现
    public void sendStartRequest() { /* no-op: server auto START when conditions met */ }

    // 旧调用：netClient.attachToPrimaryStageAuto() —— 选择房间不绑定输入，保持空实现
    public void attachToPrimaryStageAuto() { /* no-op */ }

    // ============== 内部实现 ==============

    private void ioLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                handleServerLine(line.trim());
            }
        } catch (SocketException se) {
            // 连接异常/关闭
        } catch (IOException e) {
            System.out.println("[Client] IO error: " + e.getMessage());
        } finally {
            running = false;
            closeQuietly();
            notifyGameStatus("DISCONNECTED");
        }
    }

    private void handleServerLine(String line) {
        if (line.isEmpty()) return;
        // System.out.println("[Client] <= " + line);

        try {
            if (line.startsWith("ASSIGN:")) {
                String id = line.substring("ASSIGN:".length());
                notifyAssigned(id);
                return;
            }
            if (line.startsWith("INFO:PLAYER_STATE:")) {
                // INFO:PLAYER_STATE:<p1|p2>:<READY|WAITING>
                String[] parts = line.split(":", 4);
                if (parts.length >= 4) {
                    String pid = parts[2];
                    boolean present = "READY".equalsIgnoreCase(parts[3]); // 语义：READY 表示占位存在
                    notifyPlayerState(pid, present);
                }
                return;
            }
            if (line.startsWith("PLAYER_LEFT:")) {
                String pid = line.substring("PLAYER_LEFT:".length());
                notifyPlayerLeft(pid);
                return;
            }
            if (line.startsWith("READY:")) {
                // READY:<p1|p2>:<0|1>
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    notifyReady(parts[1], "1".equals(parts[2]));
                }
                return;
            }
            if (line.startsWith("SELECT:")) {
                // SELECT:<p1|p2>:<int>
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    try {
                        notifySelected(parts[1], Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (line.startsWith("START:")) {
                // START:<ct1>:<ct2>
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    try {
                        notifyStart(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (line.startsWith("GAME_STATUS:")) {
                notifyGameStatus(line.substring("GAME_STATUS:".length()));
                return;
            }
            if (line.startsWith("KEY_DOWN:") || line.startsWith("KEY_UP:")) {
                // KEY_DOWN:<p1|p2>:<KEY>
                boolean down = line.startsWith("KEY_DOWN:");
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    String pid = parts[1];
                    KeyCode code = keyCodeSafe(parts[2]);
                    if (code != null) {
                        applyServerKey(pid, code, down);
                    }
                }
                return;
            }
            if (line.startsWith("KEY_STATE:")) {
                // KEY_STATE:<p1|p2>:key1,key2,...
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    String pid = parts[1];
                    Set<KeyCode> newSet = parseKeyList(parts[2]);
                    reconcileKeyState(pid, newSet);
                }
            }
        } catch (Exception ex) {
            System.out.println("[Client] parse error for line: " + line + " -> " + ex.getMessage());
        }
    }

    private void applyServerKey(String pid, KeyCode code, boolean down) {
        // 记录服务器权威集合
        Set<KeyCode> set = serverPressed.computeIfAbsent(pid, k -> Collections.synchronizedSet(new HashSet<>()));
        if (down) set.add(code); else set.remove(code);

        // 注入 FX 事件
        if (down) {
            injectKeyPressed(code);
        } else {
            injectKeyReleased(code);
        }
    }

    private void reconcileKeyState(String pid, Set<KeyCode> newSet) {
        Set<KeyCode> current = serverPressed.computeIfAbsent(pid, k -> Collections.synchronizedSet(new HashSet<>()));
        // 需要按下的补发
        for (KeyCode code : newSet) {
            if (!current.contains(code)) {
                current.add(code);
                injectKeyPressed(code);
            }
        }
        // 需要抬起的补发
        Iterator<KeyCode> it = current.iterator();
        while (it.hasNext()) {
            KeyCode code = it.next();
            if (!newSet.contains(code)) {
                it.remove();
                injectKeyReleased(code);
            }
        }
    }

    private void injectKeyPressed(KeyCode code) {
        runOnFxThreadOrNow(() -> {
            pressedByServer.add(code);
            try {
                // 关键：标记为服务器注入，允许 KeyInput 在联机模式下接收这些事件
                KeyInput.beginServerInjection();
                for (Scene s : attachedScenes) {
                    KeyEvent evt = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
                    Event.fireEvent(s, evt);
                }
            } finally {
                KeyInput.endServerInjection();
            }
        });
    }

    private void injectKeyReleased(KeyCode code) {
        runOnFxThreadOrNow(() -> {
            pressedByServer.add(code);
            try {
                // 关键：标记为服务器注入，允许 KeyInput 在联机模式下接收这些事件
                KeyInput.beginServerInjection();
                for (Scene s : attachedScenes) {
                    KeyEvent evt = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false);
                    Event.fireEvent(s, evt);
                }
            } finally {
                KeyInput.endServerInjection();
            }
        });
    }
//1
    private Set<KeyCode> parseKeyList(String csv) {
        Set<KeyCode> set = Collections.synchronizedSet(new HashSet<>());
        if (csv == null || csv.isBlank()) return set;
        String[] ks = csv.split(",");
        for (String k : ks) {
            KeyCode code = keyCodeSafe(k.trim());
            if (code != null) set.add(code);
        }
        return set;
    }

    private KeyCode keyCodeSafe(String name) {
        try {
            return KeyCode.valueOf(name);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isWatcher() {
        String id = assignedId;
        return id == null || id.startsWith("w");
    }

    private boolean isKeyAllowedForSelf(KeyCode code) {
        if ("p1".equals(assignedId)) return P1_KEYS.contains(code);
        if ("p2".equals(assignedId)) return P2_KEYS.contains(code);
        return false;
    }

    private void sendLine(String s) {
        if (out != null) {
            out.println(s);
            out.flush();
        }
    }

    private void closeQuietly() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        in = null; out = null; socket = null;
        pressedLocally.clear();
        pressedByServer.clear();
        serverPressed.values().forEach(Set::clear);
    }

    private void runOnFxThreadOrNow(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    // ============== 原事件分发保持 ==============

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

    // 若项目中已有该接口定义，可移除此处重复声明
    public interface ConnectionListener {
        void onAssigned(String id);
        void onPlayerState(String id, boolean present);
        void onPlayerLeft(String id);
        void onReadyState(String id, boolean ready);
        void onSelected(String id, int characterId);
        void onStartGame(int ct1, int ct2);
        void onGameStatusChanged(String status);
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
            // 标记为服务器注入，允许 KeyInput 放行这次事件
            try {
                KeyInput.beginServerInjection();
                Event.fireEvent(scene, ev);
            } finally {
                KeyInput.endServerInjection();
            }
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
            try {
                KeyInput.beginServerInjection();
                Event.fireEvent(scene, ev);
            } finally {
                KeyInput.endServerInjection();
            }
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
                try {
                    KeyInput.beginServerInjection();
                    Event.fireEvent(scene, ev);
                } finally {
                    KeyInput.endServerInjection();
                }
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
}
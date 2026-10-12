package org.stickbadminton.network;

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
 * 联机客户端：
 * - 不在本地推断任何权威状态
 * - 捕获本地物理输入，仅向服务端上报
 * - 仅根据服务端的广播/回显来注入按键和派发连接/游戏事件
 * - 接收比赛权威状态（SCORE/SERVE/BALL/HIT）回调，经 ConnectionListener 转发给游戏房间
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
    // G 为调试键：主机按 G 将 p1 切换为满级 AI，需经服务器广播使双端同帧响应
    private static final EnumSet<KeyCode> P1_KEYS = EnumSet.of(KeyCode.Q, KeyCode.W, KeyCode.E, KeyCode.A, KeyCode.S, KeyCode.D, KeyCode.G);
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

        // 带 5 秒连接超时：地址不可达时快速失败，
        // 调用方在后台线程调用，避免同步 TCP 连接阻塞 FX 线程导致界面冻结
        socket = new Socket();
        socket.connect(new java.net.InetSocketAddress(host, port), 5000);
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
     * - 本地物理按下/抬起仅上送服务器，并消耗事件
     * - 真实注入事件均来自服务器回显或心跳
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

            // 客户端白名单软校验：避免无效/越权按键淹没网络（服务器仍会硬校验）
            if (!isKeyAllowedForSelf(code)) {
                return;
            }

            // 避免重复发送
            if (pressedLocally.add(code)) {
                sendLine("KEY_DOWN:" + code.name());
            }
            // 物理输入不直接作用于本地游戏，由服务器回显为准，故消耗事件防止本地与权威状态分叉
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

    // 进入对局后请求服务器重发状态快照（SCORE/SERVE/BALL/按键），
    // 修复“连接建立时快照先于对局监听器挂载”导致中途加入者状态丢失的问题
    public void requestSnapshot() {
        sendLine("SNAPSHOT");
    }

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
            if (line.startsWith("HIT:")) {
                // HIT:<LIGHT|HEAVY>:<angle>:<x>:<y>:<speedX>:<speedY> —— 击球事件，含主机权威初速
                String[] parts = line.split(":");
                if (parts.length == 7) {
                    try {
                        String type = parts[1];
                        double angle = Double.parseDouble(parts[2]);
                        double hitX = Double.parseDouble(parts[3]);
                        double hitY = Double.parseDouble(parts[4]);
                        double speedX = Double.parseDouble(parts[5]);
                        double speedY = Double.parseDouble(parts[6]);
                        notifyHit(type, angle, hitX, hitY, speedX, speedY);
                    } catch (NumberFormatException e) {
                        System.out.println("[Client] Invalid HIT format: " + line);
                    }
                }
                return;
            }
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

            // —— 比赛权威状态回显（p1 上报、服务器转发）—— //
            if (line.startsWith("SCORE:")) {
                // SCORE:<left>:<right>
                String[] parts = line.split(":");
                if (parts.length == 3) {
                    try {
                        notifyScore(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (line.startsWith("SERVE:")) {
                // SERVE:<1|-1>
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        notifyServeSide(Integer.parseInt(parts[1]) >= 0 ? 1 : -1);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (line.startsWith("BALL:")) {
                // BALL:<x>:<y>:<vx>:<vy> —— 主机球状态快照（此前缺此分支导致球同步完全断链）
                String[] parts = line.split(":");
                if (parts.length == 5) {
                    try {
                        notifyBallState(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
                    } catch (NumberFormatException ignored) {}
                }
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

    public void sendLine(String s) {
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

    private void notifyScore(int left, int right) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onScore(left, right); } catch (Exception ignored) {}
        }));
    }

    private void notifyServeSide(int side) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onServeSide(side); } catch (Exception ignored) {}
        }));
    }

    private void notifyBallState(double x, double y, double vx, double vy) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            try { l.onBallState(x, y, vx, vy); } catch (Exception ignored) {}
        }));
    }

    private void notifyHit(String type, double angle, double x, double y, double speedX, double speedY) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> {
            // 不吞异常：重放失败时至少留日志，否则客户端零表现零线索
            try { l.onHit(type, angle, x, y, speedX, speedY); }
            catch (Exception e) { System.out.println("[Client] onHit listener error: " + e); }
        }));
    }

    /**
     * 连接/比赛事件监听。比赛类回调均为 default 空实现，
     * 各房间按需覆写，避免未使用的房间被迫实现全部方法。
     */
    public interface ConnectionListener {
        void onAssigned(String id);
        void onPlayerState(String id, boolean present);
        void onPlayerLeft(String id);
        void onReadyState(String id, boolean ready);
        void onSelected(String id, int characterId);
        void onStartGame(int ct1, int ct2);
        void onGameStatusChanged(String status);

        /** 主机上报的球状态快照，用于客户端应用插值同步 */
        default void onBallState(double x, double y, double speedX, double speedY) {}
        /** 比分状态 l:r，客户端据此驱动本地计分/重置/结束流程 */
        default void onScore(int left, int right) {}
        /** 发球方 1(左)/-1(右) */
        default void onServeSide(int side) {}
        /** 击球表现回显（主机已本地执行，忽略回显；非主机端播放音效/特效） */
        default void onHit(String type, double angle, double x, double y, double speedX, double speedY) {}
    }
}

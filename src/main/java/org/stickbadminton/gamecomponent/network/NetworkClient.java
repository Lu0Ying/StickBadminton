// NetworkClient.java
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    // 记录每个玩家当前"服务端认定"的按下集合，用于处理 KEY_STATE 心跳纠偏
    private final Map<String, Set<KeyCode>> serverPressed = new ConcurrentHashMap<>();

    // 已挂接的 Scene（通过它们注入 KeyEvent）
    private final Set<Scene> attachedScenes = Collections.newSetFromMap(new IdentityHashMap<>());

    // 本地键位白名单（仅用于客户端软约束；服务端仍会进行硬校验）
    private static final EnumSet<KeyCode> P1_KEYS = EnumSet.of(KeyCode.Q, KeyCode.W, KeyCode.E, KeyCode.A, KeyCode.S, KeyCode.D);
    private static final EnumSet<KeyCode> P2_KEYS = EnumSet.of(KeyCode.U, KeyCode.I, KeyCode.O, KeyCode.J, KeyCode.K, KeyCode.L);

    // —— 新增：比赛权威状态监听 —— //
    public interface GameplaySyncListener {
        void onScore(int left, int right);
        void onServeSide(int side);
        void onBall(double x, double y, double vx, double vy, double rotation,
                    boolean isFrozen, boolean isTouchedGround, int touchedTime, boolean isShotable, boolean isHitted);
        void onHit(String playerId, String hitType);
        void onNetCrash();
    }

    private volatile GameplaySyncListener gameplayListener;

    public void setGameplayListener(GameplaySyncListener listener) {
        this.gameplayListener = listener;
    }

    public boolean isAuthority() {
        return "p1".equals(assignedId);
    }

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

    public void attachScene(Scene scene) {
        if (scene == null || attachedScenes.contains(scene)) return;
        attachedScenes.add(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
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

            if (pressedLocally.add(code)) {
                sendLine("KEY_DOWN:" + code.name());
            }
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

    public void setReady(boolean ready) {
        sendLine("READY:" + (ready ? "1" : "0"));
    }

    public void selectCharacter(int characterId) {
        sendLine("SELECT:" + characterId);
    }

    public void sendHit(double angle, boolean isHeavy) {
        sendLine("HIT:" + angle + ":" + (isHeavy ? "HEAVY" : "LIGHT"));
    }

    public void sendServe() {
        sendLine("SERVE");
    }

    private void ioLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                handleServerLine(line.trim());
            }
        } catch (IOException e) {
            System.out.println("[Client] IO error: " + e.getMessage());
        } finally {
            running = false;
            closeQuietly();
            notifyDisconnected();
        }
    }

    private void handleServerLine(String line) {
        if (line.isEmpty()) return;

        try {
            if (line.startsWith("ASSIGN:")) {
                String id = line.substring("ASSIGN:".length());
                notifyAssigned(id);
                return;
            }
            if (line.startsWith("PLAYER_STATE:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    notifyPlayerState(parts[1], "true".equalsIgnoreCase(parts[2]));
                }
                return;
            }
            if (line.startsWith("PLAYER_LEFT:")) {
                notifyPlayerLeft(line.substring("PLAYER_LEFT:".length()));
                return;
            }
            if (line.startsWith("READY:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    notifyReady(parts[1], "1".equals(parts[2]));
                }
                return;
            }
            if (line.startsWith("SELECT:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    notifySelected(parts[1], Integer.parseInt(parts[2]));
                }
                return;
            }
            if (line.startsWith("START:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    notifyStart(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                }
                return;
            }
            if (line.startsWith("GAME_STATUS:")) {
                notifyGameStatus(line.substring("GAME_STATUS:".length()));
                return;
            }
            if (line.startsWith("KEY_DOWN:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    KeyCode code = keyCodeSafe(parts[2]);
                    if (code != null) {
                        injectKeyPressed(code);
                    }
                }
                return;
            }
            if (line.startsWith("KEY_UP:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    KeyCode code = keyCodeSafe(parts[2]);
                    if (code != null) {
                        injectKeyReleased(code);
                    }
                }
                return;
            }
            if (line.startsWith("KEY_STATE:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    String pid = parts[1];
                    Set<KeyCode> newSet = parseKeyList(parts[2]);
                    syncServerKeys(pid, newSet);
                }
                return;
            }
            if (line.startsWith("SCORE:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3 && gameplayListener != null) {
                    int left = Integer.parseInt(parts[1]);
                    int right = Integer.parseInt(parts[2]);
                    runOnFxThreadOrNow(() -> gameplayListener.onScore(left, right));
                }
                return;
            }
            if (line.startsWith("SERVE:")) {
                String sideStr = line.substring("SERVE:".length());
                int side = Integer.parseInt(sideStr);
                if (gameplayListener != null) {
                    runOnFxThreadOrNow(() -> gameplayListener.onServeSide(side));
                }
                return;
            }
            if (line.startsWith("BALL:")) {
                String[] parts = line.split(":");
                if (parts.length >= 11 && gameplayListener != null) {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double vx = Double.parseDouble(parts[3]);
                    double vy = Double.parseDouble(parts[4]);
                    double rotation = Double.parseDouble(parts[5]);
                    boolean frozen = Boolean.parseBoolean(parts[6]);
                    boolean touchedGround = Boolean.parseBoolean(parts[7]);
                    int touchedTime = Integer.parseInt(parts[8]);
                    boolean shotable = Boolean.parseBoolean(parts[9]);
                    boolean hitted = Boolean.parseBoolean(parts[10]);
                    runOnFxThreadOrNow(() -> gameplayListener.onBall(x, y, vx, vy, rotation, frozen, touchedGround, touchedTime, shotable, hitted));
                }
                return;
            }
            if (line.startsWith("HIT:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3 && gameplayListener != null) {
                    String playerId = parts[1];
                    String hitType = parts[2];
                    runOnFxThreadOrNow(() -> gameplayListener.onHit(playerId, hitType));
                }
                return;
            }
            if (line.startsWith("NET_CRASH")) {
                if (gameplayListener != null) {
                    runOnFxThreadOrNow(() -> gameplayListener.onNetCrash());
                }
                return;
            }
        } catch (Exception ex) {
            System.out.println("[Client] parse error: " + line + " -> " + ex.getMessage());
        }
    }

    private void syncServerKeys(String pid, Set<KeyCode> newSet) {
        Set<KeyCode> oldSet = serverPressed.get(pid);
        if (oldSet == null) return;

        runOnFxThreadOrNow(() -> {
            for (KeyCode code : newSet) {
                if (!oldSet.contains(code)) {
                    oldSet.add(code);
                    injectKeyPressed(code);
                }
            }
            Iterator<KeyCode> it = oldSet.iterator();
            while (it.hasNext()) {
                KeyCode code = it.next();
                if (!newSet.contains(code)) {
                    it.remove();
                    injectKeyReleased(code);
                }
            }
        });
    }

    private void injectKeyPressed(KeyCode code) {
        runOnFxThreadOrNow(() -> {
            pressedByServer.add(code);
            try {
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
            pressedByServer.remove(code);
            try {
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
        return assignedId == null || assignedId.startsWith("w");
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

    private void notifyAssigned(String id) {
        this.assignedId = id;
        System.out.println("[Client] Assigned as: " + id);
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onAssigned(id)));
    }

    private void notifyPlayerState(String id, boolean present) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onPlayerState(id, present)));
    }

    private void notifyPlayerLeft(String id) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onPlayerLeft(id)));
    }

    private void notifyReady(String id, boolean ready) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onReadyState(id, ready)));
    }

    private void notifySelected(String id, int characterId) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onSelected(id, characterId)));
    }

    private void notifyStart(int ct1, int ct2) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onStartGame(ct1, ct2)));
    }

    private void notifyGameStatus(String status) {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onGameStatusChanged(status)));
    }

    private void notifyDisconnected() {
        runOnFxThreadOrNow(() -> connectionListeners.forEach(l -> l.onDisconnected()));
    }

    public interface ConnectionListener {
        void onAssigned(String id);
        void onPlayerState(String id, boolean present);
        void onPlayerLeft(String id);
        void onReadyState(String id, boolean ready);
        void onSelected(String id, int characterId);
        void onStartGame(int ct1, int ct2);
        void onGameStatusChanged(String status);
        default void onDisconnected() {}
    }
}
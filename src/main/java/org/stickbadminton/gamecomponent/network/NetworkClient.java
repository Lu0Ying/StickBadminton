// NetworkClient.java
package org.stickbadminton.gamecomponent.network;

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

    private final String host;
    private final int port;
    private final String desiredPlayerId;

    private volatile boolean running = false;
    private Thread ioThread;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private volatile String assignedId;

    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    private final Set<KeyCode> pressedByServer = Collections.synchronizedSet(new HashSet<>());
    private final Set<KeyCode> pressedLocally = Collections.synchronizedSet(new HashSet<>());

    private final Map<String, Set<KeyCode>> serverPressed = new ConcurrentHashMap<>();

    private final Set<Scene> attachedScenes = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final EnumSet<KeyCode> P1_KEYS = EnumSet.of(KeyCode.Q, KeyCode.W, KeyCode.E, KeyCode.A, KeyCode.S, KeyCode.D);
    private static final EnumSet<KeyCode> P2_KEYS = EnumSet.of(KeyCode.U, KeyCode.I, KeyCode.O, KeyCode.J, KeyCode.K, KeyCode.L);

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
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(socket.getOutputStream(), true);

        sendLine(desiredPlayerId != null && !desiredPlayerId.isBlank() ? "HELLO:" + desiredPlayerId.trim() : "HELLO");

        ioThread = new Thread(this::ioLoop);
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
            if (!running || isWatcher()) return;

            KeyCode code = e.getCode();
            if (pressedByServer.contains(code)) {
                pressedByServer.remove(code);
                return;
            }

            if (!isKeyAllowedForSelf(code)) return;

            if (pressedLocally.add(code)) sendLine("KEY_DOWN:" + code.name());
            e.consume();
        });

        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (!running || isWatcher()) return;

            KeyCode code = e.getCode();
            if (pressedByServer.contains(code)) {
                pressedByServer.remove(code);
                return;
            }

            if (!isKeyAllowedForSelf(code)) return;

            if (pressedLocally.remove(code)) sendLine("KEY_UP:" + code.name());
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

    private void ioLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                handleServerLine(line.trim());
            }
        } catch (IOException e) {
            if (running) System.out.println("[Client] IO error: " + e.getMessage());
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
                assignedId = line.substring("ASSIGN:".length());
                notifyAssigned(assignedId);
                return;
            }
            if (line.startsWith("PLAYER_STATE:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) notifyPlayerState(parts[1], "true".equals(parts[2]));
                return;
            }
            if (line.startsWith("PLAYER_LEFT:")) {
                notifyPlayerLeft(line.substring("PLAYER_LEFT:".length()));
                return;
            }
            if (line.startsWith("READY:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) notifyReady(parts[1], "1".equals(parts[2]));
                return;
            }
            if (line.startsWith("SELECT:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) notifySelected(parts[1], Integer.parseInt(parts[2]));
                return;
            }
            if (line.startsWith("START:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) notifyStart(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                return;
            }
            if (line.startsWith("GAME_STATUS:")) {
                notifyGameStatus(line.substring("GAME_STATUS:".length()));
                return;
            }
            if (line.startsWith("KEY_DOWN:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    KeyCode code = KeyCode.valueOf(parts[2]);
                    injectKeyPressed(code);
                }
                return;
            }
            if (line.startsWith("KEY_UP:")) {
                String[] parts = line.split(":");
                if (parts.length >= 3) {
                    KeyCode code = KeyCode.valueOf(parts[2]);
                    injectKeyReleased(code);
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
                int side = Integer.parseInt(line.substring("SERVE:".length()));
                if (gameplayListener != null) runOnFxThreadOrNow(() -> gameplayListener.onServeSide(side));
                return;
            }
            if (line.startsWith("BALL:")) {
                String[] parts = line.split(":");
                if (parts.length >= 10 && gameplayListener != null) {
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
                if (gameplayListener != null) runOnFxThreadOrNow(() -> gameplayListener.onNetCrash());
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
            newSet.forEach(code -> {
                if (!oldSet.contains(code)) {
                    oldSet.add(code);
                    injectKeyPressed(code);
                }
            });
            oldSet.removeIf(code -> {
                if (!newSet.contains(code)) {
                    injectKeyReleased(code);
                    return true;
                }
                return false;
            });
        });
    }

    private void injectKeyPressed(KeyCode code) {
        runOnFxThreadOrNow(() -> {
            pressedByServer.add(code);
            try {
                KeyInput.beginServerInjection();
                attachedScenes.forEach(s -> Event.fireEvent(s, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
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
                attachedScenes.forEach(s -> Event.fireEvent(s, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false)));
            } finally {
                KeyInput.endServerInjection();
            }
        });
    }

    private Set<KeyCode> parseKeyList(String csv) {
        Set<KeyCode> set = Collections.synchronizedSet(new HashSet<>());
        if (csv == null || csv.isBlank()) return set;
        Arrays.stream(csv.split(",")).forEach(k -> {
            try {
                set.add(KeyCode.valueOf(k.trim()));
            } catch (Exception ignored) {}
        });
        return set;
    }

    private boolean isWatcher() {
        return assignedId == null || assignedId.startsWith("w");
    }

    private boolean isKeyAllowedForSelf(KeyCode code) {
        return "p1".equals(assignedId) ? P1_KEYS.contains(code) : "p2".equals(assignedId) ? P2_KEYS.contains(code) : false;
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
        in = null;
        out = null;
        socket = null;
        pressedLocally.clear();
        pressedByServer.clear();
        serverPressed.values().forEach(Set::clear);
    }

    private void runOnFxThreadOrNow(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private void notifyAssigned(String id) {
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
        void onBallState(double x, double y, double speedX, double speedY);

        default void onAssigned(String id) {}
        default void onPlayerState(String id, boolean present) {}
        default void onPlayerLeft(String id) {}
        default void onReadyState(String id, boolean ready) {}
        default void onSelected(String id, int characterId) {}
        default void onStartGame(int ct1, int ct2) {}
        default void onGameStatusChanged(String status) {}
        default void onDisconnected() {}
    }
}
package org.stickbadminton.gamecomponent.network;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;

/**
 * 控制台测试：一进程内启动 Server + 两个 Client（p1/p2）
 * - 无 JavaFX 场景也能验证协议/分配/广播/转发
 */
public class TestNetWork {

    public static void main(String[] args) throws Exception {
        // 可选：初始化 JavaFX Toolkit，避免 Platform.runLater 报 Toolkit not initialized
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}

        int port = 8888;

        GameServer server = new GameServer(port);
        Thread serverThread = new Thread(() -> {
            try { server.start(); } catch (Exception e) { e.printStackTrace(); }
        }, "GameServer-Main");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(300);

        NetworkClient c1 = new NetworkClient("127.0.0.1", port, "p1");
        NetworkClient c2 = new NetworkClient("127.0.0.1", port, "p2");

        c1.addConnectionListener(new NetworkClient.ConnectionListener() {
            @Override public void onAssigned(String playerId) {
                System.out.println("[Test] c1 assigned: " + playerId);
            }
            @Override public void onPlayerState(String playerId, boolean ready) {
                System.out.println("[Test] c1 sees " + playerId + " -> " + (ready ? "READY" : "WAITING"));
            }
        });
        c2.addConnectionListener(new NetworkClient.ConnectionListener() {
            @Override public void onAssigned(String playerId) {
                System.out.println("[Test] c2 assigned: " + playerId);
            }
            @Override public void onPlayerState(String playerId, boolean ready) {
                System.out.println("[Test] c2 sees " + playerId + " -> " + (ready ? "READY" : "WAITING"));
            }
        });

        c1.start();
        c2.start();

        Thread.sleep(800);

        System.out.println("[Test] c1 send ACTION:JUMP");
        c1.sendAction(NetworkClient.Action.JUMP);

        System.out.println("[Test] c1 send KEY A press/release");
        c1.sendKeyPress(KeyCode.A);
        Thread.sleep(100);
        c1.sendKeyRelease(KeyCode.A);

        Thread.sleep(1200);

        System.out.println("[Test] stopping...");
        c1.stop();
        c2.stop();
        server.stop();
        System.out.println("[Test] done.");
    }
}
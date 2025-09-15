package org.stickbadminton.gamecomponent.network;

import javafx.scene.input.KeyCode;

/**
 * 控制台测试：一进程内启动 Server + 两个 Client（p1/p2）
 * - 验证 ASSIGN、PLAYER_STATE 广播
 * - 演示 p1 向服务器发送 KEY 和 ACTION，p2 能收到并尝试注入（若无 Scene 则打印日志）
 *
 * 运行方式：
 * - 直接运行 main 方法
 * - 如需可视化按键注入，请在游戏运行环境中使用 NetworkClient.attachToPrimaryScene()
 */
public class TestNetWork {

    public static void main(String[] args) throws Exception {
        int port = 8888;

        // 1) 启动服务器
        GameServer server = new GameServer(port);
        Thread serverThread = new Thread(() -> {
            try { server.start(); } catch (Exception e) { e.printStackTrace(); }
        }, "GameServer-Main");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(500);

        // 2) 启动两个客户端：p1 / p2
        NetworkClient c1 = new NetworkClient("127.0.0.1", port, "p1");
        NetworkClient c2 = new NetworkClient("127.0.0.1", port, "p2");

        c1.addConnectionListener(new NetworkClient.ConnectionListener() {
            @Override
            public void onAssigned(String playerId) {
                System.out.println("[Test] c1 assigned: " + playerId);
            }
            @Override
            public void onPlayerState(String playerId, boolean ready) {
                System.out.println("[Test] c1 sees " + playerId + " -> " + (ready ? "READY" : "WAITING"));
            }
        });
        c2.addConnectionListener(new NetworkClient.ConnectionListener() {
            @Override
            public void onAssigned(String playerId) {
                System.out.println("[Test] c2 assigned: " + playerId);
            }
            @Override
            public void onPlayerState(String playerId, boolean ready) {
                System.out.println("[Test] c2 sees " + playerId + " -> " + (ready ? "READY" : "WAITING"));
            }
        });

        c1.start();
        c2.start();

        // 等待分配完成和状态广播
        Thread.sleep(1000);

        // 3) p1 发送一个动作和一对按键
        System.out.println("[Test] c1 send ACTION:JUMP");
        c1.sendAction(NetworkClient.Action.JUMP);

        System.out.println("[Test] c1 send KEY A press/release");
        c1.sendKeyPress(KeyCode.A);
        Thread.sleep(100);
        c1.sendKeyRelease(KeyCode.A);

        // 4) 保持一段时间观察日志
        Thread.sleep(1500);

        // 5) 关闭
        System.out.println("[Test] stopping...");
        c1.stop();
        c2.stop();
        server.stop();
        System.out.println("[Test] done.");
    }
}
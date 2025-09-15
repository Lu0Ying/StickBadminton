package org.stickbadminton.gamecomponent;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;
import org.stickbadminton.gamecomponent.network.GameServer;

/**
 * 联机入口：
 * - 创建房间（做主机）：本机启动服务器并进入选人界面
 * - 加入别人房间：输入对方 IP，加入后进入选人界面
 */
public class RoomNetJoin extends Room {

    // UI：初始两个主按钮
    private UIImageButton btnCreateHost;
    private UIImageButton btnJoin;

    // UI：加入模式下显示
    private UITextField ipField;
    private UIImageButton btnJoinConfirm;
    private UIImageButton btnBack;

    // 服务器配置
    private static final int DEFAULT_PORT = 8888;

    // 本机服务器（仅当“创建房间”时启动一次）
    private static volatile GameServer localServer = null;
    private static volatile Thread localServerThread = null;

    public RoomNetJoin() {
        // 背景
        addObject(new GameObject("background", new Image("stickmanselect_background_clear.png"))).setOpacity(0.85);

        // 主按钮：创建房间
        btnCreateHost = new UIImageButton("button_play.png"); // 如无此素材，可替换为现有按钮图片
        addUiObject(btnCreateHost, 360, 200);
        btnCreateHost.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            onCreateHost();
        });

        // 主按钮：加入别人房间
        btnJoin = new UIImageButton("button_2player.png"); // 如无此素材，可替换为现有按钮图片
        addUiObject(btnJoin, 360, 270);
        btnJoin.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            enterJoinMode();
        });

        // 加入模式 UI：默认隐藏
        ipField = new UITextField("输入服务器IP，如 192.168.1.23 或 0.tcp.ngrok.io");
        ipField.setPrefWidth(360);
        addUiObject(ipField, 320, 200);
        ipField.setVisible(false);

    }

    private void onCreateHost() {
        ensureLocalServerStarted(DEFAULT_PORT);
        // 作为主机，直接进入选人界面，客户端连到本机
        goToSelect("127.0.0.1", DEFAULT_PORT);
    }

    private void enterJoinMode() {
        // 隐藏主按钮，显示输入与确认
        btnCreateHost.setVisible(false);
        btnJoin.setVisible(false);

        ipField.setVisible(true);
        btnJoinConfirm.setVisible(true);
        btnBack.setVisible(true);
    }

    private void leaveJoinMode() {
        // 恢复主按钮，隐藏输入与确认
        btnCreateHost.setVisible(true);
        btnJoin.setVisible(true);

        ipField.setVisible(false);
        btnJoinConfirm.setVisible(false);
        btnBack.setVisible(false);
    }

    private void onJoinConfirm() {
        String host = ipField.getText() != null ? ipField.getText().trim() : "";
        if (host.isEmpty()) host = "127.0.0.1";
        goToSelect(host, DEFAULT_PORT);
    }

    private void goToSelect(String host, int port) {
        RoomStickmanSelectNet room = new RoomStickmanSelectNet(host, port);
        new SwitchRoomEffect(this, room);
        Platform.runLater(() ->
                System.out.println("[RoomNetJoin] Join " + host + ":" + port));
    }

    private static void ensureLocalServerStarted(int port) {
        if (localServer != null) return;
        synchronized (RoomNetJoin.class) {
            if (localServer != null) return;
            try {
                localServer = new GameServer(port);
                localServerThread = new Thread(() -> {
                    try {
                        localServer.start();
                    } catch (Exception e) {
                        System.out.println("[RoomNetJoin] start server failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                }, "Local-GameServer");
                localServerThread.setDaemon(true);
                localServerThread.start();
                System.out.println("[RoomNetJoin] Local server started on port " + port);
            } catch (Exception e) {
                System.out.println("[RoomNetJoin] create server failed: " + e.getMessage());
            }
        }
    }
}
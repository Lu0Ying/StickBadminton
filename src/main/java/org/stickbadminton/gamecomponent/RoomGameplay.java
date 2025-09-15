package org.stickbadminton.gamecomponent;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.UIObject;
import org.stickbadminton.gamecomponent.network.NetworkClient;

public class RoomGameplay extends Room {

    private NetworkClient netClient; // 可空：兼容单机
    private boolean inputAttached = false;

    private MatchController matchController; // 引用 MatchController
    private boolean isHost = false; // 是否为主机（p1 是主机）

    public RoomGameplay() {
        buildScene();
    }

    public RoomGameplay(NetworkClient netClient) {
        this.netClient = netClient;
        if (netClient.getAssignedId() != null) {
            isHost = "p1".equalsIgnoreCase(netClient.getAssignedId());
        }
        buildScene();
    }

    @Override
    public void enter() {
        super.enter();
        Platform.runLater(this::attachInputToSceneIfPossible);
    }

    private void attachInputToSceneIfPossible() {
        if (inputAttached) return;

        Scene scene = null;
        try {
            scene = FXGL.getGameScene().getRoot().getScene();
        } catch (Throwable ignored) {}

        if (scene == null) {
            UIObject left = getUiObject("score_left");
            if (left != null && left.getUINode() != null && left.getUINode().getScene() != null) {
                scene = left.getUINode().getScene();
            } else {
                UIObject right = getUiObject("score_right");
                if (right != null && right.getUINode() != null && right.getUINode().getScene() != null) {
                    scene = right.getUINode().getScene();
                }
            }
        }

        if (netClient == null) {
            System.out.println("[RoomGameplay] attachScene failed: netClient is null");
            return;
        }
        if (scene == null) {
            System.out.println("[RoomGameplay] attachScene failed: scene is null");
            return;
        }

        netClient.attachScene(scene);
        // 关键：进入联机对战后，启用“只接收服务器回显”的输入模式
        KeyInput.enableNetworkMode();

        // ========== 关键：添加网络监听 ==========
        setupNetworkListeners();

        inputAttached = true;
        System.out.println("[RoomGameplay] attachScene ok");
    }

    // ========== 新增：设置网络监听 ==========
    private void setupNetworkListeners() {
        netClient.addConnectionListener(new NetworkClient.ConnectionListener() {
            @Override
            public void onBallState(double x, double y, double speedX, double speedY) {
                Platform.runLater(() -> {
                    if (matchController != null) {
                        matchController.applyBallState(x, y, speedX, speedY);
                    }
                });
            }

            @Override
            public void onAssigned(String id) {
                // 可选：记录或日志
                System.out.println("[RoomGameplay] Assigned ID: " + id);
                // 如果需要根据 assignedId 更新 isHost，也可以在这里做
            }

            @Override
            public void onSelected(String playerId, int characterId) {}

            @Override
            public void onReadyState(String playerId, boolean ready) {}

            @Override
            public void onStartGame(int ct1, int ct2) {}

            @Override
            public void onPlayerState(String playerId, boolean present) {}

            @Override
            public void onPlayerLeft(String id) {
                // 可选处理玩家离开
            }

            @Override
            public void onGameStatusChanged(String status) {}
        });
    }

    private void buildScene() {
        SoundPlay.stopBackgroundMusic();
        addObject(new GameObject("background", new Image("ingame_background.png")));
        matchController = new MatchController();
        // 设置是否为主机，用于决定是否广播
        matchController.setHost(isHost);
        // 注入 netClient，用于发送
        matchController.setNetClient(netClient);

        addObject(matchController);

        NetAnimation net = new NetAnimation();
        addObject(net, "net");

        GameObject stickmanRight = addObject(new StickMan(-1, GameProperties.characterType2));
        stickmanRight.setPosition(700, GameProperties.floorY - GameProperties.playerHeight - 11);
        stickmanRight.setName("stickman_right");
        if (GameProperties.matchMode == 1)
            ((StickMan)stickmanRight).isAIControlled = true;

        GameObject stickmanLeft = addObject(new StickMan(1, GameProperties.characterType1));
        stickmanLeft.setPosition(200, GameProperties.floorY - GameProperties.playerHeight - 11);
        stickmanLeft.setName("stickman_left");

        if (!GameProperties.infiniteEnergyMode) {
            addObject(new EnergyDisplay(1)).setPosition(100, 30);
            addObject(new EnergyDisplay(-1)).setPosition(800 - 200, 30);
        }

        UIDigitView digitView = new UIDigitView(0);
        addUiObject(digitView, 390, 21).setName("score_left");
        UIDigitView digitView2 = new UIDigitView(0);
        addUiObject(digitView2, 484, 21).setName("score_right");

        addObject(new HintKeyboard());

        matchController.matchStart();
    }
}
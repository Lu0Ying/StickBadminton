package org.stickbadminton.room;
import org.stickbadminton.Sprite;
import org.stickbadminton.entity.Badminton;
import org.stickbadminton.entity.StickMan;
import org.stickbadminton.fx.NetAnimation;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.logic.MatchController;
import org.stickbadminton.ui.EnergyDisplay;
import org.stickbadminton.ui.HintKeyboard;
import org.stickbadminton.ui.UIDigitView;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;
import org.stickbadminton.UIObject;
import org.stickbadminton.network.NetworkClient;

public class RoomGameplay extends Room {

    private NetworkClient netClient; // 可空：兼容单机
    private boolean inputAttached = false;

    private MatchController matchController; // 引用 MatchController
    private boolean isHost = false; // 是否为主机（p1 是主机）
    // 对手离开只处理一次，防止重复触发房间切换
    private volatile boolean opponentLeftHandled = false;

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
        // 进入对局后请求状态快照，补上连接时快照错过监听器挂载的窗口（中途加入/旁观者）
        netClient.requestSnapshot();

        inputAttached = true;
        System.out.println("[RoomGameplay] attachScene ok");
    }

    // ========== 设置网络监听：比赛状态统一经 ConnectionListener 转发给 MatchController ==========
    private void setupNetworkListeners() {
        netClient.addConnectionListener(new NetworkClient.ConnectionListener() {
            @Override
            public void onBallState(double x, double y, double speedX, double speedY) {
                // 主机发球准备期心跳（50ms）：lerp 平滑跟随；自由飞行不发，靠确定性回放
                if (matchController != null) {
                    matchController.applyBallState(x, y, speedX, speedY);
                }
            }

            @Override
            public void onScore(int left, int right) {
                // 比分状态驱动客户端走与主机相同的计分/重置/结束流程
                if (matchController != null) {
                    matchController.applyRemoteScore(left, right);
                }
            }

            @Override
            public void onServeSide(int side) {
                if (matchController != null) {
                    matchController.applyRemoteServe(side);
                }
            }

            @Override
            public void onHit(String type, double angle, double x, double y) {
                // 击球表现回显：主机已本地执行，忽略回显避免音效/特效双份
                if (isHost || matchController == null) return;
                Badminton ball = (Badminton) getObject("badminton");
                if (ball == null) return;
                // 定位到击球点并重放击球公式，随后按固定逻辑帧与主机同轨回放
                ball.setPositionWithCenter(x, y);
                if ("LIGHT".equals(type)) {
                    ball.lightHit(angle);
                } else if ("HEAVY".equals(type)) {
                    ball.heavyHit(angle);
                }
            }

            @Override
            public void onAssigned(String id) {
                System.out.println("[RoomGameplay] Assigned ID: " + id);
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
                // 对手断线后对局无法继续：防重（PLAYER_LEFT 可能多条）后回主菜单
                if (opponentLeftHandled) return;
                opponentLeftHandled = true;
                Platform.runLater(() -> new SwitchRoomEffect(RoomGameplay.this, new RoomTitle()));
            }

            @Override
            public void onGameStatusChanged(String status) {}
        });
    }

    private void buildScene() {
        SoundPlay.stopBackgroundMusic();
        addObject(new GameObject("background", Sprite.loadImage("/images/scenes/ingame_background.png")));
        matchController = new MatchController();
        // 设置是否为主机，用于决定是否广播
        MatchController.setHost(isHost);
        // 注入 netClient，用于发送
        MatchController.setNetClient(netClient);

        addObject(matchController);

        // 立即创建并重置球（联机客户端的新球由 resetBall 内部标记为网络控制）
        matchController.resetBall();

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
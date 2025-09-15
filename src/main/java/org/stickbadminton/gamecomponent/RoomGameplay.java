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

    public RoomGameplay() {
        buildScene();
    }

    public RoomGameplay(NetworkClient netClient) {
        this.netClient = netClient;
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

        inputAttached = true;
        System.out.println("[RoomGameplay] attachScene ok");
    }

    private void buildScene() {
        SoundPlay.stopBackgroundMusic();
        addObject(new GameObject("background", new Image("ingame_background.png")));
        MatchController matchController = new MatchController();
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
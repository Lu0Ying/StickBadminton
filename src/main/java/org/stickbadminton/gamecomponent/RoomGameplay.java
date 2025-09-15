package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;

public class RoomGameplay extends Room {
    public RoomGameplay() {
        SoundPlay.stopBackgroundMusic();
        //背景
        addObject(new GameObject("background", new Image("ingame_background.png")));
        //控制器
        MatchController matchController = new MatchController();
        addObject(matchController);

        //添加触网
        NetAnimation net = new NetAnimation();
        addObject(net, "net");

        //火柴人
        GameObject stickmanRight = addObject(new StickMan(-1, GameProperties.characterType2));
        stickmanRight.setPosition(700, GameProperties.floorY - GameProperties.playerHeight - 11);
        stickmanRight.setName("stickman_right");
        if (GameProperties.matchMode == 1)
            ((StickMan)stickmanRight).isAIControlled = true; // 单人游戏模式下，连接电脑

        GameObject stickmanLeft = addObject(new StickMan(1, GameProperties.characterType1));
        stickmanLeft.setPosition(200, GameProperties.floorY - GameProperties.playerHeight - 11);
        stickmanLeft.setName("stickman_left");

        //体力
        if (!GameProperties.infiniteEnergyMode) {
            addObject(new EnergyDisplay(1)).setPosition(100, 30);
            addObject(new EnergyDisplay(-1)).setPosition(800 - 200, 30);
        }

        //显示数字，电子显像管风格
        UIDigitView digitView = new UIDigitView(0);
        addUiObject(digitView, 390, 21).setName("score_left");
        UIDigitView digitView2 = new UIDigitView(0);
        addUiObject(digitView2, 484, 21).setName("score_right");

        //按键提示
        addObject(new HintKeyboard());

        matchController.matchStart();
    }
}

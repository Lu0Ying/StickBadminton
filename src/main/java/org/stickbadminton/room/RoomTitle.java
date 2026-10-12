package org.stickbadminton.room;
import org.stickbadminton.Sprite;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.room.RoomNetJoin;
import org.stickbadminton.room.RoomStickmanSelect;
import org.stickbadminton.ui.UIImageButton;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import javafx.scene.image.ImageView;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;
import org.stickbadminton.logic.MatchController;

public class RoomTitle extends Room{
    public RoomTitle() {
        // 回主菜单即视为联机会话结束，统一断网并复位网络静态状态
        MatchController.endSession();
        SoundPlay.setBackgroundMusic("title_bgm.mp3");
        SoundPlay.playBackgroundMusic();

        addObject(new GameObject("title_background", Sprite.loadImage("/images/scenes/title_background.png")));

        UIImageButton button_start = new UIImageButton("images/buttons/button_titlestart.png");
        button_start.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            GameProperties.matchMode = 1;
            RoomStickmanSelect rsr = new RoomStickmanSelect();
            new SwitchRoomEffect(this, rsr);
        });
        addUiObject(button_start, 650, 350);

        UIImageButton button_players = new UIImageButton("images/buttons/button_2player.png");
        button_players.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            GameProperties.matchMode = 2;
            RoomStickmanSelect rsr = new RoomStickmanSelect();
            new SwitchRoomEffect(this, rsr);
        });
        addUiObject(button_players, 650, 420);

        UIImageButton button_netplay = new UIImageButton("images/buttons/button_netplay.png");
        button_netplay.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            GameProperties.matchMode = 2;
            RoomNetJoin rnj = new RoomNetJoin();
            new SwitchRoomEffect(this, rnj);
        });
        addUiObject(button_netplay, 650, 490);
    }
}

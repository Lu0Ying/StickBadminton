package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import javafx.scene.image.ImageView;

public class RoomTitle extends Room{
    public RoomTitle() {
        // 背景: title_background.png
        addObject(new GameObject("title_background", new Image("title_background.png")));
        // 游玩按钮: play.png
        // 双人 / 单人游戏按钮：暂无 自己再整个资源
        // 开始按钮: button_titlestart.png
        // 双人游戏按钮：button_2player.png
        //ui按钮
//        UIImageButton button1 = new UIImageButton("button_start.png");
//        button1.setOnAction(e -> {
//            Room2 room2 = new Room2();
//            this.leave();
//            room2.enter();
//        });
//        addUiObject(button1, 300, 400);
        //900x600 布局
        UIImageButton button_start = new UIImageButton("button_titlestart.png");
        button_start.setOnAction(e -> {
            RoomStickmanSelect rsr = new RoomStickmanSelect();
            this.leave();
            rsr.enter();
        });
        addUiObject(button_start, 650, 400);

        UIImageButton button_players = new UIImageButton("button_2player.png");
        button_players.setOnAction(e -> {
            RoomStickmanSelect rsr = new RoomStickmanSelect();
            this.leave();
            rsr.enter();
        });
        addUiObject(button_players, 650, 470);

        UIImageButton button_net = new UIImageButton("button_netplay.png");
        button_net.setOnAction(e -> {
            RoomStickmanSelect rsr = new RoomStickmanSelect();
            this.leave();
            rsr.enter();
        });
        addUiObject(button_net, 650, 540);
    }
}

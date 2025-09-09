package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;

public class Room1 extends Room {
    public Room1() {
        //背景
        addObject(new GameObject("background", new Image("ingame_background.png")));
        //测试用物体，以后版本移除
        addObject(new Box());

        //ui按钮，需css改样式
        Button button1 = new Button("下一房间");
        button1.setOnAction(e -> {Room2 room2 = new Room2(); this.leave(); room2.enter();});
        addUiNode(button1, 300, 400);
    }
}

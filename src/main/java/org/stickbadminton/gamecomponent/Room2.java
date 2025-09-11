package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;

public class Room2 extends Room{
    public Room2() {
        addObject(new Box()).setPosition(500,500);
        Button button1 = new Button("返回");
        button1.setOnAction(e -> {Room1 room = new Room1(); this.leave(); room.enter();});
        addUiNode(button1, 300, 400);
        Button button2 = new Button("选人界面");
        button2.setOnAction(e -> {RoomStickmanSelect room = new RoomStickmanSelect(); this.leave(); room.enter();});
        addUiNode(button2, 700, 400);
        Button button3 = new Button("标题界面");
        button3.setOnAction(e -> {RoomTitle room = new RoomTitle(); this.leave(); room.enter();});
        addUiNode(button3, 600, 200);
    }
}

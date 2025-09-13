package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import org.stickbadminton.Room;

public class Room2 extends Room{
    public Room2() {
        Button button1 = new Button("返回");
        button1.setOnAction(e -> {
            RoomGameplay room = new RoomGameplay(); this.leave(); room.enter();});
        addUiNode(button1, 300, 400);
        Button button2 = new Button("选人界面");
        button2.setOnAction(e -> {RoomStickmanSelect room = new RoomStickmanSelect(); this.leave(); room.enter();});
        addUiNode(button2, 700, 400);
        Button button3 = new Button("标题界面");
        button3.setOnAction(e -> {RoomTitle room = new RoomTitle(); this.leave(); room.enter();});
        addUiNode(button3, 600, 200);
    }
}

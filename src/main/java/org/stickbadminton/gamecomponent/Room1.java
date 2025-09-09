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
        addObject(new Box());

        Button button1 = new Button("start");
        button1.setOnAction(e -> {addObject(new Box()).setPosition(500, 400);});
        addUiNode(button1, 300, 400);
    }
}

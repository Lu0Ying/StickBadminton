package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;

public class Room1 extends Room {
    public Room1() {
        addObject(new Box()).setX(300);

        Rectangle rectangle = new Rectangle(32,32);
        rectangle.setFill(new Color(0, 0, 0, 0.1));
        addUiNode(rectangle, 300, 400);
    }
}

package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;

import static org.stickbadminton.KeyInput.keys;

public class Box extends GameObject {
    public Box() {
        super("block", new Image("/Block.png"));
        setCenterPosition(16, 16);
    }

    @Override
    public void onUpdate() {
        speedX = speedY = 0;
        if (keys.contains(KeyCode.LEFT)) {
            speedX = -200;
        }
        if (keys.contains(KeyCode.RIGHT)) {
            speedX = 200;
        }
        if (keys.contains(KeyCode.UP)) {
            speedY = -200;
        }
        if (keys.contains(KeyCode.DOWN)) {
            speedY = 200;
        }
    }
}

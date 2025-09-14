package org.stickbadminton.gamecomponent;

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.stickbadminton.GameObject;

public class Hint extends GameObject{
    private double liveTimer = 0;
    public Hint() {
        super("hint", new Image("ingame_hint.png"));
    }
    @Override
    public void onUpdate() {
        liveTimer += GameProperties.frameTime;
        if (liveTimer > 3) {
            if (liveTimer > 4) {
                setOpacity(0.0);
                deactivate();
            }
            else
                setOpacity(1.0 - (liveTimer - 3) * 1.0);
        }
    }
}

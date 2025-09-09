package org.stickbadminton.gamecomponent;

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;

import static org.stickbadminton.KeyInput.keys;

public class StickMan extends GameObject {
    private GameObject bodyIdle;
    private GameObject bodyMoving;
    public double scaleX = 1.0;

    public StickMan() {
        super("stickman1", new Image("stickman_head1.png"));
        bodyIdle = new GameObject("body1", new Image("stickman_body_idle.png"));
        this.entity.setScaleOrigin(new Point2D(10, 0));
        bodyIdle.getEntity().setScaleOrigin(new Point2D(12, 0));
    }

    @Override
    public void onUpdate() {
        if (keys.contains(KeyCode.LEFT)) {
            scaleX = -1;
        }
        else
            scaleX = 1;


        if (scaleX < 0) {
            this.entity.setScaleX(-1);
            bodyIdle.getEntity().setScaleX(-1);
        }
        else {
            this.entity.setScaleX(1);
            bodyIdle.getEntity().setScaleX(1);
        }
        bodyIdle.setX(x - 2 - 6 * scaleX);
        bodyIdle.setY(y + 20);
    }

    @Override
    public void activate() {
        super.activate();
        bodyIdle.activate();
    }
}

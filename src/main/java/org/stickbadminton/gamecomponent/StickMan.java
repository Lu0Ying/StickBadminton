package org.stickbadminton.gamecomponent;

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;

import static org.stickbadminton.KeyInput.keys;

public class StickMan extends GameObject {
    private GameObject bodyIdle;
    private GameObject bodyMoving;
    private GameObject rightHand;
    private GameObject leftHandIdle;
    public double scaleX = 1.0;

    public StickMan() {
        super("stickman1", new Image("stickman_head1.png")); //head
        this.entity.setScaleOrigin(new Point2D(10, 0));

        bodyIdle = new GameObject("body1", new Image("stickman_body_idle.png"));
        bodyIdle.getEntity().setScaleOrigin(new Point2D(12, 0));

        rightHand = new GameObject("right_hand", new Image("stickman_righthand.png"));
        rightHand.setCenterPosition(1, 1);

        leftHandIdle = new GameObject("left_hand", new Image("stickman_lefthand_idle.png"));
        leftHandIdle.setCenterPosition(1, 1);
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
            rightHand.getEntity().setScaleX(-1);
            leftHandIdle.getEntity().setScaleX(-1);
        }
        else {
            this.entity.setScaleX(1);
            bodyIdle.getEntity().setScaleX(1);
            rightHand.getEntity().setScaleX(1);
            leftHandIdle.getEntity().setScaleX(1);

        }

        bodyIdle.setX(x - 2 - 6 * scaleX);
        bodyIdle.setY(y + 20);

        rightHand.setX(x + 9 - 3 * scaleX);
        rightHand.setY(y + 25);
        rightHand.setRotation(90 * scaleX);

        leftHandIdle.setX(x + 9 - 3 * scaleX);
        leftHandIdle.setY(y + 25);
    }

    @Override
    public void activate() {
        super.activate();
        bodyIdle.activate();
        rightHand.activate();
        leftHandIdle.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        bodyIdle.deactivate();
        rightHand.deactivate();
        leftHandIdle.deactivate();
    }
}

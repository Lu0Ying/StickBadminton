package org.stickbadminton.gamecomponent;

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.stickbadminton.GameObject;

import static org.stickbadminton.KeyInput.keys;

public class StickMan extends GameObject {
    private GameObject bodyIdle;
    private GameObject bodyMoving;
    private GameObject rightHand;
    private GameObject leftHandIdle;
    private int side = 1; // 1->Right -1->Left
    int getSide() { return side; }

    public boolean isMoving = false;
    public boolean isJumping = false;
    private int jumpCooldownTimer = 0;
    public boolean isShotting = false;
    private int shotCooldownTimer = 0;

    public StickMan(int side) {
        super("stickman1", new Image("stickman_head1.png")); //head

        this.side = side;

        setCenterPosition(10, 10);

        bodyIdle = new GameObject("bodyIdle", new Image("stickman_body_idle.png"));
        bodyIdle.getEntity().setScaleOrigin(new Point2D(12, 0));
        bodyMoving = new GameObject("bodyMoving", new Image("stickman_body_moving.png"), 32, Duration.seconds(0.2));
        bodyMoving.getEntity().setScaleOrigin(new Point2D(16, 0));

        rightHand = new GameObject("right_hand", new Image("stickman_righthand.png"));
        rightHand.setCenterPosition(1, 1);

        leftHandIdle = new GameObject("left_hand", new Image("stickman_lefthand_idle.png"));
        leftHandIdle.setCenterPosition(1, 1);
    }

    private void bindBodyPart() { //显示层面
        if (side < 0) {
            this.entity.setScaleX(-1);
            bodyIdle.getEntity().setScaleX(-1);
            rightHand.getEntity().setScaleX(-1);
            leftHandIdle.getEntity().setScaleX(-1);
            bodyMoving.getEntity().setScaleX(-1);
        }
        else {
            this.entity.setScaleX(1);
            bodyIdle.getEntity().setScaleX(1);
            rightHand.getEntity().setScaleX(1);
            leftHandIdle.getEntity().setScaleX(1);
            bodyMoving.getEntity().setScaleX(1);
        }

        bodyIdle.setX(x - 2 - 6 * side);
        bodyIdle.setY(y + 20);
        bodyMoving.setX(x - 6 - 8 * side);
        bodyMoving.setY(y + 20);

        rightHand.setX(x + 9 - 3 * side);
        rightHand.setY(y + 25);
        rightHand.setRotation(90 * side);

        leftHandIdle.setX(x + 9 - 3 * side);
        leftHandIdle.setY(y + 25);

        if (isMoving) {
            bodyIdle.setVisible(false);
            bodyMoving.setVisible(true);
        }
        else {
            bodyIdle.setVisible(true);
            bodyMoving.setVisible(false);
        }
    }

    @Override
    public void onUpdate() {
        isMoving = false;
        if (keys.contains(KeyCode.LEFT)) {
            isMoving = true;
        }


        bindBodyPart();
    }

    @Override
    public void activate() {
        super.activate();
        bodyIdle.activate();
        bodyMoving.activate();
        rightHand.activate();
        leftHandIdle.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        bodyIdle.deactivate();
        bodyMoving.deactivate();
        rightHand.deactivate();
        leftHandIdle.deactivate();
    }
}

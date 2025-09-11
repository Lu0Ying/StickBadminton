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
    private double jumpCooldownTimer = 0;
    public boolean isShotting = false;
    public boolean shotType = false; // false -> 上方击球, true -> 下方击球
    private double shotCooldownTimer = 0;
    public boolean isReadyingKickOff = false;
    private double kickedOffTimer = 0;

    public StickMan(int side) {
        super("stickman1", new Image("stickman_head1.png")); //head
        this.side = side;

        setCenterPosition(10, 10);

        bodyIdle = new GameObject("bodyIdle", new Image("stickman_body_idle.png"));
        bodyIdle.getEntity().setScaleOrigin(new Point2D(12, 0));
        bodyMoving = new GameObject("bodyMoving", new Image("stickman_body_moving.png"), 32, Duration.seconds(0.15));
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
        bodyIdle.speedX = speedX;
        bodyIdle.speedY = speedY;
        bodyMoving.setX(x - 6 - 8 * side);
        bodyMoving.setY(y + 20);
        bodyMoving.speedX = speedX;
        bodyMoving.speedY = speedY;

        rightHand.setX(x + 9 - 3 * side);
        rightHand.setY(y + 25);
        if (isReadyingKickOff)
            rightHand.setRotation(90 * side);
        else if (isShotting) {
            if (shotType == false) { // 上方击球
                double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                double nowAngle;
                if (deltaT < GameProperties.shotAnimationTime)
                    nowAngle = 180 + deltaT * (GameProperties.hitAreaAngleUp / GameProperties.shotAnimationTime);
                else if (deltaT < 2 * GameProperties.shotAnimationTime)
                    nowAngle = 180 + 90 - (deltaT - GameProperties.shotAnimationTime)
                            * (GameProperties.hitAreaAngleUp / GameProperties.shotAnimationTime);
                else nowAngle = 180;
                rightHand.setRotation(nowAngle * side);
            }
            else { // 下方击球
                double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                double nowAngle;
                if (deltaT < GameProperties.shotAnimationTime2)
                    nowAngle = 180 - deltaT * (135 + GameProperties.hitAreaAngleDown / GameProperties.shotAnimationTime2);
                else if (deltaT < 2 * GameProperties.shotAnimationTime2)
                    nowAngle = 180 - 135 - GameProperties.hitAreaAngleDown + (deltaT - GameProperties.shotAnimationTime2)
                            * (GameProperties.hitAreaAngleUp / GameProperties.shotAnimationTime2);
                else nowAngle = 180;
                rightHand.setRotation(nowAngle * side);
            }
        }
        else
            rightHand.setRotation(180 * side);
        rightHand.speedX = speedX;
        rightHand.speedY = speedY;

        leftHandIdle.setX(x + 9 - 3 * side);
        leftHandIdle.setY(y + 25);
        leftHandIdle.speedX = speedX;
        leftHandIdle.speedY = speedY;

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
        // 水平移动相关
        isMoving = false;
        speedX = 0;
        if ((keys.contains(KeyCode.D) && side == 1) || (keys.contains(KeyCode.RIGHT) && side == -1)) {
            if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                isMoving = true;
            speedX = GameProperties.moveSpeed;
        } else if ((keys.contains(KeyCode.A) && side == 1) || (keys.contains(KeyCode.LEFT) && side == -1)) {
            if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                isMoving = true;
            speedX = -GameProperties.moveSpeed;
        }
        if (side == 1) { // 左半场
            if (speedX < 0 && getCenterX() + speedX * GameProperties.frameTime <= GameProperties.playFieldLeft + GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.playFieldLeft + GameProperties.playerWidth/2 - spriteCenterX;
            }
            else if (speedX > 0 && getCenterX() + speedX * GameProperties.frameTime >= GameProperties.netPosition - GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.netPosition - GameProperties.playerWidth/2 - spriteCenterX;
            }
        }
        else { // 右半场
            if (speedX < 0 && getCenterX() + speedX * GameProperties.frameTime <= GameProperties.netPosition + GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.netPosition + GameProperties.playerWidth/2 - spriteCenterX;
            }
            else if (speedX > 0 && getCenterX() + speedX * GameProperties.frameTime >= GameProperties.playFieldRight - GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.playFieldRight - GameProperties.playerWidth/2 - spriteCenterX;
            }
        }

        // 竖直移动相关
        if (isJumping) {
            jumpCooldownTimer -= GameProperties.frameTime;
            if (jumpCooldownTimer <= 0) {
                isJumping = false;
                jumpCooldownTimer = 0;
            }
        }
        else if ((keys.contains(KeyCode.W) && side == 1) || (keys.contains(KeyCode.UP) && side == -1)) {
            isJumping = true;
            jumpCooldownTimer = GameProperties.jumpCooldown;
            speedY = -GameProperties.jumpSpeedY;
        }

        if (getCenterY() + GameProperties.playerHeight + speedY * GameProperties.frameTime >= GameProperties.floorY) {
            speedY = 0;
            y = GameProperties.floorY - GameProperties.playerHeight - spriteCenterY;
        }
        else
            speedY += GameProperties.jumpGravity;

        // 击球相关
        if (isShotting) {
            shotCooldownTimer -= GameProperties.frameTime;
            if (shotCooldownTimer <= 0) {
                isShotting = false;
                shotCooldownTimer = 0;
            }
        }
        else if ((keys.contains(KeyCode.S) && side == 1) || (keys.contains(KeyCode.DOWN) && side == -1)) {
            isShotting = true;
            shotCooldownTimer = GameProperties.shotCooldown;
            shotType = false;
        }

        // 身体部件和头绑定
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

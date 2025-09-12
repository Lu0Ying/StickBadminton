package org.stickbadminton.gamecomponent;

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;

public class StickMan extends GameObject {
    public int characterType = 0; // 1 ~ 5
    public int isAIControlled = 0;
    private GameObject bodyIdle;
    private GameObject bodyMoving;
    private GameObject rightHand;
    private GameObject leftHandIdle;
    private int side; // 1->Right -1->Left
    public static final int sideLeft = -1;
    public static final int sideRight = 1;
    int getSide() { return side; }

    public boolean isMoving = false;
    public boolean isJumping = false;
    private double jumpCooldownTimer = 0;
    public boolean isShotting = false;
    public boolean shotType = false; // false -> 上方击球, true -> 下方击球
    public boolean isHeavyShot = false;
    public boolean hasShotted = false; // 自本次击球动画开始以来是否打过球
    public static final boolean shotTypeUp = false;
    public static final boolean shotTypeDown = true;
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
            if (shotType == shotTypeUp) { // 上方击球
                double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                double targetAngle = 180 + 45 + GameProperties.hitAreaAngleUp;
                double nowAngle;
                if (deltaT < GameProperties.shotAnimationTime)
                    nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime);
                else if (deltaT < 2 * GameProperties.shotAnimationTime)
                    nowAngle = targetAngle - (deltaT - GameProperties.shotAnimationTime)
                            * ((targetAngle - 180) / GameProperties.shotAnimationTime);
                else nowAngle = 180;
                rightHand.setRotation(nowAngle * side);
            }
            else { // 下方击球
                double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                double targetAngle = 180 - 145 - GameProperties.hitAreaAngleDown;
                double nowAngle;
                if (deltaT < GameProperties.shotAnimationTime2)
                    nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime2);
                else if (deltaT < 2 * GameProperties.shotAnimationTime2)
                    nowAngle = (deltaT - GameProperties.shotAnimationTime2)
                            * ((targetAngle - 180) / GameProperties.shotAnimationTime2);
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
        if ((KeyInput.isKeyHolding(KeyCode.D) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.L) && side == sideLeft)) {
            if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                isMoving = true;
            speedX = GameProperties.moveSpeed;
        } else if ((KeyInput.isKeyHolding(KeyCode.A) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.J) && side == sideLeft)) {
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
        else if ((KeyInput.isKeyHolding(KeyCode.W) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.I) && side == sideLeft)) {
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
        if (isShotting) { // 击球动画中
            shotCooldownTimer -= GameProperties.frameTime;
            if (shotCooldownTimer <= 0) {
                isShotting = false;
                shotCooldownTimer = 0;
            }
            else if (hasShotted == false){
                if (shotType == shotTypeUp) { // 上方击球
                    double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                    if (deltaT < GameProperties.shotAnimationTime)
                    {
                        double targetAngle = 180 + 45 + GameProperties.hitAreaAngleUp;
                        double nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime);
                        nowAngle = nowAngle + 45;
                        if (side == -1)
                            nowAngle = 180 - nowAngle;
                        GameObject badmintonObj = inRoom.getObject("badminton");
                        if (badmintonObj != null) {
                            Badminton badminton = (Badminton) badmintonObj;
                            double racketX = getCenterX() + Math.cos(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                            double racketY = getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight
                                    + Math.sin(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                            if (Math.pow(badminton.getX() - racketX, 2) + Math.pow(badminton.getY() - racketY, 2)
                                    <= Math.pow(GameProperties.racketRadius, 2)) {
                                // 判定为打到球
                                if (isHeavyShot)
                                    badminton.heavyHit(nowAngle + (side == sideRight ? 90 - 20 : -90 + 20));
                                else
                                    badminton.lightHit(nowAngle + (side == sideRight ? 90 - 20 : -90 + 20));
                                hasShotted = true;
                            }
                        }
                    }
                }
                else { // 下方击球
                    double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                    if (deltaT < GameProperties.shotAnimationTime2) {
                        double targetAngle = 180 - 145 - GameProperties.hitAreaAngleDown;
                        double nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime2);
                        nowAngle = nowAngle + 45;
                        if (side == -1)
                            nowAngle = 180 - nowAngle;
                        while (nowAngle < 0)
                            nowAngle += 360;
                        while (nowAngle > 360)
                            nowAngle -= 360;
                        if ((side == sideRight && nowAngle < 110)||(side == sideLeft && nowAngle > 70)) {
                            GameObject badmintonObj = inRoom.getObject("badminton");
                            if (badmintonObj != null) {
                                Badminton badminton = (Badminton) badmintonObj;
                                double racketX = getCenterX() + Math.cos(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                                double racketY = getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight
                                        + Math.sin(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                                if (Math.pow(badminton.getX() - racketX, 2) + Math.pow(badminton.getY() - racketY, 2)
                                        <= Math.pow(GameProperties.racketRadius, 2)) {
                                    double ballAngle = nowAngle + (side == sideRight ?  - 90 - 30 : 90 + 30);
                                    if (side == sideRight && nowAngle > 260 && nowAngle < 300)
                                        ballAngle = 300;
                                    if (side == sideLeft && nowAngle > 240)
                                        ballAngle = 240;
                                    if (isHeavyShot)
                                        badminton.heavyHit(ballAngle);
                                    else
                                        badminton.lightHit(ballAngle);
                                    hasShotted = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        else if ((KeyInput.isKeyHolding(KeyCode.Q) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.U) && side == sideLeft)
            || (KeyInput.isKeyHolding(KeyCode.E) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.O) && side == sideLeft)) {
            isShotting = true;
            hasShotted = false;
            if ((KeyInput.isKeyHolding(KeyCode.Q) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.U) && side == sideLeft))
                isHeavyShot = false;
            else if ((KeyInput.isKeyHolding(KeyCode.E) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.O) && side == sideLeft))
                isHeavyShot = true;
            shotCooldownTimer = GameProperties.shotCooldown;
            shotType = shotTypeUp;
            GameObject badminton = inRoom.getObject("badminton");
            if (badminton != null) {
                if ((badminton.getCenterX() - 450) * side < 0 // 轮到本方击球
                        && badminton.getCenterY() > getCenterY())
                    shotType = shotTypeDown;
                else
                    shotType = shotTypeUp;
            }
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
        bodyIdle.deactivate();
        bodyMoving.deactivate();
        rightHand.deactivate();
        leftHandIdle.deactivate();
        super.deactivate();
    }
}

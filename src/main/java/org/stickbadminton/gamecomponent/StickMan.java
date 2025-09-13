package org.stickbadminton.gamecomponent;

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;

public class StickMan extends GameObject {
    public int characterType = 0; // 1 ~ 5
    public boolean isAIControlled = false;
    private GameObject bodyIdle;
    private GameObject bodyMoving;
    private GameObject rightHand;
    private GameObject leftHandIdle;
    private GameObject decoration;
    private int side; // 1->（画面左侧）朝向右边; -1->（画面右侧）朝向左边
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
    public boolean isReadyingServe = false;

    public StickMan(int side, int characterType) {
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

        this.characterType = characterType;
        decoration = new GameObject("decoration", new Image("stickman_decoration" + characterType + ".png"));
        decoration.getEntity().setScaleOrigin(new Point2D(52, 0));
    }

    @Override
    public void activate() {
        super.activate();
        bodyIdle.activate();
        bodyMoving.activate();
        rightHand.activate();
        leftHandIdle.activate();
        decoration.activate();
    }

    @Override
    public void deactivate() {
        bodyIdle.deactivate();
        bodyMoving.deactivate();
        rightHand.deactivate();
        leftHandIdle.deactivate();
        decoration.deactivate();
        super.deactivate();
    }

    private void bindBodyPart() { //显示层面
        if (side < 0) {
            this.entity.setScaleX(-1);
            bodyIdle.getEntity().setScaleX(-1);
            rightHand.getEntity().setScaleX(-1);
            leftHandIdle.getEntity().setScaleX(-1);
            bodyMoving.getEntity().setScaleX(-1);
            decoration.getEntity().setScaleX(-0.66);
            decoration.getEntity().setScaleY(0.66);
        }
        else {
            this.entity.setScaleX(1);
            bodyIdle.getEntity().setScaleX(1);
            rightHand.getEntity().setScaleX(1);
            leftHandIdle.getEntity().setScaleX(1);
            bodyMoving.getEntity().setScaleX(1);
            decoration.getEntity().setScaleX(0.66);
            decoration.getEntity().setScaleY(0.66);
        }

        bodyIdle.setX(x - 2 - 6 * side);
        bodyIdle.setY(y + 20);
        bodyIdle.speedX = speedX;
        bodyIdle.speedY = speedY;
        bodyMoving.setX(x - 6 - 8 * side);
        bodyMoving.setY(y + 20);
        bodyMoving.speedX = speedX;
        bodyMoving.speedY = speedY;
        decoration.setX(x - 42 - 2 * side);
        decoration.setY(y - 22);
        decoration.speedX = speedX;
        decoration.speedY = speedY;

        rightHand.setX(x + 9 - 3 * side);
        rightHand.setY(y + 25);
        if (isShotting) {
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
        isMoving = false;
        speedX = 0;
        GameObject badmintonObj = inRoom.getObject("badminton");
        Badminton badminton = null;
        if (badmintonObj != null) {
            badminton = (Badminton) badmintonObj;
        }
        GameObject opponentObj = null;
        if (side == sideLeft) { // 右半场
            opponentObj = inRoom.getObject("stickman_left");
        }
        else { // 左半场
            opponentObj = inRoom.getObject("stickman_right");
        }
        ComputerDecision decision;
        if (isAIControlled && badminton != null && opponentObj != null) {
            decision = new ComputerDecision(side, getCenterX(), getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight,
                    isShotting, isJumping, badminton.getCenterX(), badminton.getCenterY(), badminton.speedX, badminton.speedY, opponentObj.getCenterX(), opponentObj.getCenterY());
        }
        else decision = new ComputerDecision();

        // 水平移动相关
        if (isAIControlled) {
            if (decision.isMoveRight == true) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = GameProperties.moveSpeed;
            } else if (decision.isMoveLeft == true) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = -GameProperties.moveSpeed;
            }
        }
        else {
            if ((KeyInput.isKeyHolding(KeyCode.D) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.L) && side == sideLeft)) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = GameProperties.moveSpeed;
            } else if ((KeyInput.isKeyHolding(KeyCode.A) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.J) && side == sideLeft)) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = -GameProperties.moveSpeed;
            }
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

        if (isReadyingServe) {
            if (side == 1 && x + speedX * GameProperties.frameTime
                    >= GameProperties.netPosition - GameProperties.serveLineDistance - GameProperties.playerWidth/2 - spriteCenterX) { // 左半场
                x = GameProperties.netPosition - GameProperties.serveLineDistance - GameProperties.playerWidth/2 - spriteCenterX;
                speedX = 0;
            }
            else if (side == -1 && x + speedX * GameProperties.frameTime
                    <= GameProperties.netPosition + GameProperties.serveLineDistance - 10 + GameProperties.playerWidth/2 - spriteCenterX){ // 右半场
                x = GameProperties.netPosition + GameProperties.serveLineDistance - 10 + GameProperties.playerWidth/2 - spriteCenterX;
                speedX = 0;
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
        else if (isReadyingServe) {
            // 发球时禁用跳跃
            // do nothing
        }
        else if (isAIControlled) {
            if (decision.isJump == true) {
                isJumping = true;
                jumpCooldownTimer = GameProperties.jumpCooldown;
                speedY = -GameProperties.jumpSpeedY;
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
            else if (hasShotted == false && badminton.isShotable){
                if (shotType == shotTypeUp) { // 上方击球
                    double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                    if (deltaT < GameProperties.shotAnimationTime)
                    {
                        double targetAngle = 180 + 45 + GameProperties.hitAreaAngleUp;
                        double nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime);
                        nowAngle = nowAngle + 45;
                        if (side == -1)
                            nowAngle = 180 - nowAngle;
                        while (nowAngle < 0)
                            nowAngle += 360;
                        while (nowAngle > 360)
                            nowAngle -= 360;
                        if (badmintonObj != null) {
                            double racketX = getCenterX() + Math.cos(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                            double racketY = getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight
                                    + Math.sin(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                            if (Math.pow(badminton.getX() - racketX, 2) + Math.pow(badminton.getY() - racketY, 2)
                                    <= Math.pow(GameProperties.racketRadius, 2)) {
                                // 判定为打到球
                                double hitAngle = nowAngle;
                                if (side == sideRight) {
                                    hitAngle += 90;
                                    if (hitAngle > 360)
                                        hitAngle -= 360;
                                    if (hitAngle > 340 && getCenterY() > GameProperties.floorY - GameProperties.netHeight - 60)
                                        hitAngle = 340;
                                }
                                else {
                                    hitAngle -= 90;
                                    if (hitAngle < 0)
                                        hitAngle += 360;
                                    if (hitAngle > 180 && hitAngle < 200 && getCenterY() > GameProperties.floorY - GameProperties.netHeight - 60)
                                        hitAngle = 200;
                                }
                                if (isHeavyShot)
                                    badminton.heavyHit(hitAngle);
                                else
                                    badminton.lightHit(hitAngle);
                                hasShotted = true;
                                isReadyingServe = false;
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
                            if (badmintonObj != null) {
                                double racketX = getCenterX() + Math.cos(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                                double racketY = getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight
                                        + Math.sin(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                                if (Math.pow(badminton.getX() - racketX, 2) + Math.pow(badminton.getY() - racketY, 2)
                                        <= Math.pow(GameProperties.racketRadius, 2)) {
                                    double hitAngle = nowAngle;
                                    if (side == sideRight) {
                                        hitAngle -= 90;
                                        if (hitAngle < 0)
                                            hitAngle += 360;
                                        if (hitAngle < 90 || hitAngle > 320)
                                            hitAngle = 320;
                                        else if (hitAngle < 290)
                                            hitAngle = 290;
                                    }
                                    else {
                                        hitAngle += 90;
                                        if (hitAngle > 360)
                                            hitAngle -= 360;
                                        if (hitAngle < 220)
                                            hitAngle = 220;
                                        else if (hitAngle > 250)
                                            hitAngle = 250;
                                    }
                                    if (isHeavyShot)
                                        badminton.heavyHit(hitAngle);
                                    else
                                        badminton.lightHit(hitAngle);
                                    hasShotted = true;
                                    isReadyingServe = false;
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            if (isAIControlled) {
                if (decision.isShot == true) {
                    isShotting = true;
                    hasShotted = false;
                    isHeavyShot = decision.isHeavyhit;
                    shotCooldownTimer = GameProperties.shotCooldown;
                    shotType = shotTypeUp;
                    if (badminton != null) {
                        if ((badminton.getCenterX() - 450) * side < 0 // 轮到本方击球
                                && badminton.getCenterY() > getCenterY() - 30)
                            shotType = shotTypeDown;
                        else
                            shotType = shotTypeUp;
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
                if (badminton != null) {
                    if ((badminton.getCenterX() - 450) * side < 0 // 轮到本方击球
                            && badminton.getCenterY() > getCenterY() - 30)
                        shotType = shotTypeDown;
                    else
                        shotType = shotTypeUp;
                }
            }
        }

        // 身体部件和头绑定
        bindBodyPart();
    }
}

package org.stickbadminton.entity;
import org.stickbadminton.entity.StickMan;
import org.stickbadminton.fx.HittingFX;
import org.stickbadminton.fx.NetAnimation;
import org.stickbadminton.fx.ParticleFX;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.logic.MatchController;

import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitter;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;
import org.stickbadminton.*;

public class Badminton extends GameObject {

    public boolean isNetworkControlled = false; // 是否由网络同步控制
    private double sendTimer = 0;  // 控制发送频率

    // 新增: 客户端预测用（上一个接收速度/时间），供 MatchController 跨包重置预测
    public double lastSpeedX = 0;
    public double lastSpeedY = 0;
    public double predictTimer = 0;

    public static int sideServe = 0; // 当前发球人
    public static double airResistance = 0;  //空气阻力加速度
    public boolean isFrozen = true; // 待发球状态时为 false，开球后能够自由移动，设为 true
    public boolean isTouchedGround = false; // 球是否落地
    public boolean isHitted = false;
    public int TouchedTime = 11;  //触墙或者触网后经过的时间，小于10的时候关闭拖尾
    public boolean isShotable = true; // 是否能被打出
    ParticleEmitter emitter = ParticleFX.fire();
    ParticleComponent particleComponent = new ParticleComponent(emitter);

    public Badminton() {
        super("badminton", Sprite.loadImage("/images/game/badminton.png"));
        speedY = -100;
        setCenterPosition(10.5, 3);
        setRotation(180);

        emitter.setNumParticles(0);
        entity.addComponent(particleComponent);

    }

    public Badminton(int _sideServe) {
        super("badminton", Sprite.loadImage("/images/game/badminton.png"));
        speedY = -100;
        setCenterPosition(10.5, 3);
        setRotation(180);

        sideServe = _sideServe;
        setRotation(sideServe == 1 ? 225: -225);

        emitter.setNumParticles(0);
        entity.addComponent(particleComponent);
    }

    @Override
    public void onUpdate() {

        //方向修正
        // 始终运行渲染逻辑（旋转、粒子）
        if (!isTouchedGround || Math.pow(speedY, 2) > 50) {
            double targetRotation;
            double p = (Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2))) / 800;
            if (speedX == 0)
                targetRotation = speedY > 0 ? 180 : 0;
            else if (speedX > 0)
                targetRotation = 90 + Math.toDegrees(Math.atan(speedY / speedX));
            else
                targetRotation = -90 + Math.toDegrees(Math.atan(speedY / speedX));
            if (isHitted) {
                rotation = targetRotation;
                isHitted = false;
            } else
                rotation = targetRotation * p + rotation * (1 - p);
        }


        // 主机定时广播球状态（50ms 周期，与客户端预测窗口/插值系数匹配）
        if (MatchController.isHost() && MatchController.getNetClient() != null) {
            sendTimer += GameProperties.frameTime;
            if (sendTimer >= 0.05) {
                String ballState = String.format("BALL:%.2f:%.2f:%.2f:%.2f", getCenterX(), getCenterY(), speedX, speedY);
                MatchController.getNetClient().sendLine(ballState);
                sendTimer = 0;
            }
        }

        if (TouchedTime > 10)
            ParticleFX.updataFire(emitter, speedX, speedY);
        else {
            ParticleFX.closeParticle(emitter);
            TouchedTime++;
        }

        if (isNetworkControlled) {
            // 客户端: 短暂预测物理（模拟弧线，避免匀速）
            predictTimer += GameProperties.frameTime;
            if (predictTimer < 0.05) {  // 只预测50ms，避免分叉
                airResistance = 0.00001 * (Math.pow(lastSpeedX, 2) + Math.pow(lastSpeedY, 2));
                speedY += GameProperties.badmintonGravity * GameProperties.frameTime;
                double totalSpeed = Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2));
                if (totalSpeed > 0) {
                    speedX -= airResistance * speedX / totalSpeed * GameProperties.frameTime;
                    speedY -= airResistance * speedY / totalSpeed * GameProperties.frameTime;
                }
                x += speedX * GameProperties.frameTime;
                y += speedY * GameProperties.frameTime;
            }
            return;
        }

        if (isFrozen) {
            speedY = 0;
            if (sideServe == StickMan.sideLeft) { // 右侧火柴人
                StickMan stickmanRight = (StickMan) inRoom.getObject("stickman_right");
                setPositionWithCenter(stickmanRight.getX(), stickmanRight.getY() + 30);
            }
            if (sideServe == StickMan.sideRight) { // 左侧火柴人
                StickMan stickManLeft = (StickMan) inRoom.getObject("stickman_left");
                setPositionWithCenter(stickManLeft.getX() + 21, stickManLeft.getY() + 30);
            }
            if (sideServe == StickMan.sideLeft && GameProperties.matchMode == 1) {
                isFrozen = false;
                speedX = 250 * sideServe;
                speedY = 300;
            }
            else if (((sideServe == StickMan.sideRight) && (KeyInput.isKeyHolding(KeyCode.Q) || KeyInput.isKeyHolding(KeyCode.E)))
                    || ((sideServe == StickMan.sideLeft) && (KeyInput.isKeyHolding(KeyCode.U) || KeyInput.isKeyHolding(KeyCode.O)))) {
                isFrozen = false;
                speedX = 250 * sideServe;
                speedY = 300;
            }
            // 发球准备期间球跟随球员的位置由 50ms 定时广播同步，不再每帧发送
            return;
        }
        // 注意球的贴图会随着运动方向而进行旋转
        // 在空中运动状态
        // 操作 speedX, speedY 等
        //落地判断
        double centerX = getCenterX();
        double centerY = getCenterY();
        if (y + speedY * GameProperties.frameTime >= GameProperties.floorBallY) {
            isTouchedGround = true;
            onHitGround();
        } else
            isTouchedGround = false;
        if (isTouchedGround) {
            y = GameProperties.floorBallY;
            if (speedY >= 400) {
                speedY = -(speedY * 0.4);
                speedX *= 0.3;
            } else if (speedY > 50) {
                speedY = -(speedY * 0.4);
                speedX *= 0.5;
            } else {
                speedY = 0;
                speedX = 0;
            }
        } else {
            if (speedY == 0 && speedX == 0)
                speedY += GameProperties.badmintonGravity;
            else {
                airResistance = 0.00001 * (Math.pow(speedX, 2) + Math.pow(speedY, 2));  //空气阻力计算公式
                speedY += GameProperties.badmintonGravity - 0.5 * airResistance * (speedY / Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)));
                speedX -= 2.7 * airResistance * (speedX / Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)));
            }
        }
        //触墙判断
        if (x + speedX * GameProperties.frameTime <= GameProperties.playFieldLeft || x + speedX * GameProperties.frameTime >= GameProperties.playFieldRight) {
            TouchedTime = 0;
            x = Math.pow(x - GameProperties.playFieldLeft, 2) < Math.pow(x - GameProperties.playFieldRight, 2) ? GameProperties.playFieldLeft : GameProperties.playFieldRight;
            speedX = -speedX * 0.6;
        }
        //触网判断
        if (y + speedY * GameProperties.frameTime >= GameProperties.floorBallY - GameProperties.netHeight + 20
                && (x + speedX * GameProperties.frameTime >= GameProperties.netPosition - 18 && x <= GameProperties.netPosition - 18
                || x + speedX * GameProperties.frameTime <= GameProperties.netPosition - 8 && x >= GameProperties.netPosition - 8)) {
            TouchedTime = 0;
            onNetCrashed();   //调用播放触网动画方法
            if (y < GameProperties.floorBallY - GameProperties.netHeight + 25) {
                y = GameProperties.floorBallY - GameProperties.netHeight + 20;
                speedY = speedY * 0.1;
                speedX = speedX * 0.8;
            } else {
                if (speedX > 0) {
                    x = GameProperties.netPosition - 23;
                } else {
                    x = GameProperties.netPosition - 3;
                }
                speedY = speedY * 0.4;
                speedX = -speedX * 0.4;
            }
        }

        //拖尾粒子发射
        if(TouchedTime>10)
            ParticleFX.updataFire(emitter, speedX, speedY);
        else {
            ParticleFX.closeParticle(emitter);
            TouchedTime++;
        }
    }

    public void lightHit(double angle) {
        // angle: 击打角度
        // 被击打（力度小)
        SoundPlay.playSound("shot_light.mp3", 0.4);
        while (angle < 0)
            angle += 360;
        while (angle > 360)
            angle -= 360;
        isHitted = true;
        double speed;
        if (angle < 180) // 扣球
            speed = 1200;
        else if (getCenterX() >= GameProperties.netPosition - GameProperties.powerDistance
                && getCenterX() <= GameProperties.netPosition + GameProperties.powerDistance) // 近场
            speed = 500;
        else if (getCenterX() <= GameProperties.netPosition - GameProperties.powerDistance * 2
                || getCenterX() >= GameProperties.netPosition + GameProperties.powerDistance * 2) // 远场
            speed = 900;
        else
            speed = 700;
        speedY = speed * Math.sin(Math.toRadians(angle));
        speedX = speed * Math.cos(Math.toRadians(angle));
        emitter.setNumParticles(4);
        onHit();
        if (MatchController.isHost() && MatchController.getNetClient() != null) {
            // 主机: 击球后立即广播状态，减少等待下一周期造成的视觉跳变
            String ballState = String.format("BALL:%.2f:%.2f:%.2f:%.2f", getCenterX(), getCenterY(), speedX, speedY);
            MatchController.getNetClient().sendLine(ballState);
        }
    }

    public void heavyHit(double angle) {
        // angle: 击打角度
        // 被击打（力度大）
        SoundPlay.playSound("shot_heavy.mp3", 1.0);
        while (angle < 0)
            angle += 360;
        while (angle > 360)
            angle -= 360;
        isHitted = true;
        double speed;
        if (angle < 180) // 扣球
            speed = 2500;
        else if (getCenterX() >= GameProperties.netPosition - GameProperties.powerDistance
                && getCenterX() <= GameProperties.netPosition + GameProperties.powerDistance) // 近场
            speed = 900;
        else if (getCenterX() <= GameProperties.netPosition - GameProperties.powerDistance * 2
                || getCenterX() >= GameProperties.netPosition + GameProperties.powerDistance * 2) // 远场
            speed = 1300;
        else
            speed = 1100;
        speedY = speed * Math.sin(Math.toRadians(angle));
        speedX = speed * Math.cos(Math.toRadians(angle));

        emitter.setNumParticles(4);
        onHit();

        if (MatchController.isHost() && MatchController.getNetClient() != null) {
            // 主机: 击球后立即广播状态，减少等待下一周期造成的视觉跳变
            String ballState = String.format("BALL:%.2f:%.2f:%.2f:%.2f", getCenterX(), getCenterY(), speedX, speedY);
            MatchController.getNetClient().sendLine(ballState);
        }
    }

    //播放触网动画，在触网判断中被调用
    public void onNetCrashed() {
        SoundPlay.playSound("net_crash.mp3", 0.4);
        NetAnimation net = (NetAnimation) inRoom.getObject("net");
        if (net != null) {
            net.playCrashAnimation();
        }
    }

    //播放击球特效
    public void onHit() {
        double FXRotation;
        HittingFX hf = new HittingFX();
        hf.setX(getCenterX() - hf.getCenterX());
        hf.setY(getCenterY() - hf.getCenterY());
        if (speedX > 0)
            FXRotation = Math.toDegrees(Math.atan(speedY / speedX)) + 90;
        else
            FXRotation = Math.toDegrees(Math.atan(speedY / speedX)) - 90;
        inRoom.addObject(hf);
        hf.setRotation(FXRotation);
    }

    //触地判断
    public void onHitGround() {
        if (isShotable) {
            isShotable = false;
            if (inRoom != null) {
                ((MatchController) inRoom.getObject("controller")).onBallGroundHit(getCenterX() > 450 ? 1 : -1);
            }
        }
    }
}

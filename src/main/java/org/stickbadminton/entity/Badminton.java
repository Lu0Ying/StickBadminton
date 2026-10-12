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

    public boolean isNetworkControlled = false; // 联机客户端模式：手里位置靠 BALL 同步、自由飞行走确定性同轨模拟
    private double sendTimer = 0;  // 发球准备期同步心跳计时
    private boolean ballSendLogged = false; // 一次性诊断：主机首条发送日志

    public static int sideServe = 0; // 当前发球人
    public static double airResistance = 0;  //空气阻力加速度
    public boolean isFrozen = true; // 待发球状态时为 false，开球后能够自由移动，设为 true
    public boolean isTouchedGround = false; // 球是否落地
    public boolean isHitted = false;
    public int TouchedTime = 11;  //触墙或者触网后经过的时间，小于10的时候关闭拖尾
    public boolean isShotable = true; // 是否能被打出
    // 触网一次性闩锁：路径进入网柱区间时触发一次即上锁，离开区间才解锁——
    // 防止球停在网柱区间内时每逻辑帧重复触发触网音效/动画
    private boolean netHitLatch = true;
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

        //方向修正（StrictMath 保证双端逐位一致）
        // 始终运行渲染逻辑（旋转、粒子）
        if (!isTouchedGround || StrictMath.pow(speedY, 2) > 50) {
            double targetRotation;
            double p = (StrictMath.sqrt(StrictMath.pow(speedX, 2) + StrictMath.pow(speedY, 2))) / 800;
            if (speedX == 0)
                targetRotation = speedY > 0 ? 180 : 0;
            else if (speedX > 0)
                targetRotation = 90 + StrictMath.toDegrees(StrictMath.atan(speedY / speedX));
            else
                targetRotation = -90 + StrictMath.toDegrees(StrictMath.atan(speedY / speedX));
            if (isHitted) {
                rotation = targetRotation;
                isHitted = false;
            } else
                rotation = targetRotation * p + rotation * (1 - p);
        }


        // 主机在发球准备期广播球位置（跟随球员无法双端确定性还原）；
        // 自由飞行不发——客机用同一套固定逻辑帧物理同轨回放，靠 HIT/SCORE/resetBall 事件对齐
        if (MatchController.isHost() && MatchController.getNetClient() != null) {
            if (isFrozen) {
                sendTimer += GameProperties.frameTime;
                if (sendTimer >= 0.05) {
                    String ballState = String.format("BALL:%.2f:%.2f:%.2f:%.2f", getCenterX(), getCenterY(), speedX, speedY);
                    MatchController.getNetClient().sendLine(ballState);
                    sendTimer = 0;
                    // 一次性诊断：确认主机在发球状态
                    if (!ballSendLogged) {
                        ballSendLogged = true;
                        System.out.println("[Ball] host first send: " + ballState);
                    }
                }
            } else {
                sendTimer = 0;
            }
        }

        if (TouchedTime > 10)
            ParticleFX.updataFire(emitter, speedX, speedY);
        else {
            ParticleFX.closeParticle(emitter);
            TouchedTime++;
        }

        if (isFrozen) {
            speedY = 0;
            // 主机/单机：球跟随发球者位置；客户端不跟随（本地球员与主机不同步，位置由 BALL 心跳驱动）
            if (!isNetworkControlled) {
                if (sideServe == StickMan.sideLeft) { // 右侧火柴人
                    StickMan stickmanRight = (StickMan) inRoom.getObject("stickman_right");
                    setPositionWithCenter(stickmanRight.getX(), stickmanRight.getY() + 30);
                }
                if (sideServe == StickMan.sideRight) { // 左侧火柴人
                    StickMan stickManLeft = (StickMan) inRoom.getObject("stickman_left");
                    setPositionWithCenter(stickManLeft.getX() + 21, stickManLeft.getY() + 30);
                }
            }
            // 释放检测双端同构执行（客户端按键来自服务器注入，固定初速双端一致）。
            // 发球方是 AI（单人模式右半场常驻 AI，或 G 键把 p1 切成 AI）时自动释放，
            // 替代原先的 matchMode==1 特判：单人行为不变，联机 AI 发球也走同一路径
            if (isServeSideControlledByAI()) {
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
            return;
        }
        // 释放后双端进入同一套固定步长（1/60）物理回放：状态序列一致则轨迹同轨
        // 注意球的贴图会随着运动方向而进行旋转
        // 在空中运动状态
        // 操作 speedX, speedY 等
        // 本逻辑步的起点位置（x 的积分由 ObjectControl 在 onUpdate 返回后执行），
        // 供触网判定做整段路径覆盖检测
        double stepStartX = x;
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
                airResistance = 0.00001 * (StrictMath.pow(speedX, 2) + StrictMath.pow(speedY, 2));  //空气阻力计算公式
                speedY += GameProperties.badmintonGravity - 0.5 * airResistance * (speedY / StrictMath.sqrt(StrictMath.pow(speedX, 2) + StrictMath.pow(speedY, 2)));
                speedX -= 2.7 * airResistance * (speedX / StrictMath.sqrt(StrictMath.pow(speedX, 2) + StrictMath.pow(speedY, 2)));
            }
        }
        //触墙判断
        if (x + speedX * GameProperties.frameTime <= GameProperties.playFieldLeft || x + speedX * GameProperties.frameTime >= GameProperties.playFieldRight) {
            TouchedTime = 0;
            x = StrictMath.pow(x - GameProperties.playFieldLeft, 2) < StrictMath.pow(x - GameProperties.playFieldRight, 2) ? GameProperties.playFieldLeft : GameProperties.playFieldRight;
            speedX = -speedX * 0.6;
        }
        //触网判断：本步路径与网柱区间 [netPosition-18, netPosition-8] 重叠即判定触网——
        // 与步相位无关，消除双端因状态相差一步导致一端触发、一端漏判的轨迹分叉
        double stepEndX = x + speedX * GameProperties.frameTime;
        double pathMin = Math.min(stepStartX, stepEndX);
        double pathMax = Math.max(stepStartX, stepEndX);
        boolean inNetColumn = pathMin <= GameProperties.netPosition - 8 && pathMax >= GameProperties.netPosition - 18;
        if (!inNetColumn) {
            netHitLatch = true; // 路径已离开网柱区间，解锁以允许下一次触网
        }
        if (inNetColumn && netHitLatch
                && y + speedY * GameProperties.frameTime >= GameProperties.floorBallY - GameProperties.netHeight + 20) {
            netHitLatch = false; // 同一次穿越只触发一次，防止球停在网柱区间内时逐帧重复触发
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
        // 拖尾粒子与 TouchedTime 递增统一在 onUpdate 开头的渲染块处理，避免一帧双跳
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
        speedY = speed * StrictMath.sin(StrictMath.toRadians(angle));
        speedX = speed * StrictMath.cos(StrictMath.toRadians(angle));
        emitter.setNumParticles(4);
        onHit();
        // 击球状态同步由 HIT 事件承担（带击球点，客户端 setPosition 后重放同一公式）
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
        speedY = speed * StrictMath.sin(StrictMath.toRadians(angle));
        speedX = speed * StrictMath.cos(StrictMath.toRadians(angle));

        emitter.setNumParticles(4);
        onHit();
        // 击球状态同步由 HIT 事件承担（带击球点，客户端 setPosition 后重放同一公式）
    }

    /**
     * 当前发球方是否由 AI 控制。
     * sideServe==sideRight 的发球者是左半场 stickman_left（p1），
     * sideServe==sideLeft 的发球者是右半场 stickman_right；
     * 双端按同一状态判断，自动释放时机一致。
     */
    private boolean isServeSideControlledByAI() {
        StickMan server = (StickMan) inRoom.getObject(
                sideServe == StickMan.sideRight ? "stickman_left" : "stickman_right");
        return server != null && server.isAIControlled;
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
            FXRotation = StrictMath.toDegrees(StrictMath.atan(speedY / speedX)) + 90;
        else
            FXRotation = StrictMath.toDegrees(StrictMath.atan(speedY / speedX)) - 90;
        inRoom.addObject(hf);
        hf.setRotation(FXRotation);
    }

    //触地判断
    public void onHitGround() {
        if (isShotable) {
            isShotable = false;
            // 仅主机/单机做落地裁决计分：客户端等主机 SCORE 事件驱动，避免双端裁决分叉
            boolean localAuthority = MatchController.getNetClient() == null || MatchController.isHost();
            if (localAuthority && inRoom != null) {
                ((MatchController) inRoom.getObject("controller")).onBallGroundHit(getCenterX() > 450 ? 1 : -1);
            }
        }
    }
}

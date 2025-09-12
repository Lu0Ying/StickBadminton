package org.stickbadminton.gamecomponent;

import com.almasb.fxgl.dsl.FXGL;
import javafx.geometry.Point2D;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitter;
import com.almasb.fxgl.particle.ParticleEmitters;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;
import org.stickbadminton.Sprite;
import org.stickbadminton.UIObject;
import org.stickbadminton.gamecomponent.GameProperties;
import java.util.ArrayList;
import java.util.List;

public class Badminton extends GameObject {
    //ParticleEmitter emitter = ParticleEmitters.newFireEmitter(); //火焰附加粒子发射器
    //private ParticleComponent pc = new ParticleComponent(emitter); //火焰发射器的载体，要通过这个载体来更变发射器的位置
    public static double airResistance = 0;  //空气阻力加速度
    public boolean isFrozen = false; // 待发球状态时为 false，开球后能够自由移动，设为 true
    public boolean isTouchedGround = false; // 球是否落地
    public boolean isHitted = false;
    ParticleEmitter emitter;
    ParticleComponent component;
    public Badminton() {
        super("badminton", new Image("badminton.png"));
        speedY = -100;
        setCenterPosition(10.5, 3);
        setRotation(180);
        emitter = ParticleFX.fire();
        component = new ParticleComponent(emitter);
        entity.addComponent(component);
    }

    @Override
    public void onUpdate() {
        if (isFrozen) {
            // 这块先不碰，等火柴人代码写好
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
            x = Math.pow(x - GameProperties.playFieldLeft, 2) < Math.pow(x - GameProperties.playFieldRight, 2) ? GameProperties.playFieldLeft : GameProperties.playFieldRight;
            speedX = -speedX * 0.6;
        }
        //触网判断
        if (y + speedY * GameProperties.frameTime >= GameProperties.floorBallY - GameProperties.netHeight + 20
                && (x + speedX * GameProperties.frameTime >= GameProperties.netPosition - 18 && x <= GameProperties.netPosition - 18
                || x + speedX * GameProperties.frameTime <= GameProperties.netPosition - 8 && x >= GameProperties.netPosition - 8)) {
            onNetCrashed();   //调用播放触网动画方法
            if (y < GameProperties.floorBallY - GameProperties.netHeight + 30) {
                y = GameProperties.floorBallY - GameProperties.netHeight + 27;
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
        //方向修正
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
        //扣杀火焰附加
        if(Math.sqrt(Math.pow(speedX,2)+Math.pow(speedY,2))>=1400) {
            emitter.setNumParticles(20); // 打开发射
            emitter.setVelocityFunction(i -> {
                double angle = FXGL.random(-10.0, 10.0);
                double speed = FXGL.random(100, 250);
                double dirX = (speedX/Math.sqrt(speedX*speedX+speedY*speedY));
                double dirY = (speedY/Math.sqrt(speedX*speedX+speedY*speedY));
                double finalX = dirX * Math.cos(Math.toRadians(angle)) - dirY * Math.sin(Math.toRadians(angle));
                double finalY = dirX * Math.sin(Math.toRadians(angle)) + dirY * Math.cos(Math.toRadians(angle));
                return new Point2D(
                        -speed * finalX,
                        -speed * finalY
                );
            });
        }
        else
            emitter.setNumParticles(0);
        //测试代码
        if(KeyInput.isKeyHolding(KeyCode.T))
            heavyHit(x >=450 ? -135 :-45);
        if(KeyInput.isKeyHolding(KeyCode.Y))
            heavyHit(x >=450 ? 165 :15);
    }

    public void kickOffHeavy() {
        // 开球..（自由落体）
        isHitted = true;
        isFrozen = false;
        double angle = getCenterX() > GameProperties.netPosition ? -45 : 45;
        double speed = 1100;
        speedY = -speed * Math.cos(Math.toRadians(angle));
        speedX = speed * Math.sin(Math.toRadians(angle));
        onHit();
    }

    public void kickOffLight() {
        // 开球..（自由落体）
        isHitted = true;
        isFrozen = false;
        double angle = getCenterX() > GameProperties.netPosition ? -58 : 58;
        double speed = 700;
        speedY = -speed * Math.cos(Math.toRadians(angle));
        speedX = speed * Math.sin(Math.toRadians(angle));
        onHit();
    }

    public void lightHit(double angle) {
        // angle: 击打角度
        // 被击打（力度小)
        while (angle < 0)
            angle += 360;
        while (angle > 360)
            angle -= 360;
        isHitted = true;
        double speed;
        if (angle < 180) // 扣球
            speed = 1200;
        else if (getCenterX() >= GameProperties.netPosition - GameProperties.serveLineDistance
                && getCenterX() <= GameProperties.netPosition + GameProperties.serveLineDistance) // 近场
            speed = 500;
        else if (getCenterX() <= GameProperties.netPosition - GameProperties.serveLineDistance * 2
                || getCenterX() >= GameProperties.netPosition + GameProperties.serveLineDistance * 2) // 远场
            speed = 900;
        else
            speed = 700;
        speedY = speed * Math.sin(Math.toRadians(angle));
        speedX = speed * Math.cos(Math.toRadians(angle));
        onHit();
    }

    public void heavyHit(double angle) {
        // angle: 击打角度
        // 被击打（力度大）
        while (angle < 0)
            angle += 360;
        while (angle > 360)
            angle -= 360;
        isHitted = true;
        double speed;
        if (angle < 180) // 扣球
            speed = 2500;
        else if (getCenterX() >= GameProperties.netPosition - GameProperties.serveLineDistance
                && getCenterX() <= GameProperties.netPosition + GameProperties.serveLineDistance) // 近场
            speed = 900;
        else if (getCenterX() <= GameProperties.netPosition - GameProperties.serveLineDistance * 2
                || getCenterX() >= GameProperties.netPosition + GameProperties.serveLineDistance * 2) // 远场
            speed = 1300;
        else
            speed = 1100;
        speedY = speed * Math.sin(Math.toRadians(angle));
        speedX = speed * Math.cos(Math.toRadians(angle));
        onHit();
    }

    //播放触网动画，在触网判断中被调用
    public void onNetCrashed() {
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
        if (inRoom != null) {
            if (getCenterX() < GameProperties.netPosition) {
                UIObject scoreRight = inRoom.getUiObject("score_right");
                if (scoreRight instanceof UIDigitView) {
                    UIDigitView scoreView = (UIDigitView) scoreRight;
                    scoreView.setCurrentNumber((scoreView.getCurrentNumber() + 1) % 10);
                }
            } else {
                UIObject scoreLeft = inRoom.getUiObject("score_left");
                if (scoreLeft instanceof UIDigitView) {
                    UIDigitView scoreView = (UIDigitView) scoreLeft;
                    scoreView.setCurrentNumber((scoreView.getCurrentNumber() + 1) % 10);
                }
            }

            //判断获胜
            UIObject scoreLeft = inRoom.getUiObject("score_left");
            UIObject scoreRight = inRoom.getUiObject("score_right");

            if (scoreLeft instanceof UIDigitView && scoreRight instanceof UIDigitView) {
                UIDigitView leftView = (UIDigitView) scoreLeft;
                UIDigitView rightView = (UIDigitView) scoreRight;

                if (leftView.getCurrentNumber() >= 9 || rightView.getCurrentNumber() >= 9) {
                    // 游戏结束，显示结果
                    showGameResult(leftView.getCurrentNumber() >= 9 ? "Player1" : "Player2");
                    deactivate();
                    return;
                }
            }

            inRoom.addObject(new Badminton()).setPosition(100, 100);
            deactivate();
        }
    }

    private void showGameResult(String winner) {
        // 创建游戏结束UI
        UIGameOver gameOverUI = new UIGameOver(winner);

        // 将UI添加到当前房间
        if (inRoom != null) {
            // 居中显示
            double centerX = (GameProperties.roomWidth - 300) / 2; // 假设UI宽度为300
            double centerY = (GameProperties.roomHeight - 200) / 2; // 假设UI高度为200

            inRoom.addUiObject(gameOverUI, (int) centerX, (int) centerY);

            // 暂停游戏逻辑
            // 可以添加一个游戏暂停的状态变量来控制更新逻辑
        }
    }
}

package org.stickbadminton.gamecomponent;

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
    public Badminton() {
        super("badminton", new Image("badminton.png"));
        speedY = -100;
        setCenterPosition(10.5, 3);
        setRotation(180);
        //emitter.setNumParticles(0); // 默认不发射
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
        if(y+ speedY * GameProperties.frameTime >=GameProperties.floorY) {
            isTouchedGround = true;
            onHitGround();
        }
        else
            isTouchedGround = false;
        if(isTouchedGround) {
            y=GameProperties.floorY;
            if(speedY>=400) {
                speedY = -(speedY * 0.4);
                speedX*=0.3;
            }
            else if(speedY>50) {
                speedY = -(speedY * 0.4);
                speedX*=0.5;
            }
            else {
                speedY = 0;
                speedX = 0;
            }
        }
        else {
            if(speedY==0&&speedX==0)
                speedY += GameProperties.badmintonGravity;
            else {
                airResistance = 0.00001 * (Math.pow(speedX, 2) + Math.pow(speedY, 2));  //空气阻力计算公式
                speedY += GameProperties.badmintonGravity-0.5*airResistance*(speedY/Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)));
                speedX -= 2.7*airResistance*(speedX/Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)));
            }
        }
        //触墙判断
        if(x + speedX * GameProperties.frameTime<=GameProperties.playFieldLeft||x + speedX * GameProperties.frameTime>=GameProperties.playFieldRight) {
            x=Math.pow(x-GameProperties.playFieldLeft,2)<Math.pow(x-GameProperties.playFieldRight,2)?GameProperties.playFieldLeft:GameProperties.playFieldRight;
            speedX = -speedX * 0.6;
        }
        //触网判断
        if(y + speedY * GameProperties.frameTime>= GameProperties.floorY-GameProperties.netHeight+20
                && (x+ speedX * GameProperties.frameTime >=GameProperties.netPosition-18 && x<= GameProperties.netPosition-18
                || x+speedX * GameProperties.frameTime <= GameProperties.netPosition-8 && x>= GameProperties.netPosition-8)) {
            onNetCrashed();   //调用播放触网动画方法
            if(y< GameProperties.floorY-GameProperties.netHeight+30) {
                y=GameProperties.floorY-GameProperties.netHeight+27;
                speedY = speedY * 0.1;
                speedX = speedX * 0.8;
            }
            else {
                if(speedX>0) {
                    x = GameProperties.netPosition - 23;
                }
                else {
                    x = GameProperties.netPosition - 3;
                }
                speedY = speedY * 0.4;
                speedX = -speedX * 0.4;
            }
        }
        //方向修正
        if(!isTouchedGround||Math.pow(speedY,2)>50) {
            double targetRotation;
            double p = (Math.sqrt(Math.pow(speedX,2)+Math.pow(speedY,2))) / 800;
            if (speedX == 0)
                targetRotation = speedY > 0 ? 180 : 0;
            else if (speedX > 0)
                targetRotation = 90 + Math.toDegrees(Math.atan(speedY / speedX));
            else
                targetRotation = -90 + Math.toDegrees(Math.atan(speedY / speedX));
            if(isHitted) {
                rotation = targetRotation;
                isHitted = false;
            }
            else
                rotation = targetRotation * p + rotation * (1 - p);
        }
        /*扣杀火焰附加
        emitter.setSpawnPoint(badminton.getCenter());;
        if(Math.sqrt(Math.pow(speedX,2)+Math.pow(speedY,2))>=2000)
            emitter.setNumParticles(30);
        else
            emitter.setNumParticles(0);
         */
        //测试代码
        /*if(KeyInput.isKeyHolding(KeyCode.T))
            lightHit(x >=450 ? -135 :-45);*/
    }

    public void kickOffHeavy() {
        // 开球..（自由落体）
        isHitted = true;
        isFrozen = false;
        double angle = x > 450 ? -45 : 45;
        double speed = 900;
        speedY = -speed * Math.cos(Math.toRadians(angle));
        speedX = speed * Math.sin(Math.toRadians(angle));
        onHit();
    }
    public void kickOffLight() {
        // 开球..（自由落体）
        isHitted = true;
        isFrozen = false;
        double angle = x > 450 ? -58 : 58;
        double speed = 700;
        speedY = -speed * Math.cos(Math.toRadians(angle));
        speedX = speed * Math.sin(Math.toRadians(angle));
        onHit();
    }
    public void lightHit(double angle) {
        // angle: 击打角度
        // 被击打（力度小)
        isHitted = true;
        double speed;
        if(Math.sin(Math.toRadians(angle))>0.2)
            speed = 1200;
        else if(getCenterX()>=350 && getCenterX()<=550)
            speed= 300;
        else
            speed= 500;
        speedY = speed * Math.sin(Math.toRadians(angle));
        speedX = speed * Math.cos(Math.toRadians(angle));
        onHit();
    }

    public void heavyHit(double angle) {
        // angle: 击打角度
        // 被击打（力度大）
        isHitted = true;
        double speed;
        if(Math.sin(Math.toRadians(angle))>0.2)
            speed= 2500;
        else if(getCenterX()>=320 && getCenterX()<=680)
            speed= 700;
        else
            speed= 900;
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
                UIObject scoreRight=inRoom.getUiObject("score_right");
                if(scoreRight instanceof UIDigitView){
                    UIDigitView scoreView =(UIDigitView)scoreRight;
                    scoreView.setCurrentNumber((scoreView.getCurrentNumber()+1)%10);
                }
            }
            else {
                UIObject scoreLeft = inRoom.getUiObject("score_left");
                if (scoreLeft instanceof UIDigitView) {
                    UIDigitView scoreView = (UIDigitView) scoreLeft;
                    scoreView.setCurrentNumber((scoreView.getCurrentNumber() + 1) % 10);
                }
            }

            inRoom.addObject(new Badminton()).setPosition(100, 100);
            deactivate();
        }
    }
}

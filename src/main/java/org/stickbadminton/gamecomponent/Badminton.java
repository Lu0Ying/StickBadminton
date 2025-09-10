package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;
import org.stickbadminton.Sprite;
import org.stickbadminton.gamecomponent.GameProperties;

import static org.stickbadminton.KeyInput.keys;

public class Badminton extends GameObject {
    public final double gravity = 13.0; // 此值可视具体情况调整
    public boolean isFrozen = false; // 待发球状态时为 false，开球后能够自由移动，设为 true
    public boolean isTouchedGround = false; // 球是否落地
    public Badminton() {
        super("badminton", new Image("badminton.png"));
        speedX= 550; //测试代码
        setCenterPosition(10.5, 3);
        setRotation(180);
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
        if(y>=GameProperties.floorY)
            isTouchedGround = true;
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
            speedY += gravity;
        }
        //触墙判断
        if(x<=GameProperties.playFieldLeft||x>=GameProperties.playFieldRight) {
            x=Math.pow(x-GameProperties.playFieldLeft,2)<Math.pow(x-GameProperties.playFieldRight,2)?GameProperties.playFieldLeft:GameProperties.playFieldRight;
            speedX = -speedX * 0.6;
        }
        //触网判断
        if(y>= GameProperties.floorY-GameProperties.netHeight+20 && Math.pow(x-(GameProperties.netPosition-13),2)<12
            /*&& (centerX-GameProperties.netPosition)*speedX > 0*/) {
            onNetCrashed();   //调用播放触网动画方法
            if(y< GameProperties.floorY-GameProperties.netHeight+30) {
                y=GameProperties.floorY-GameProperties.netHeight+27;
                //System.out.println(1);     //测试代码
                speedY = speedY * 0.1;
                speedX = speedX * 0.8;
            }
            else {
            if(x<GameProperties.netPosition)
                x=GameProperties.netPosition-15;
            else
                x=GameProperties.netPosition+5;
            //System.out.println(2);      //测试代码
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
            rotation = targetRotation * p + rotation * (1 - p);
            // 测试轻击重击用代码，以后版本会删除
            if (keys.contains(KeyCode.Z)) {
                lightHit(x > 450 ? -45.0 : -135.0);
            }
            if (keys.contains(KeyCode.X)) {
                heavyHit(x > 450 ? -45.0 : -135.0);
            }
        }
    }

    public void startMove() {
        // 开球..（自由落体）
    }

    public void lightHit(double angle) {
        // angle: 击打角度
        // 被击打（力度小）
    }

    public void heavyHit(double angle) {
        // angle: 击打角度
        // 被击打（力度大）
    }
    //播放触网动画，在触网判断中被调用
    public void onNetCrashed() {

    }
}

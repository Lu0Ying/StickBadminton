package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;
import org.stickbadminton.Sprite;

import static org.stickbadminton.KeyInput.keys;

public class Badminton extends GameObject {
    public final double gravity = 10.0; // 此值可视具体情况调整
    public boolean isFrozen = false; // 待发球状态时为 false，开球后能够自由移动，设为 true
    public boolean isTouchedGround = false; // 球是否落地

    public Badminton() {
        super("badminton", new Image("badminton.png"));
        setCenterPosition(0, 13.5);
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
        speedY += gravity;



        // 测试轻击重击用代码，以后版本会删除
        if (keys.contains(KeyCode.Z))
        {
            lightHit(x > 450 ? -45.0 : -135.0);
        }
        if (keys.contains(KeyCode.X))
        {
            heavyHit(x > 450 ? -45.0 : -135.0);
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
}

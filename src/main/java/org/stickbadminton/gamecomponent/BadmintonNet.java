// BadmintonNet.java
package org.stickbadminton.gamecomponent;

import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitter;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.SoundPlay;

public class BadmintonNet extends GameObject {
    public static int sideServe = 0; // 当前发球人
    public boolean isFrozen = true;
    public boolean isTouchedGround = false;
    public boolean isHitted = false;
    public int TouchedTime = 11;
    public boolean isShotable = true;
    ParticleEmitter emitter = ParticleFX.fire();
    ParticleComponent particleComponent = new ParticleComponent(emitter);

    public BadmintonNet() {
        super("badminton", new Image("badminton.png"));
        setCenterPosition(10.5, 3);
        setRotation(180);

        emitter.setNumParticles(0);
        entity.addComponent(particleComponent);
    }

    public BadmintonNet(int _sideServe) {
        super("badminton", new Image("badminton.png"));
        setCenterPosition(10.5, 3);
        setRotation(180);

        sideServe = _sideServe;
        setRotation(sideServe == 1 ? 225 : -225);

        emitter.setNumParticles(0);
        entity.addComponent(particleComponent);
    }

    @Override
    public void onUpdate() {
        // 只处理粒子效果
        if (TouchedTime > 10) {
            ParticleFX.updataFire(emitter, speedX, speedY);
        } else {
            ParticleFX.closeParticle(emitter);
            TouchedTime++;
        }
    }

    public void updateFromServer(double x, double y, double vx, double vy, double rotation,
                                 boolean frozen, boolean touchedGround, int touchedTime,
                                 boolean shotable, boolean hitted) {
        setCenterPosition(x, y);
        speedX = vx;
        speedY = vy;
        setRotation(rotation);
        isFrozen = frozen;
        isTouchedGround = touchedGround;
        TouchedTime = touchedTime;
        isShotable = shotable;
        isHitted = hitted;
    }

    // 播放触网动画
    public void onNetCrashed() {
        SoundPlay.playSound("net_crash.mp3", 0.4);
        NetAnimation net = (NetAnimation) inRoom.getObject("net");
        if (net != null) {
            net.playCrashAnimation();
        }
    }

    // 播放击球特效，根据hitType播放不同声音
    public void onHit(String hitType) {
        String soundFile = "shot_" + hitType.toLowerCase() + ".mp3";
        double volume = "light".equalsIgnoreCase(hitType) ? 0.4 : 1.0;
        SoundPlay.playSound(soundFile, volume);

        emitter.setNumParticles(4);

        double FXRotation;
        HittingFX hf = new HittingFX();
        hf.setX(getCenterX() - hf.getCenterX());
        hf.setY(getCenterY() - hf.getCenterY());
        if (speedX > 0) {
            FXRotation = Math.toDegrees(Math.atan(speedY / speedX)) + 90;
        } else {
            FXRotation = Math.toDegrees(Math.atan(speedY / speedX)) - 90;
        }
        inRoom.addObject(hf);
        hf.setRotation(FXRotation);
    }
}
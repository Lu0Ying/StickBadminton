package org.stickbadminton.gamecomponent;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitter;
import com.almasb.fxgl.particle.ParticleEmitters;
import javafx.geometry.Point2D;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.stickbadminton.GameObject;

public class ParticleFX {

    //尾焰粒子参数
    public static ParticleEmitter fire() {

        ParticleEmitter emitter = ParticleEmitters.newSmokeEmitter();
        emitter.setNumParticles(0);
        emitter.setSpawnPointFunction(i -> {
            return new Point2D(7, 0);
        });
        emitter.setBlendMode(BlendMode.ADD);
        return emitter;
    }
    public static void addParticle(GameObject object, int particleType) {
        ParticleEmitter emitter;
        ParticleComponent component;
        switch (particleType) {
            case 0:
                emitter = fire();
                break;
            default:
                emitter = ParticleEmitters.newFireEmitter();
                emitter.setNumParticles(0);
                break;
        }
        component = new ParticleComponent(emitter);
        object.getEntity().addComponent(component);
    }
    public static void updataFire(ParticleEmitter emitter,double speedX, double speedY) {
        double speed = Math.sqrt(speedX * speedX + speedY * speedY);
        if (speed > 300) {
            emitter.setVelocityFunction(i -> {
                double dirX = -speedX / speed;
                double dirY = -speedY / speed;
                double randomAngle = FXGL.random(-15.0, 15.0);
                double cos = Math.cos(Math.toRadians(randomAngle));
                double sin = Math.sin(Math.toRadians(randomAngle));
                double finalX = dirX * cos - dirY * sin;
                double finalY = dirX * sin + dirY * cos;
                double FireSpeed = FXGL.random(100, 250);
                return new Point2D(finalX * FireSpeed, finalY * FireSpeed);
            });
        }
        if (speed>1400)
        {
            emitter.setSize(6,9);
            emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(0.4, 0.6)));
            emitter.setNumParticles(40);
            emitter.setEndColor(Color.color(0.5, 0.03,0.03, 0.9));
        }
        else if (speed > 1000) {
            emitter.setSize(2,3);
            emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(0.5, 0.8)));
            emitter.setNumParticles(20);
            emitter.setEndColor(Color.color(0.96, 0.1+Math.random()*0.5, 0.02, Math.random()*0.7));
            emitter.setStartColor(Color.color(0.96, 0.1+Math.random()*0.5, 0.02, Math.random()*0.7));
        } else if (speed > 300) {
            emitter.setSize(2,3);
            emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(0.5, 0.8)));
            emitter.setNumParticles(4);
            emitter.setEndColor(Color.color(0.96, 0.1+Math.random()*0.5, 0.02, Math.random()*0.7));
            emitter.setStartColor(Color.color(0.96, 0.1+Math.random()*0.5, 0.02, Math.random()*0.7));
        } else {
            emitter.setNumParticles(0);
        }
    }
    public static void closeParticle(ParticleEmitter emitter) {
        emitter.setNumParticles(0);
    }
}

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
        emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(1, 2)));
        emitter.setSize(1,2);
        emitter.setVelocityFunction(i -> {
            // 给一个随机方向（-15° ~ 15° 偏移）保证尾焰不会太死板
            double angle = FXGL.random(85.0, 95.0);
            double speed = FXGL.random(100, 250);
            return new Point2D(
                     speed * Math.cos(Math.toRadians(angle)),
                    speed * Math.sin(Math.toRadians(angle))-140
            );
        });
        emitter.setSpawnPointFunction(i -> {
            return new Point2D(7, 0);
        });
        emitter.setEndColor(Color.color(0.96, 0.9, 0.1, 0.4+Math.random()*0.18+Math.random()*0.32));
        emitter.setStartColor(Color.color(0.96, 0.9, 0.01, 0.1+Math.random()*0.35+Math.random()*0.15));
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
}

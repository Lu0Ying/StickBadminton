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
        emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(0.3, 0.5)));
        emitter.setSize(3, 8);
        emitter.setStartColor(Color.color(1.0, 0.2, 0.2, 0.5));
        emitter.setEndColor(Color.color(1.0, 0.2, 0.02, 0.1));
        emitter.setBlendMode(BlendMode.ADD);
        return emitter;
    }

    public static void addParticle(GameObject object,int particleType) {
        ParticleEmitter emitter;
        ParticleComponent component;
        switch (particleType)
        {
            case 0: emitter = fire(); break;
            default: emitter = ParticleEmitters.newFireEmitter(); break;
        }
        component = new ParticleComponent(emitter);
        object.getEntity().addComponent(component);
    }
}

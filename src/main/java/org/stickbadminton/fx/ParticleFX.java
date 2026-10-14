package org.stickbadminton.fx;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitter;
import com.almasb.fxgl.particle.ParticleEmitters;
import javafx.geometry.Point2D;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.SoundPlay;

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

    // 拖尾档位缓存：全场只有 1 个羽毛球，档位与速度方向在两次跨档之间几乎不变。
    // 原实现每逻辑帧(60/s)重设 size/粒子数/颜色/寿命/速度函数——每次 new Color、
    // Math.random、new 一个捕获速度数组的 lambda，扣杀帧(速度跨 1400 档)还要叠加
    // 40 粒 burst 与原生音频创建，分配与属性 churn 集中同一帧即肉眼可见的"卡一下"。
    // 现改为仅档位/方向变化时应用一次；颜色按档位定色，顺带消除颜色逐帧随机抖动
    private static int lastTier = -1;      // 当前档位：0=关 1=弱 2=中 3=扣杀
    private static boolean lastState = false; // fire.mp3 单次触发闩锁
    private static double lastDirX, lastDirY; // 上次写入的速度方向（重建 lambda 的判据）
    // 档位颜色常量：避免每帧 new Color 触发对象属性失效通知
    private static final Color TIER3_END = Color.color(0.5, 0.03, 0.03, 0.9);

    public static void updataFire(ParticleEmitter emitter, double speedX, double speedY) {
        double speed = Math.sqrt(speedX * speedX + speedY * speedY);
        int tier = speed > 1400 ? 3 : speed > 1000 ? 2 : speed > 300 ? 1 : 0;
        if (tier != lastTier) {
            applyTier(emitter, tier);
            lastTier = tier;
            if (tier < 3) lastState = false;
        }
        if (tier == 3 && !lastState) {
            lastState = true;
            SoundPlay.playSound("fire.mp3", 0.2);
        }
        if (tier >= 1) updateVelocity(emitter, speedX, speedY, speed);
    }

    // 档位应用：仅在跨档那一帧调用（含档位内随机色一次定死，不再逐帧抖动）
    private static void applyTier(ParticleEmitter emitter, int tier) {
        switch (tier) {
            case 3: // 扣杀档
                emitter.setSize(6, 9);
                emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(0.4, 0.6)));
                emitter.setNumParticles(40);
                emitter.setEndColor(TIER3_END);
                break;
            case 2: // 高速档
                emitter.setSize(2, 3);
                emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(0.5, 0.8)));
                emitter.setNumParticles(20);
                applyRandomFireColor(emitter);
                break;
            case 1: // 普通拖尾档
                emitter.setSize(2, 3);
                emitter.setExpireFunction(i -> Duration.seconds(FXGL.random(0.5, 0.8)));
                emitter.setNumParticles(4);
                applyRandomFireColor(emitter);
                break;
            default: // 关闭
                emitter.setNumParticles(0);
        }
    }

    private static void applyRandomFireColor(ParticleEmitter emitter) {
        double c = Math.random() * 0.5;
        double a = Math.random() * 0.7;
        Color color = Color.color(0.96, 0.1 + c, 0.02, a);
        emitter.setEndColor(color);
        emitter.setStartColor(color);
    }

    // 速度方向变化超过约 5.7°(点积<0.995)才重建采样 lambda：正常飞行方向每帧
    // 仅微变不重建，触墙反弹/突转时立即重建，避免每帧 new lambda 捕获快照
    private static void updateVelocity(ParticleEmitter emitter, double speedX, double speedY, double speed) {
        double dirX = -speedX / speed, dirY = -speedY / speed;
        if (lastDirX * dirX + lastDirY * dirY >= 0.995) return;
        lastDirX = dirX;
        lastDirY = dirY;
        final double sampledDirX = dirX, sampledDirY = dirY;
        emitter.setVelocityFunction(i -> {
            double randomAngle = FXGL.random(-15.0, 15.0);
            double cos = Math.cos(Math.toRadians(randomAngle));
            double sin = Math.sin(Math.toRadians(randomAngle));
            double finalX = sampledDirX * cos - sampledDirY * sin;
            double finalY = sampledDirX * sin + sampledDirY * cos;
            double fireSpeed = FXGL.random(100, 250);
            return new Point2D(finalX * fireSpeed, finalY * fireSpeed);
        });
    }

    public static void closeParticle(ParticleEmitter emitter) {
        emitter.setNumParticles(0);
        lastTier = 0; // 下次 updataFire 视作跨档，重新应用配置（粒子数已置 0）
        lastState = false;
    }
}

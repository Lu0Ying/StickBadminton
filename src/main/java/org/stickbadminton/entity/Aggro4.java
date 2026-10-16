package org.stickbadminton.entity;

/**
 * 4级"激进"——反应几乎即时(0.10)、判圈满格(系数1.0)、
 * 最爱起跳(0.98)、扣杀概率最高(圈内85/55、提前95/60)、
 * 代价是 35% 扣杀免过网兜底(会砸网)、出球 ±7°、
 * 本回合扣杀/接杀后精度再掉 5°(±12，比5级明显)——凶但有破绽。
 */
final class Aggro4 extends AIDifficultyProfile {
    static final Aggro4 INSTANCE = new Aggro4();

    private Aggro4() {
        super(4, /*coeff*/1.0, /*reactionGate*/0.10, /*jumpGate*/0.98,
                /*inRingBack*/0.55, /*inRingMid*/0.85, /*earlyBack*/0.60, /*earlyMid*/0.95,
                /*lowSmashGate*/0.0, /*heavyScale*/1.0, /*smashMissRate*/0.35, /*accDrop*/5.0);
    }
}

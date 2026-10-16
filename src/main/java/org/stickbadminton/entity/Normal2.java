package org.stickbadminton.entity;

/**
 * 2级"普通"——正常人的反应(门槛0.35)、判圈×0.8、站位收敛0.9、
 * 起跳较积极(0.76)、圈内扣杀 80% 出手、免过网兜底但比菜鸟稳(±14°低档但兜底要求低)：
 * 能接能打，就是偶尔下网，"业余但不业余到离谱"。
 */
final class Normal2 extends AIDifficultyProfile {
    static final Normal2 INSTANCE = new Normal2();

    private Normal2() {
        super(2, /*coeff*/0.8, /*reactionGate*/0.35, /*jumpGate*/0.76,
                /*inRingBack*/0.35, /*inRingMid*/0.60, /*earlyBack*/0.40, /*earlyMid*/0.75,
                /*lowSmashGate*/0.80, /*heavyScale*/1.0, /*smashMissRate*/0.0, /*accDrop*/0.0);
    }
}

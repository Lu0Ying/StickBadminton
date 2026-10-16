package org.stickbadminton.entity;

/**
 * 1级"菜鸟"——反应迟钝(门槛0.50，平均慢半拍)、判圈只有半径×0.5、
 * 站位只走到落点七成半(0.5+0.5×0.5)、起跳随缘(0.475)、
 * 圈内扣杀 55% 才出手且免过网兜底——经常下网/出界，
 * 出球散布 ±14°，跑位预判误差保留（"看着球跑、慢半拍"的手感）。
 */
final class Noob1 extends AIDifficultyProfile {
    static final Noob1 INSTANCE = new Noob1();

    private Noob1() {
        super(1, /*coeff*/0.5, /*reactionGate*/0.50, /*jumpGate*/0.475,
                /*inRingBack*/0.35, /*inRingMid*/0.60, /*earlyBack*/0.40, /*earlyMid*/0.75,
                /*lowSmashGate*/0.55, /*heavyScale*/1.0, /*smashMissRate*/0.0, /*accDrop*/0.0,
                /*smashReceive*/0.10); // 菜鸟接杀几乎不反应
    }
}

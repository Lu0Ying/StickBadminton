package org.stickbadminton.entity;

/**
 * 5级"精准"——全档最快反应(0.02)、判圈满格(系数1.0)、
 * 起跳收敛(0.85，把节奏交给回球质量)、扣杀最克制(圈内35/15、提前50/20)、
 * 出球最准(±5°)、兜底要求最高(仅扣杀 15% 允许砸网)、
 * 本回合扣杀/接杀后精度仅掉 2°(±7)——稳、准、少失误。
 */
final class Precise5 extends AIDifficultyProfile {
    static final Precise5 INSTANCE = new Precise5();

    private Precise5() {
        super(5, /*coeff*/1.0, /*reactionGate*/0.02, /*jumpGate*/0.85,
                /*inRingBack*/0.15, /*inRingMid*/0.35, /*earlyBack*/0.20, /*earlyMid*/0.50,
                /*lowSmashGate*/0.0, /*heavyScale*/1.0, /*smashMissRate*/0.15, /*accDrop*/2.0,
                /*smashReceive*/0.60); // 全档最快也只有六成接到玩家扣杀
    }
}

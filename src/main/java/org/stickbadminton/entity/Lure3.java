package org.stickbadminton.entity;

/**
 * 3级"诱饵"——吊高球风格：反应快(0.20)、判圈×0.9、体力管理上岗(接球更稳)，
 * 起跳积极(0.95)专注接球，但 variedShot 重击倾向 ×0.4——多出高远球把进攻权
 * 交给玩家、诱玩家扣杀；扣杀免过网兜底，扣杀失误天然偏高。
 */
final class Lure3 extends AIDifficultyProfile {
    static final Lure3 INSTANCE = new Lure3();

    private Lure3() {
        super(3, /*coeff*/0.9, /*reactionGate*/0.20, /*jumpGate*/0.95,
                /*inRingBack*/0.35, /*inRingMid*/0.60, /*earlyBack*/0.40, /*earlyMid*/0.75,
                /*lowSmashGate*/0.0, /*heavyScale*/0.4, /*smashMissRate*/0.0, /*accDrop*/0.0,
                /*smashReceive*/0.28); // 诱饵接杀偏弱——正合"诱你扣、再送分"的角色
    }
}

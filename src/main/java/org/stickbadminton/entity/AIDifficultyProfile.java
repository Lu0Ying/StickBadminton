package org.stickbadminton.entity;

import org.stickbadminton.logic.GameProperties;

/**
 * 难度档案：每档人机的行为参数与击球特质集中在各子类
 * （Noob1 菜鸟 / Normal2 普通 / Lure3 诱饵 / Aggro4 激进 / Precise5 精准）。
 * 决策层与接触层经 byDifficulty() 取当前档——不再散落 switch，调手感只改子类。
 *
 * 档案只携带"门槛值"，随机 roll 仍在各调用点取恰好一次再比较
 * （与旧实现同频，不偏移随机流——联机同轨的硬约束）。
 */
public abstract class AIDifficultyProfile {

    /** 档位号 1~5 */
    public final int level;
    // 决策系数（0~1）：判圈半径 ×coeff、站位收敛 0.5+0.5×coeff
    public final double coeff;
    // 反应门槛：每帧 roll > reactionGate 才进入决策（越大越迟钝）
    public final double reactionGate;
    // 普通起跳门槛：roll < jumpGate 才起跳
    public final double jumpGate;
    // 圈内概率扣杀(需体力管理)：后场 / 中前场
    public final double inRingBack;
    public final double inRingMid;
    // 提前起跳扣杀概率：后场 / 中前场
    public final double earlyBack;
    public final double earlyMid;
    // 低难度(无体力管理)圈内扣杀门：仅 1/2 级非零（0 = 不启用该门）
    public final double lowSmashGate;
    // variedShot 重击倾向倍率：3级吊高球诱饵 0.4，其余 1.0 不干预
    public final double heavyScale;
    // 扣杀过网兜底跳过率(砸网失误)：4级 0.35、5级 0.15；1-3级由"低难度免兜底"整体承担=0
    public final double smashMissRate;
    // 回合精度惩罚追加偏差(度)：扣杀/接起玩家扣杀后本回合出球更散（落地复位）
    public final double accDropAfterSmash;
    // 接玩家扣杀的整拍反应概率：来球判为扣杀时整拍只掷一次——通过才正常反应接球，
    // 拒绝则本拍全程不动（5级60%、4级40%，其余按档递减），满级不走此门
    public final double smashReceiveChance;

    protected AIDifficultyProfile(int level, double coeff, double reactionGate, double jumpGate,
                                  double inRingBack, double inRingMid, double earlyBack, double earlyMid,
                                  double lowSmashGate, double heavyScale, double smashMissRate,
                                  double accDropAfterSmash, double smashReceiveChance) {
        this.level = level;
        this.coeff = coeff;
        this.reactionGate = reactionGate;
        this.jumpGate = jumpGate;
        this.inRingBack = inRingBack;
        this.inRingMid = inRingMid;
        this.earlyBack = earlyBack;
        this.earlyMid = earlyMid;
        this.lowSmashGate = lowSmashGate;
        this.heavyScale = heavyScale;
        this.smashMissRate = smashMissRate;
        this.accDropAfterSmash = accDropAfterSmash;
        this.smashReceiveChance = smashReceiveChance;
    }

    /** 体力管理（3级起启用；G 键满级另行恒开） */
    public boolean energyManaged() {
        return level >= GameProperties.energyManageDifficulty;
    }

    /** 低难度免过网兜底：1-3级允许砸网/出界（角色特质的"真实失误"来源） */
    public boolean exemptFromNetGuard() {
        return level <= 3;
    }

    /** 出球随机偏差(度)：3级10、4级7、5级5；1-2级沿用历史 ±14 */
    public double shotSpread() {
        switch (level) {
            case 3: return 10.0;
            case 4: return 7.0;
            default: return level >= 5 ? 5.0 : 14.0;
        }
    }

    /** 回合精度惩罚生效时的出球偏差(度)：4级 ±7→±12(更明显)、5级 ±5→±7(轻微) */
    public double shotSpreadWithRallyDrop() {
        return shotSpread() + accDropAfterSmash;
    }

    /** 当前档档案；档位越界按 5 级兜底（高难保底，不静默降级） */
    public static AIDifficultyProfile byDifficulty() {
        switch (GameProperties.difficultyLevel) {
            case 1: return Noob1.INSTANCE;
            case 2: return Normal2.INSTANCE;
            case 3: return Lure3.INSTANCE;
            case 4: return Aggro4.INSTANCE;
            default: return Precise5.INSTANCE;
        }
    }
}

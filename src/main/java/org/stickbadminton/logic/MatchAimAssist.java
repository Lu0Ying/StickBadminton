package org.stickbadminton.logic;

/**
 * 单人 AI 的滑动窗口漏接算法（"淘汰记忆"）：
 * 记录 AI 最近 windowCapacity 个回合的接球结果，最旧的记录随新结果进窗被淘汰；
 * 窗口成功率达标时，下一回合的第一拍可能被预设为失误（出球压网下网或跑位欠步），
 * 失误进窗后成功率回落自动停火——这就是滑动窗口的"淘汰"语义：
 * AI 连续压制玩家时偶尔送一拍，被玩家连续得分时不再补刀。
 *
 * 按回合记账（球落地恰好一次），无挥拍动画与落地的时序竞争：
 * AI 输分=失败(没够到/自己打丢/被对手打死)，AI 赢分=成功。
 * 仅单人本地生效：联机走主机权威出球、双人无 AI，窗口容量为 0 直接短路。
 */
public final class MatchAimAssist {
    // 各难度的回合窗口长度：低难度记忆短(失误来得快)、高难度记忆长(须持续压制才失误)
    private static final int WINDOW_L1 = 4;
    private static final int WINDOW_L2 = 5;
    private static final int WINDOW_L3 = 6;
    private static final int WINDOW_L4 = 8;
    private static final int WINDOW_L5 = 10;
    private static final int WINDOW_MAX = WINDOW_L5;
    // 武装阈值：窗口成功率 ≥ 该值才允许预设失误（阈值均 > 1-1/cap，
    // 刚失误过的窗口无法立刻再次武装，天然防连续放水）
    private static final double[] ARM_THRESHOLD = {0, 1.0, 0.9, 0.85, 0.85, 0.92};
    // 武装后下一回合首拍的失误概率（3级及以下再叠加 MISS_LOW_BONUS）
    private static final double[] MISS_BASE = {0, 0.15, 0.06, 0.03, 0.02, 0.01};
    // 低难度补正：窗口越满额外失误越高——3级接球虽稳，其扣球/处理球的
    // 自身失误靠这项抬高（与1-3级免过网兜底呼应）
    private static final double MISS_LOW_BONUS = 0.15;

    // 滑窗记录（环形，最近的在 cursor-1），窗口满 size==WINDOW_MAX
    private static final boolean[] recent = new boolean[WINDOW_MAX];
    private static int size;
    private static int cursor;
    // 预设：true = 下一回合 AI 首拍被安排失误（出球压网 + 跑位欠步）
    private static boolean armed;

    private MatchAimAssist() {}

    /** 窗口容量（0 = 当前模式不参与漏接：联机/双人/非1-5级） */
    private static int windowCapacity() {
        if (GameProperties.matchMode != 1 || MatchController.getNetClient() != null) return 0;
        return switch (GameProperties.difficultyLevel) {
            case 1 -> WINDOW_L1;
            case 2 -> WINDOW_L2;
            case 3 -> WINDOW_L3;
            case 4 -> WINDOW_L4;
            case 5 -> WINDOW_L5;
            default -> 0;
        };
    }

    /** 每帧读取：本回合首拍是否预设失误（出球压角与跑位欠步共用；非单人恒 false） */
    public static boolean isArmed() {
        return armed && windowCapacity() > 0;
    }

    /** 预设失误已用于出球：消费掉，同一回合的后续拍恢复正常 */
    public static void consume() {
        armed = false;
    }

    /**
     * 球落地（一分结束）：按点位归属记一次窗口成绩并掷下一回合的预设。
     * 单人 AI 在右半场：球落右半场（winner==1）= 左侧玩家得分 = AI 输分
     */
    public static void resolveRally(int winner) {
        int cap = windowCapacity();
        if (cap == 0) return;
        record(winner != 1);
        armed = missRoll(cap); // 掷下一回合首拍预设（武装条件不足时恒 false）
    }

    /** 开局/新一局清窗：记忆不跨局 */
    public static void reset() {
        size = 0;
        cursor = 0;
        armed = false;
    }

    // 推入滑窗：环形写入，最旧记录随新结果淘汰出窗
    private static void record(boolean success) {
        recent[cursor] = success;
        cursor = (cursor + 1) % WINDOW_MAX;
        if (size < WINDOW_MAX) size++;
    }

    // 掷预设：窗口未满不武装（开局热身期）；成功率未达阈值不武装
    private static boolean missRoll(int cap) {
        if (size < cap) return false;
        int hits = 0;
        for (int i = 0; i < cap; i++) {
            // 从 cursor-1 向旧取 cap 条：超出当前难度窗口长度的旧记录被淘汰不计
            int idx = (cursor - 1 - i + WINDOW_MAX) % WINDOW_MAX;
            if (recent[idx]) hits++;
        }
        if ((double) hits / cap < ARM_THRESHOLD[GameProperties.difficultyLevel]) return false;
        double probability = MISS_BASE[GameProperties.difficultyLevel];
        if (GameProperties.difficultyLevel <= 3) probability += MISS_LOW_BONUS * (double) hits / cap;
        return Math.random() < probability;
    }
}

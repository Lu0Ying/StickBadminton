package org.stickbadminton.logic;

/**
 * 连胜记录数据接口：按 AI 难度(1~5)分别保存玩家的当前连胜局数。
 * 当前实现为本地文件（FileWinStreakStore）；未来迁移到 MySQL/SQLite 时，
 * 只需新增一个实现类，并把 getInstance() 里的返回实现替换掉，业务代码无需改动。
 */
public interface WinStreakStore {

    /** 统一获取入口：业务代码一律通过本方法拿存储实例，便于将来替换底层实现 */
    static WinStreakStore getInstance() {
        return FileWinStreakStore.getInstance();
    }

    /** 获取指定难度下玩家当前连胜局数（从未记录过返回 0） */
    int getWinStreak(int difficulty);

    /**
     * 记录一局单人模式结果：玩家获胜则该难度连胜 +1，失败则清零。
     * 实现负责持久化（写文件/写库），调用方无需再手动保存。
     */
    void recordMatchResult(int difficulty, boolean playerWon);
}

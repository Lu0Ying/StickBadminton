package org.stickbadminton.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 连胜记录的本地文件实现：数据存于启动目录 win_streak.properties，
 * 键格式 streak.<难度>=连胜局数，与 MissTrace 写盘保持同一目录约定。
 * 全程单例，内存中只有一份数据；写盘在同步块内完成，避免并发写坏文件。
 */
public final class FileWinStreakStore implements WinStreakStore {

    private static final String FILE_NAME = "win_streak.properties";
    private static final String KEY_PREFIX = "streak.";

    // 单例：多处调用共享同一份内存数据，防止各自读文件造成数值不一致
    private static final FileWinStreakStore INSTANCE = new FileWinStreakStore();

    private final Properties props = new Properties();

    private FileWinStreakStore() {
        load();
    }

    public static FileWinStreakStore getInstance() {
        return INSTANCE;
    }

    /** 启动时读盘：文件不存在视为全部 0，不视为错误 */
    private void load() {
        File file = dataFile();
        if (!file.exists()) return;
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("win_streak 读取失败，将从 0 开始记录: " + e);
        }
    }

    private File dataFile() {
        return new File(System.getProperty("user.dir"), FILE_NAME);
    }

    @Override
    public synchronized int getWinStreak(int difficulty) {
        String value = props.getProperty(KEY_PREFIX + difficulty);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0; // 手改坏的脏数据按 0 兜底，不抛错打断游戏
        }
    }

    @Override
    public synchronized void recordMatchResult(int difficulty, boolean playerWon) {
        int updated = playerWon ? getWinStreak(difficulty) + 1 : 0;
        props.setProperty(KEY_PREFIX + difficulty, String.valueOf(updated));
        save();
    }

    /** 落盘当前内存数据（覆盖写） */
    private void save() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(dataFile()), StandardCharsets.UTF_8)) {
            props.store(writer, "stick badminton win streak (per ai difficulty)");
        } catch (IOException e) {
            System.err.println("win_streak 写盘失败: " + e);
        }
    }
}

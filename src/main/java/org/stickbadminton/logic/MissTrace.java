package org.stickbadminton.logic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 失分诊断记录器（测试工具，默认开启，GameProperties.missTraceEnabled 可关）：
 * 逐帧记录两侧 AI 的决策分支与拒绝原因、挥拍命中/落空事件、起跳事件；
 * 每个球落地时把本球全部记录追加写入启动目录 miss_trace.log。
 * 分析口径：挥拍动画结束未命中=真实挥空；决策帧 rej=xx=算法某道门拒绝；
 * rej 中带 roll/need 的 = 概率层故意不接（varianceRoll 超阈值）。
 * 所有入口在未启用时为常量 false 早退，零分配零开销。
 */
public final class MissTrace {

    // 本球逐帧决策与事件混合缓冲（含 frame 序号，落地时整体落盘）
    private static final List<String> log = new ArrayList<>();
    private static long frame = 0;
    private static int rally = 0;     // 本球累计命中次数（每次击中 +1）
    private static int pointNo = 0;   // 全场已记分球数
    private static boolean armed = false;

    private MissTrace() {}

    public static boolean isEnabled() { return GameProperties.missTraceEnabled; }
    public static long frame() { return frame; }

    /** 逻辑帧推进：由 Badminton.onUpdate 每逻辑帧调用一次（单例） */
    public static void tick() {
        if (GameProperties.missTraceEnabled) frame++;
    }

    /** 落地记分后重置：清空缓冲、开启新球记录 */
    public static void resetRally() {
        if (!GameProperties.missTraceEnabled) return;
        log.clear();
        frame = 0;
        rally = 0;
        armed = true;
    }

    /** 决策帧一行（由 StickMan 每帧在球未冻结时调用） */
    public static void decision(String line) {
        if (!GameProperties.missTraceEnabled || !armed) return;
        log.add(line);
    }

    /** 事件一行：挥拍命中/落空、起跳 */
    public static void event(String line) {
        if (!GameProperties.missTraceEnabled || !armed) return;
        log.add(line);
    }

    /** 本球被击中一次（rally 计数，分析用参考序号） */
    public static void onHit() {
        if (GameProperties.missTraceEnabled) rally++;
    }

    /** 球落地：写盘本球全部记录并重置。winner=得分方(1左/-1右)，landX=落点 */
    public static void onPoint(int winner, double landX) {
        if (!GameProperties.missTraceEnabled || !armed) return;
        pointNo++;
        String path = new File(System.getProperty("user.dir"), "miss_trace.log").getAbsolutePath();
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(path, true), StandardCharsets.UTF_8))) {
            out.println("========== POINT " + pointNo
                    + " winner=" + (winner == 1 ? "LEFT" : "RIGHT")
                    + " rally=" + rally
                    + " landX=" + Math.round(landX)
                    + " frame=" + frame + " ==========");
            for (String s : log) out.println(s);
        } catch (IOException e) {
            System.err.println("miss_trace 写盘失败: " + e);
        }
        System.out.println("[MissTrace] POINT " + pointNo + " -> " + path);
        resetRally();
    }
}

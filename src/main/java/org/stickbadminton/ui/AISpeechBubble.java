package org.stickbadminton.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.UIObject;

import java.util.Random;

/**
 * 单人模式右侧 AI 的头顶对白气泡（漫画式对话框）。
 * 本地 UI 节点、不参与网络同步——单人模式没有联机会话，无需协议下发。
 * 触发点：AI 进攻击球 onSmash（真扣杀同步出挑衅句）、对手回击 onPlayerReturn（惊讶系）、
 * 每分结束 onScore（含失分台词）；显示计时与跟随由持有方每逻辑帧调 tick。
 */
public class AISpeechBubble extends UIObject {

    // 台词优先级：高优先级可打断正在显示的低优先级，反之等待播完（计分台词 > 扣杀台词）
    public static final int PRIO_SMASH = 1;
    public static final int PRIO_SCORE = 2;

    // 台词停留时长（秒）
    private static final double SMASH_LINGER = 1.6;
    private static final double SCORE_LINGER = 2.4;
    // 同优先级台词的最短播完时间：连续扣杀间隔短时先让当前台词说完，避免文字狂闪
    private static final double MIN_HOLD = 1.0;
    // 吃瘪跳脸冷却（秒）：扣杀被接住后该时长内不出挑衅/嘲讽等优势话，防止刚吃瘪就跳脸；
    // 纳尼反应句与失分/劣势句不受影响（可按手感调）
    private static final double TAUNT_COOLDOWN = 8.0;
    // 领先/落后多少分触发对应台词；一局先得 9 分，8 分即赛点
    private static final int LEAD_GAP = 3;
    private static final int MATCH_POINT_SCORE = 8;

    // 气泡左右两侧距屏幕边缘的最小留白：贴墙跑位时整块贴墙显示，不出屏也不压到比分牌
    private static final double HALF_BUBBLE_MIN = 16.0;
    // 气泡顶边相对人物实体顶(y)的偏移：装饰顶在 y-22，留约 8px 空隙不遮角色
    private static final double TOP_OFFSET = 75.0;
    // 气泡水平中心相对人物实体 x 的偏移（头部贴图中心即 x+10）
    private static final double CENTER_OFFSET_X = 10.0;

    // 五组台词按难度独立成文件（Quotes1~Quotes5），本类只保留播放/定位/优先级逻辑；
    // 播放时经 AISpeechQuotes.byDifficulty() 取当前档，说话风格与角色特质配对
    private final AISpeechQuotes quotes;

    private final Random random = new Random();
    private final Label textLabel;
    private final StackPane bubble;

    // 当前台词剩余停留时间（<=0 表示已隐藏）
    private double lingerTimer = 0.0;
    private int currentPriority = 0;
    // AI 扣杀已打出、本分尚未结束：玩家此刻回击即触发"被接住"台词
    private boolean smashInFlight = false;
    // 吃瘪冷却剩余秒数（>0 期间优势话静默），tick 每帧递减
    private double tauntCooldown = 0.0;
    // 气泡宽度只在换台词时量一次：Label.prefWidth 实时按当前字体与文本计算，
    // 同一句台词结果恒定，无需每帧重算
    private double bubbleWidth = 0.0;
    // 人物锚点（tick 每帧刷新）：showLine 即刻定位用，避免换句后到下一帧 tick 之间
    // 气泡以旧坐标/原点闪现一帧
    private double anchorX = 700.0;
    private double anchorY = GameProperties.floorY - GameProperties.playerHeight - 11;

    public AISpeechBubble() {
        // 按当前难度取语录（选人已定难度，气泡全程单档不变）
        quotes = AISpeechQuotes.byDifficulty();
        textLabel = new Label();
        textLabel.setFont(Font.font("微软雅黑", FontWeight.BOLD, 17));
        textLabel.setTextFill(Color.web("#2B2B2B"));
        // 漫画对白框：白底圆角 + 深色描边 + 投影；整块不吃鼠标，避免挡住操作
        textLabel.setStyle("-fx-background-color: rgba(255, 255, 255, 0.94);"
                + " -fx-background-radius: 12px;"
                + " -fx-border-color: #2B2B2B;"
                + " -fx-border-radius: 12px;"
                + " -fx-border-width: 2.5px;"
                + " -fx-padding: 6px 12px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.8, 0, 1);");

        bubble = new StackPane(textLabel);
        bubble.setAlignment(Pos.CENTER);
        bubble.setMaxWidth(StackPane.USE_PREF_SIZE);
        bubble.setMouseTransparent(true);
        bubble.setVisible(false);
    }

    @Override
    public Node getUINode() {
        return bubble;
    }

    /**
     * AI 进攻击球命中：仅真扣杀（扣杀意图+重击）武装在途标记并同步出挑衅台词；
     * 普通回球（轻击/重击但无扣杀意图）不武装——玩家接普通回球不触发"纳尼"，
     * 只有扣杀被接住才说（2026-10-16 用户反馈收紧）。
     * 被体力降级成轻击的"扣杀"（isHeavyShot=false）同样不算扣杀。
     * 吃瘪冷却期内只武装不说话：扣杀刚被接住，随后一段时间不跳脸
     */
    public void onSmash(boolean isSmash) {
        smashInFlight = isSmash;
        if (!isSmash || tauntCooldown > 0.0) return; // 冷却期内只武装不跳脸
        showLine(pick(quotes.smash()), PRIO_SMASH, SMASH_LINGER);
    }

    /**
     * 对手回击了 AI 的扣杀（本分内）：随机一句惊讶系台词，仅触发一次，
     * 并启动吃瘪跳脸冷却。force：回击是本拍独立事件，即便当前仍显示高优先级计分行
     * 也强制顶掉，否则台词被挡即"扣杀被接没对话框"；
     * 球没被接住（直接落地）就走到 onScore 收标记，不出这句。
     */
    public void onPlayerReturn() {
        if (!smashInFlight) return;
        smashInFlight = false;
        showLine(pick(quotes.returned()), PRIO_SMASH, SMASH_LINGER, true);
        tauntCooldown = TAUNT_COOLDOWN; // 吃瘪：随后 TAUNT_COOLDOWN 秒内优势话静默
    }

    /**
     * 每分结束触发台词（aiLost = 本分是否 AI 失分），并收掉在途扣杀标记：
     * AI 失分 → 除非玩家已大幅领先/拿到赛点（转燃系反击腔），否则随机失分不甘腔；
     * AI 得分且大幅领先/拿到赛点 → 嘲讽腔；普通得分不出句。
     */
    public void onScore(int left, int right, boolean aiLost) {
        smashInFlight = false;
        int lead = right - left; // AI 在右半场，right 是 AI 分
        if (aiLost) {
            if (left >= MATCH_POINT_SCORE || lead <= -LEAD_GAP) {
                showLine(pick(quotes.pressure()), PRIO_SCORE, SCORE_LINGER);
            } else {
                showLine(pick(quotes.loss()), PRIO_SCORE, SCORE_LINGER);
            }
        } else if (right >= MATCH_POINT_SCORE || lead >= LEAD_GAP) {
            // 吃瘪冷却期内不出优势话：扣杀刚被接住就领先跳脸被禁止
            if (tauntCooldown > 0.0) return;
            showLine(pick(quotes.taunt()), PRIO_SCORE, SCORE_LINGER);
        }
    }

    /** 比赛结束：立即收起，避免残留盖住结算界面 */
    public void hide() {
        lingerTimer = 0.0;
        currentPriority = 0;
        bubble.setVisible(false);
    }

    /**
     * 每逻辑帧调用：推进停留计时，并把气泡定位到人物头顶。
     *
     * @param anchorX 人物实体 x（左上角）
     * @param anchorY 人物实体 y（脚底）
     */
    public void tick(double anchorX, double anchorY) {
        if (tauntCooldown > 0.0) { // 吃瘪跳脸冷却递减
            tauntCooldown -= GameProperties.frameTime;
            if (tauntCooldown < 0.0) tauntCooldown = 0.0;
        }
        if (lingerTimer > 0.0) {
            lingerTimer -= GameProperties.frameTime;
            if (lingerTimer <= 0.0) {
                currentPriority = 0;
                bubble.setVisible(false);
            }
        }
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        locate();
        // showLine 若因 CSS 未过（宽度为0）暂未显形，这里在量到宽度后补显
        if (lingerTimer > 0.0 && bubbleWidth > 0.0 && !bubble.isVisible()) {
            bubble.setVisible(true);
        }
    }

    // 按当前锚点把气泡摆到头顶（tick 每帧与换台词时都走这里）
    private void locate() {
        if (bubbleWidth <= 0.0) {
            // showLine 若抢在 Label skin 创建（CSS pass）之前会量到 0，这里补量直到有值
            bubbleWidth = bubble.prefWidth(-1);
            if (bubbleWidth <= 0.0) return;
        }
        // 按实际气泡宽度限位：贴墙跑位时整块贴墙显示，不出屏也不压到比分牌下方
        double centerX = clamp(anchorX + CENTER_OFFSET_X, HALF_BUBBLE_MIN + bubbleWidth / 2,
                GameProperties.roomWidth - HALF_BUBBLE_MIN - bubbleWidth / 2);
        bubble.setTranslateX(centerX - bubbleWidth / 2);
        bubble.setTranslateY(anchorY - TOP_OFFSET);
    }

    // 播放台词：低优先级不打断进行中的高优先级；同优先级先让当前台词播完最短时长再换新句。
    // force=true 一并绕过优先级保护与同级防抖——回击/失分这类独立事件必须立即可见，
    // 被仍在播放的高优先级句挡住就等于"没弹对话框"（bug 反馈点）
    private void showLine(String line, int priority, double linger, boolean force) {
        if (!force) {
            if (priority < currentPriority && lingerTimer > 0.0) return;
            if (priority == currentPriority && lingerTimer > MIN_HOLD) return;
        }
        textLabel.setText(line);
        currentPriority = priority;
        lingerTimer = linger;
        bubbleWidth = bubble.prefWidth(-1); // 现量现用；CSS 未过时为 0
        locate();
        // 就位后才显形（首句也不会在原点闪一帧）；宽度未量到则隐藏，由 tick 补显
        if (bubbleWidth > 0.0) bubble.setVisible(true);
    }

    // 常规出句（不强制）
    private void showLine(String line, int priority, double linger) {
        showLine(line, priority, linger, false);
    }

    private String pick(String[] lines) {
        return lines[random.nextInt(lines.length)];
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

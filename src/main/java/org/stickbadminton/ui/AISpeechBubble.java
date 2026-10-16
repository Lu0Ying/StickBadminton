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
 * 触发点：扣杀命中 onSmash、扣杀被玩家回击 onSmashReturned、每分结束 onScore
 * （含失分台词）；显示计时与跟随由持有方每逻辑帧调 tick。
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
    // 领先/落后多少分触发对应台词；一局先得 9 分，8 分即赛点
    private static final int LEAD_GAP = 3;
    private static final int MATCH_POINT_SCORE = 8;

    // 气泡左右两侧距屏幕边缘的最小留白：贴墙跑位时整块贴墙显示，不出屏也不压到比分牌
    private static final double HALF_BUBBLE_MIN = 16.0;
    // 气泡顶边相对人物实体顶(y)的偏移：装饰顶在 y-22，留约 8px 空隙不遮角色
    private static final double TOP_OFFSET = 75.0;
    // 气泡水平中心相对人物实体 x 的偏移（头部贴图中心即 x+10）
    private static final double CENTER_OFFSET_X = 10.0;

    // 扣杀命中：挑衅腔
    private static final String[] SMASH_LINES = {
            "你还差得远呢！",
            "接好咯——！",
            "这一拍，你接得住吗？",
            "太慢了，凡人。",
            "看见了吗？这就是神速！",
            "挣扎毫无意义。",
            "在我的拍下起舞吧！",
            "破绽，全是破绽！",
            "萤火之光，也敢与皓月争辉？",
            "这点程度，挠痒都嫌轻。"
    };

    // AI 领先/拿到赛点：嘲讽腔
    private static final String[] TAUNT_LINES = {
            "你也不过如此。",
            "杂鱼~杂鱼~",
            "就这点能耐？",
            "差距，已经无法弥补了。",
            "跪下仰望吧，凡人。",
            "胜利，不过是理所当然。",
            "你的挣扎，取悦了我。",
            "半场之内，我即是法则。"
    };

    // 被玩家领先/玩家拿到赛点：燃系反击腔
    private static final String[] PRESSURE_LINES = {
            "神！不惧死亡！",
            "你成功激发了我的斗志！",
            "区区劣势，不过是神的试炼！",
            "我的灵魂，还在燃烧！",
            "还没结束……胜负未分！",
            "这具躯壳，还能再战！",
            "逆转的剧本，早已写好。",
            "别高兴得太早，人类！"
    };

    // AI 失分（普通丢分，未到大比分落后/赛点）：不甘腔
    private static final String[] LOSS_LINES = {
            "我还不能在这里倒下……",
            "竟、竟然……",
            "这不过是大意罢了。",
            "下一分，就是你的末日。",
            "别得意忘形，人类。",
            "神明……也会跌落凡尘吗？",
            "区区一分，何足挂齿。",
            "怒火，已经被你点燃了。"
    };

    // AI 扣杀被玩家接住：惊讶/敬意腔
    private static final String[] RETURN_LINES = {
            "可敬的对手……",
            "什么！这不可能！",
            "哦？居然接住了。",
            "有点意思，你这凡人。",
            "这一拍……被看穿了吗？",
            "好球！但下一拍你接不住。",
            "不错，值得我认真几分。",
            "居然挡下了神的一击！？"
    };

    private final Random random = new Random();
    private final Label textLabel;
    private final StackPane bubble;

    // 当前台词剩余停留时间（<=0 表示已隐藏）
    private double lingerTimer = 0.0;
    private int currentPriority = 0;
    // AI 扣杀已打出、本分尚未结束：玩家此刻回击即触发"被接住"台词
    private boolean smashInFlight = false;
    // 气泡宽度只在换台词时量一次：Label.prefWidth 实时按当前字体与文本计算，
    // 同一句台词结果恒定，无需每帧重算
    private double bubbleWidth = 0.0;
    // 人物锚点（tick 每帧刷新）：showLine 即刻定位用，避免换句后到下一帧 tick 之间
    // 气泡以旧坐标/原点闪现一帧
    private double anchorX = 700.0;
    private double anchorY = GameProperties.floorY - GameProperties.playerHeight - 11;

    public AISpeechBubble() {
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

    /** AI 扣杀命中：随机一句挑衅台词，并标记扣杀在途（等待玩家回接） */
    public void onSmash() {
        smashInFlight = true;
        showLine(pick(SMASH_LINES), PRIO_SMASH, SMASH_LINGER);
    }

    /**
     * 玩家回击了 AI 的扣杀（本分内）：随机一句惊讶/敬意台词，仅触发一次。
     * 回击与扣杀同优先级但属独立事件，强制覆盖（不等 MIN_HOLD 防抖窗口）；
     * 玩家没接（球落地）就走到 onScore 收标记，不出这句。
     */
    public void onPlayerReturn() {
        if (!smashInFlight) return;
        smashInFlight = false;
        showLine(pick(RETURN_LINES), PRIO_SMASH, SMASH_LINGER, true);
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
                showLine(pick(PRESSURE_LINES), PRIO_SCORE, SCORE_LINGER);
            } else {
                showLine(pick(LOSS_LINES), PRIO_SCORE, SCORE_LINGER);
            }
        } else if (right >= MATCH_POINT_SCORE || lead >= LEAD_GAP) {
            showLine(pick(TAUNT_LINES), PRIO_SCORE, SCORE_LINGER);
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

    // 播放台词：低优先级不打断进行中的高优先级；同优先级先让当前台词播完最短时长再换新句
    // （force=true 跳过同优先级防抖，用于扣杀→回击这类独立事件的立即接话）
    private void showLine(String line, int priority, double linger, boolean force) {
        if (priority < currentPriority && lingerTimer > 0.0) return;
        if (!force && priority == currentPriority && lingerTimer > MIN_HOLD) return;
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

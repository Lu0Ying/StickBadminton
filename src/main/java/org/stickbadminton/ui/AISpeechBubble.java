package org.stickbadminton.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.GameObject;
import org.stickbadminton.UIObject;

import java.util.Random;

/**
 * 单人模式右侧 AI 的头顶对白气泡（漫画式对话框）。
 * 本地 UI 节点、不参与网络同步——单人模式没有联机会话，无需协议下发。
 * 触发点：AI 进攻击球 onSmash（只登记扣杀剧情不说话）、对手回击/扣杀 onPlayerHit
 * （惊讶/夸赞，经"球心过网"确认才显示）、球落地 onPointEnd（作废未过网的待确认台词）、
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
    // 球心越过球网多少像素算"回击打成"（留容差避开网柱区贴网轨迹）
    public static final double NET_CROSS_MARGIN = 10.0;
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
    // 本分 AI 是否打出过扣杀（onScore 时未被回击 → 成功/自失句的"扣杀剧情"依据）
    private boolean smashStory = false;
    // 本分扣杀是否已被对手回击过（区分"扣杀直接落地"与"对拉后丢分"）
    private boolean smashReturned = false;
    // 待过网确认的台词（null=无）：击球帧入队，球心越过 pendingNetX 才显示——
    // 回击随即下网/砸地（观感"没接住"）在确认前已随落地作废，杜绝"没接扣杀却出被接语录"
    private String pendingLine;
    private int pendingPrio;
    private double pendingLinger;
    private boolean pendingForce;
    // 本条是否"回击扣杀"句：确认显示时才启动吃瘪冷却（真接住才算吃瘪）
    private boolean pendingIsReturn;
    // 过网确认线（按击球方向取网位置±容差）：+ 向右越过 / - 向左越过
    private double pendingNetX;
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
     * AI 真扣杀出拍命中：登记本分"扣杀剧情"，**出拍瞬间不说话**（2026-10-16 用户要求：
     * 不要立马说成功的话）——结果由 onScore 分岔：没被回击且得分 → 成功句、砸网/出界
     * → 自失句；被回击则由 onPlayerHit 出惊讶句。普通回球清剧情，不登记
     */
    public void onSmash(boolean isSmash) {
        smashStory = isSmash;
        smashReturned = false;
    }

    /**
     * 对手一次命中（左半场：人类玩家，或按 G 后的满级 AI）：
     * - 回击了右侧 AI 的在途扣杀 → 惊讶腔（首次回击才入队）；
     * - hitSmash=true 对手打出真扣杀（击出角为下压扣球档+快而下坠）→ 夸赞腔（惊喜话），
     *   仅玩家扣杀才出，普通重击/挑球不出。
     * 两者都先入队、待球心越过球网才显示——回击随即下网/砸地的（观感"没接住"）
     * 在确认前已随落地作废（onPointEnd），杜绝"没接扣杀却出被接语录"
     */
    public void onPlayerHit(boolean hitSmash) {
        if (smashStory && !smashReturned) { // 回击在途扣杀：惊讶腔
            smashReturned = true;
            queueLine(pick(quotes.returned()), PRIO_SMASH, SMASH_LINGER, true);
            return;
        }
        if (hitSmash && lingerTimer <= 0.0 && pendingLine == null) { // 玩家扣杀：夸赞腔（空闲才夸，防连打刷屏）
            queueLine(pick(quotes.praise()), PRIO_SMASH, SMASH_LINGER, false);
        }
    }

    /** 球落地（Badminton.onHitGround）：作废待确认台词——这拍没打成，话咽回去 */
    public void onPointEnd() {
        pendingLine = null;
    }

    /**
     * 每分结束：先捕获本分剧情再清标记（冷却分支若提前 return 也不能把标记带进下一分），
     * 按剧情分岔出句：
     * 1) 任意一方赛点(8分，未终局) → 打气腔（压过一切）；终局9分不出打气句
     * 2) AI 扣杀没被回击就落地：得分 → 扣杀成功句（结果落地才说）；
     *    砸网/出界（落 AI 侧）→ 自失句
     * 3) AI 失分（对拉/常规）→ 玩家赛点或大比分落后走压力腔，否则失分不甘腔
     * 4) AI 得分且大比分领先（非扣杀直接得分）→ 嘲讽腔；普通得分/被回击后对拉取胜不出句
     * 成功与嘲讽句受吃瘪冷却约束（扣杀刚被接住不跳脸），负情绪句不受影响
     */
    public void onScore(int left, int right, boolean aiLost) {
        int lead = right - left; // AI 在右半场，right 是 AI 分
        boolean directKill = smashStory && !smashReturned; // 扣杀没被回击就落地
        smashStory = false;
        smashReturned = false;
        boolean matchPoint = (left >= MATCH_POINT_SCORE || right >= MATCH_POINT_SCORE)
                && left < 9 && right < 9;
        boolean playerPressure = left >= MATCH_POINT_SCORE || lead <= -LEAD_GAP;

        if (matchPoint) { // 任意一方赛点：打气
            showLine(pick(quotes.matchPoint()), PRIO_SCORE, SCORE_LINGER);
        } else if (aiLost) {
            if (directKill) { // 扣杀砸网/出界：自己的失误
                showLine(pick(quotes.smashMiss()), PRIO_SCORE, SCORE_LINGER);
            } else if (playerPressure) {
                showLine(pick(quotes.pressure()), PRIO_SCORE, SCORE_LINGER);
            } else {
                showLine(pick(quotes.loss()), PRIO_SCORE, SCORE_LINGER);
            }
        } else if (directKill || lead >= LEAD_GAP) {
            if (tauntCooldown > 0.0) return; // 吃瘪冷却：刚被接住不跳脸
            showLine(pick(directKill ? quotes.smash() : quotes.taunt()),
                    PRIO_SCORE, SCORE_LINGER);
        }
        // 普通得分、扣杀被回击后的对拉取胜（未大比分领先）：保持沉默
    }

    /** 比赛结束：立即收起，避免残留盖住结算界面 */
    public void hide() {
        lingerTimer = 0.0;
        currentPriority = 0;
        pendingLine = null;
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
        // 待确认台词：球心越过球网 = 这一拍真打成了才显示；没过网就落地的
        // 已在 onPointEnd 作废（观感"没接住"绝不出被接语录）
        if (pendingLine != null) {
            GameObject ball = getParentRoom() != null ? getParentRoom().getObject("badminton") : null;
            if (ball == null) {
                pendingLine = null; // 换分过渡球不存在：不显示
            } else if (ball.getCenterX() >= pendingNetX) {
                String line = pendingLine;
                int prio = pendingPrio;
                double linger = pendingLinger;
                boolean force = pendingForce;
                boolean isReturn = pendingIsReturn;
                pendingLine = null;
                if (isReturn) tauntCooldown = TAUNT_COOLDOWN; // 确认真被接住：跳脸冷却从这一刻起算
                showLine(line, prio, linger, force);
            }
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

    /**
     * 入队待过网确认的台词：击球帧不能立即显示——回击随即下网/砸地时
     * 观感就是"没接住"，先弹惊讶句即成 bug。isReturn 标记"回击 AI 扣杀"句，
     * 待确认显示时才启动吃瘪冷却
     */
    private void queueLine(String line, int priority, double linger, boolean force) {
        pendingLine = line;
        pendingPrio = priority;
        pendingLinger = linger;
        pendingForce = force;
        pendingIsReturn = force; // 现仅回击句以 force 入队；夸赞句 force=false
        // 对手侧击球方向朝右（左半场击出）：越过网+容差即确认
        pendingNetX = GameProperties.netPosition + NET_CROSS_MARGIN;
    }

    private String pick(String[] lines) {
        return lines[random.nextInt(lines.length)];
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

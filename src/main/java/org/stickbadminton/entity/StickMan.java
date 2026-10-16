package org.stickbadminton.entity;
import org.stickbadminton.Sprite;
import org.stickbadminton.entity.Badminton;
import org.stickbadminton.entity.ComputerDecision;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.logic.MatchAimAssist;
import org.stickbadminton.logic.MatchController;
import org.stickbadminton.logic.MissTrace;
import org.stickbadminton.ui.AISpeechBubble;

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;

public class StickMan extends GameObject {
    public int characterType = 0; // 1 ~ 5
    public boolean isAIControlled = false;
    // 调试用：G 键切换后 p1 以满级（难度 1.0）AI 出战，不受选人房难度影响
    public boolean aiMaxLevel = false;
    private GameObject bodyIdle;
    private GameObject bodyMoving;
    private GameObject rightHand;
    private GameObject leftHandIdle;
    private GameObject decoration;
    private int side; // 1->（画面左侧）朝向右边; -1->（画面右侧）朝向左边
    public static final int sideLeft = -1;
    public static final int sideRight = 1;
    int getSide() { return side; }

    public boolean isMoving = false;
    public boolean isJumping = false;
    private double jumpCooldownTimer = 0;
    public boolean isShotting = false;
    public boolean shotType = false; // false -> 上方击球, true -> 下方击球
    public boolean isHeavyShot = false;
    // 本拍是否扣杀意图（ComputerDecision.superShot 路径）：接触层据此把起手相位
    // 算出的朝上角镜像成下压角，进 hitSpeedFor 的 2500 扣球档；平抽/防守重击不置位
    private boolean aiSmashIntent = false;
    // AI 击球角度随机源：仅接触层本地生成偏差；联机权威出球角由主机 HIT 消息下发，双端不分叉
    private final java.util.Random shotVariance = new java.util.Random();
    // 扣杀失误率/回合精度惩罚等档位特质已移入难度档案（Aggro4 0.35+5°、Precise5 0.15+2°…），
    // 本类经 AIDifficultyProfile.byDifficulty() 读取，不再持档位常量
    private final AIDifficultyProfile profile = AIDifficultyProfile.byDifficulty();
    // 跳杀风存：起跳后先攒 6 帧(~0.1s，肩膀抬约68px)再挥拍，保证"先跳再扣"
    private int smashWindupFrames = 0;
    private boolean smashWindupDone = false;
    // 起跳帧锁定的扣杀快照：风存结束帧恒按重击+扣杀意图出招
    private boolean smashWindupPending = false;
    public boolean hasShotted = false; // 自本次击球动画开始以来是否打过球
    // 4-5级：本回合已触发"自己扣杀或接起玩家扣杀" → 出球偏差加大（见 shotVarianceSpread），
    // 回合结束由 MatchController 落地判分时 clearRallyAccDrop 复位
    private boolean rallyAccDrop = false;
    public static final boolean shotTypeUp = false;
    public static final boolean shotTypeDown = true;
    private double shotCooldownTimer = 0;
    public boolean isReadyingServe = false;
    public double energyRemain = 100.0; // 满格体力 = 100

    /**
     * 当前实际移速：体力不足 fullSpeedEnergy 时线性衰减。
     * AI 决策层（走位预判/跳跃可达性）必须用同一公式，否则低体力时会误判“跑得到”。
     * 5级 AI 不再叠 ×1.1 属性倍率（已取消），与 4 级完全一致，仅靠扣杀风格分层区分。
     */
    public double getEffectiveMoveSpeed() {
        double baseMoveSpeed = GameProperties.moveSpeed;
        if (GameProperties.infiniteEnergyMode)
            return baseMoveSpeed;
        return energyRemain > GameProperties.fullSpeedEnergy ? baseMoveSpeed
                : (GameProperties.moveSpeed0 + (baseMoveSpeed - GameProperties.moveSpeed0)
                        * energyRemain / GameProperties.fullSpeedEnergy);
    }

    /**
     * AI 出球过网安全网：出球角由挥拍扫掠相位决定（=球相对肩部极角的切线方向），
     * 决策层无法精确预知，低球/贴网球常算出“砸网”的下压角。
     * 在击球瞬间按接触点步进物理仿真，会撞网就把出球角每次向“正上方”一侧调 5°
     * （右半场+5、左半场-5，绕360°均朝270°收敛），最多试24次；
     * 本就能过网的出球一次仿真即通过、零干预。仅 AI 生效，人类玩家保留原物理手感。
     */
    private double adjustAINetSafeAngle(double hitAngle, double fromX, double fromY) {
        double stepDeg = (side == sideRight) ? -5.0 : 5.0;
        for (int k = 0; k <= 24; k++) {
            double candidate = hitAngle + stepDeg * k;
            while (candidate < 0) candidate += 360;
            while (candidate >= 360) candidate -= 360;
            // 接触点已在挥拍圆弧上，边距12只留轻微相位/步进误差
            if (Badminton.launchClearsNet(fromX, fromY, candidate,
                    Badminton.hitSpeedFor(isHeavyShot, candidate, fromX), 12.0))
                return candidate;
        }
        return hitAngle; // 24次仍无解（极罕见），维持原角度
    }

    /**
     * AI 击球随机偏差：每拍给出手角度叠加 ±spread°，让球路不可预测、对手难以预判。
     * 精度按难度递进（原 1-2级±14°、3-5级±5° 两档）：3级精度减小至±10°、
     * 4级微微降低至±7°、5级与满级准度不变±5°——1-2级维持原±14°；
     * 且 1-3 级不走过网兜底(见 shouldGuardNet)，下网/出界属低难度允许的失误。
     * 仅 AI 生效，人类手感不变
     */
    private double applyShotVariance(double hitAngle) {
        // 出球偏差取自档位档案：3级10°、4级7°、5级5°、1-2级14°（满级=5级档案±5°）
        double spread = (aiMaxLevel || GameProperties.difficultyLevel >= 5) ? 5.0
                : profile.shotSpread();
        // 4-5级回合精度惩罚：本回合已扣杀/已接起玩家扣杀 → 档案追加偏差
        // （4级 ±7→±12 更明显，5级 ±5→±7 轻微；满级不参与，联机不分叉）
        if (rallyAccDrop && !aiMaxLevel) {
            spread = profile.shotSpreadWithRallyDrop();
        }
        hitAngle += (shotVariance.nextDouble() - 0.5) * 2.0 * spread;
        while (hitAngle < 0) hitAngle += 360;
        while (hitAngle >= 360) hitAngle -= 360;
        return hitAngle;
    }

    /**
     * 本拍是否需要过网兜底：1-3级低难度不兜底——出球角可能压不过网，直接砸网，
     * 这就是给玩家的"接得到但 AI 会自己失误"的真实感；4/5级仅扣杀按各自失误率
     * 概率跳过兜底(4激进0.35、5精准0.15)，普通回球恒兜底。
     * 滑动窗口预设的失误拍(MatchAimAssist)不兜底——预设失误必须真的失误。
     * 仅单人模式按难度生效——联机与 G 键满级恒走兜底，双端判定不受随机影响
     */
    private boolean shouldGuardNet() {
        if (aiMaxLevel) return true;
        // 预设失误拍在调用点已被 netWardMissAngle 分支截走并消费，此处只处理常规拍；
        // 1-3级档案豁免兜底（低难度允许砸网/出界），4/5级仅扣杀按档案失误率概率跳过
        if (profile.exemptFromNetGuard()) return false;
        return !skipNetGuardOnSmash();
    }

    /**
     * 滑动窗口预设失误的出球角：朝网的下压角（±15°）——肩点在网顶之下，
     * 下压从站位/起跳击打必然砸网或先落自己半场，视觉即"AI 这一拍自己失误了"。
     * 仅单人本地生效
     */
    private double netWardMissAngle() {
        double base = (side == sideRight) ? 50.0 : 130.0; // 左玩家朝+x、右玩家(AI)朝-x 的下压
        return base + (shotVariance.nextDouble() - 0.5) * 30.0;
    }

    /**
     * 4-5级回合精度惩罚的触发判定：本拍命中时若为"自己扣杀"或"接起玩家扣杀
     * （来球合速度>900 且竖直下坠>350，与 isIncomingSmash 同口径）"，
     * 置位本回合标记——惩罚作用于该拍之后的出球（扣杀这一拍本身不受影响），
     * 落地判分时复位。仅单人 4/5 级；须在 heavyHit/lightHit 改速之前调用
     * （读取的是来球速度）
     */
    private void updateRallyAccDrop(Badminton badminton) {
        // 档案 accDrop=0 的档(1-3级)直接退出；4级+5.0°、5级+2.0°才参与
        if (!isAIControlled || aiMaxLevel || rallyAccDrop) return;
        if (profile.accDropAfterSmash <= 0.0) return;
        boolean ownSmash = aiSmashIntent && isHeavyShot;
        double incomingSpeed = Math.sqrt(badminton.speedX * badminton.speedX
                + badminton.speedY * badminton.speedY);
        boolean receivedSmash = incomingSpeed > ComputerDecision.SMASH_SPEED_THRESHOLD
                && badminton.speedY > ComputerDecision.SMASH_VY_THRESHOLD;
        if (ownSmash || receivedSmash) rallyAccDrop = true;
    }

    /** 回合结束（球落地）：精度惩罚复位，下一回合不受上一回合扣杀影响 */
    public void clearRallyAccDrop() {
        rallyAccDrop = false;
    }

    /**
     * 4/5级AI 扣杀是否跳过过网兜底（概率砸网失误）——失误率取档位档案
     * （Aggro4 35%、Precise5 15%；1-3级整体免兜底、满级恒兜底不走此路）。
     * 仅单人模式的4/5级会走到这里——联机与G键满级恒不跳过，双端判定不受随机影响。
     */
    private boolean skipNetGuardOnSmash() {
        if (aiMaxLevel || !isAIControlled || !aiSmashIntent || !isHeavyShot) return false;
        if (profile.smashMissRate <= 0.0) return false;
        return shotVariance.nextDouble() < profile.smashMissRate;
    }

    /**
     * 起手前校验（AI 专用）：本拍是否真能扫到球——用决策层当帧真实状态预演。
     * heavy=本拍重击(风存拍恒重击/普通拍看决策)，jumpVy0=风存拍的当前垂直速度(站定0)。
     * 人类玩家不出校验（手感不变）；默认构造 decision(traceSide=0) 直接放行
     */
    private boolean canSwingHit(ComputerDecision decision, boolean windupPending) {
        if (!isAIControlled) return true;
        boolean heavy = windupPending || decision.isHeavyhit;
        return decision.connectableThisSwing(heavy, isJumping ? speedY : 0);
    }

    /**
     * 失分诊断：命中事件（含档位表判出的出球初速，供与 SWING_MISS/落地对账）。
     * 仅 MissTrace 开启时拼串；调用点在 hasShotted 置位后，不影响任何游戏状态。
     */
    private void traceHit(Badminton badminton, double hitAngle) {
        if (!MissTrace.isEnabled()) return;
        double speed = Badminton.hitSpeedFor(isHeavyShot, hitAngle, badminton.getCenterX());
        MissTrace.event(String.format(
                "E HIT f=%d s=%s %s %s | ang=%.0f spd=%.0f | ball %.0f,%.0f -> %.0f,%.0f",
                MissTrace.frame(), side > 0 ? "L" : "R",
                aiSmashIntent ? "smash" : "normal",
                isHeavyShot ? "heavy" : "light",
                hitAngle, speed,
                badminton.getCenterX(), badminton.getCenterY(),
                badminton.speedX, badminton.speedY));
        MissTrace.onHit();
    }

    /**
     * 单人模式右侧 AI 进攻击球（上/下方击球命中）：仅真扣杀（扣杀意图+重击）
     * 才武装气泡的在途标记，**出拍瞬间不说话**——被回击出惊讶句、直接落地得分出成功句、
     * 砸网出界出自失句，均由 onScore 按本分剧情分岔。台词按难度档取 Quotes1~5。
     * 气泡只在单人房创建（双人/联机 getUiObject 返回 null 即静默跳过）；
     * side==sideLeft 的值 -1 对应右半场，左侧 G 键满级 AI 不参与说话
     */
    private void notifySmashSpeech() {
        if (!isAIControlled || side != sideLeft) return;
        if (inRoom == null) return;
        if (inRoom.getUiObject("ai_speech_bubble") instanceof AISpeechBubble bubble) {
            bubble.onSmash(aiSmashIntent && isHeavyShot);
        }
    }

    /**
     * 左半场"对手侧"击球命中时的台词分岔（只看半场不看谁在控——人类玩家，或按 G 后的满级 AI）：
     * 回击了右侧 AI 的在途扣杀 → 惊讶系台词；对手真扣杀（重击+扣球档下压击角）→ 夸赞句，
     * 普通重击/挑球不出惊喜话。两句都由气泡入队、待球心过网才显示，没过网就落地的
     * 随落地作废——"没接扣杀却出被接语录"由此杜绝。
     * 旧版加 isAIControlled 门槛会把 G 键模式的左 AI 挡掉，导致整个回击链不触发
     */
    private void notifyPlayerReturn(double hitAngle) {
        if (side != sideRight) return;
        if (inRoom == null) return;
        if (!(inRoom.getUiObject("ai_speech_bubble") instanceof AISpeechBubble bubble)) return;
        bubble.onPlayerHit(isPlayerSmash(hitAngle));
    }

    /**
     * 本拍是否"真扣杀"：击角归一化后落在 180° 内的下压扣球档（hitSpeedFor 扣球分支）
     * 且为重击——与 AI 侧 aiSmashIntent&&isHeavyShot 同义（人类无扣杀意图字段）。
     * 命中处的 hitAngle 已是物理终角（AI 的扣杀镜像与角度修正都在其之前执行），
     * 故人类/AI 满级 G 键共用同一判据
     */
    private boolean isPlayerSmash(double hitAngle) {
        if (!isHeavyShot) return false;
        while (hitAngle < 0) hitAngle += 360;
        while (hitAngle >= 360) hitAngle -= 360;
        return hitAngle < 180;
    }

    /**
     * 拍面扫掠命中：球心到"拍轴心→拍头"线段的最短距离 ≤ racketRadius 即命中。
     * 球拍是有面积的拍面而非一个点——原判定只查拍头点，球从拍杆中途穿过时挥空，
     * 扣杀命中窗口只有 1-2 帧；线段口径把整个拍杆扫过区都算命中，更符合真实挥拍。
     * 人类与 AI 共用（判定公平一致），联机由主机权威执行不分叉
     */
    private boolean racketSweepHit(double ballCX, double ballCY, double racketX, double racketY) {
        double pivotX = getCenterX();
        double pivotY = getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight;
        double segX = racketX - pivotX, segY = racketY - pivotY;
        double lenSq = segX * segX + segY * segY;
        double t = lenSq == 0 ? 0 : ((ballCX - pivotX) * segX + (ballCY - pivotY) * segY) / lenSq;
        if (t < 0) t = 0; else if (t > 1) t = 1; // 投影夹紧在线段内
        double nearX = pivotX + segX * t, nearY = pivotY + segY * t;
        double dx = ballCX - nearX, dy = ballCY - nearY;
        return dx * dx + dy * dy <= GameProperties.racketRadius * GameProperties.racketRadius;
    }

    public StickMan(int side, int characterType) {
        super("stickman1", Sprite.loadImage("/images/stickman/stickman_head1.png")); //head
        this.side = side;

        setCenterPosition(10, 10);

        bodyIdle = new GameObject("bodyIdle", Sprite.loadImage("/images/stickman/stickman_body_idle.png"));
        bodyIdle.getEntity().setScaleOrigin(new Point2D(12, 0));
        bodyMoving = new GameObject("bodyMoving", Sprite.loadImage("/images/stickman/stickman_body_moving.png"), 32, Duration.seconds(0.15));
        bodyMoving.getEntity().setScaleOrigin(new Point2D(16, 0));

        rightHand = new GameObject("right_hand", Sprite.loadImage("/images/stickman/stickman_righthand.png"));
        rightHand.setCenterPosition(1, 1);

        leftHandIdle = new GameObject("left_hand", Sprite.loadImage("/images/stickman/stickman_lefthand_idle.png"));
        leftHandIdle.setCenterPosition(1, 1);

        this.characterType = characterType;
        decoration = new GameObject("decoration", Sprite.loadImage("/images/stickman/stickman_decoration" + characterType + ".png"));
        decoration.getEntity().setScaleOrigin(new Point2D(52, 0));
    }

    @Override
    public void activate() {
        super.activate();
        bodyIdle.activate();
        bodyMoving.activate();
        rightHand.activate();
        leftHandIdle.activate();
        decoration.activate();
    }

    @Override
    public void deactivate() {
        bodyIdle.deactivate();
        bodyMoving.deactivate();
        rightHand.deactivate();
        leftHandIdle.deactivate();
        decoration.deactivate();
        super.deactivate();
    }

    private void bindBodyPart() { //显示层面
        if (side < 0) {
            this.entity.setScaleX(-1);
            bodyIdle.getEntity().setScaleX(-1);
            rightHand.getEntity().setScaleX(-1);
            leftHandIdle.getEntity().setScaleX(-1);
            bodyMoving.getEntity().setScaleX(-1);
            decoration.getEntity().setScaleX(-0.66);
            decoration.getEntity().setScaleY(0.66);
        }
        else {
            this.entity.setScaleX(1);
            bodyIdle.getEntity().setScaleX(1);
            rightHand.getEntity().setScaleX(1);
            leftHandIdle.getEntity().setScaleX(1);
            bodyMoving.getEntity().setScaleX(1);
            decoration.getEntity().setScaleX(0.66);
            decoration.getEntity().setScaleY(0.66);
        }

        bodyIdle.setX(x - 2 - 6 * side);
        bodyIdle.setY(y + 20);
        bodyIdle.speedX = speedX;
        bodyIdle.speedY = speedY;
        bodyMoving.setX(x - 6 - 8 * side);
        bodyMoving.setY(y + 20);
        bodyMoving.speedX = speedX;
        bodyMoving.speedY = speedY;
        decoration.setX(x - 42 - 2 * side);
        decoration.setY(y - 22);
        decoration.speedX = speedX;
        decoration.speedY = speedY;

        rightHand.setX(x + 9 - 3 * side);
        rightHand.setY(y + 25);
        if (isShotting) {
            if (shotType == shotTypeUp) { // 上方击球
                double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                double targetAngle = 180 + 45 + GameProperties.hitAreaAngleUp;
                double nowAngle;
                if (deltaT < GameProperties.shotAnimationTime)
                    nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime);
                else if (deltaT < 2 * GameProperties.shotAnimationTime)
                    nowAngle = targetAngle - (deltaT - GameProperties.shotAnimationTime)
                            * ((targetAngle - 180) / GameProperties.shotAnimationTime);
                else nowAngle = 180;
                rightHand.setRotation(nowAngle * side);
            }
            else { // 下方击球
                double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                double targetAngle = 180 - 145 - GameProperties.hitAreaAngleDown;
                double nowAngle;
                if (deltaT < GameProperties.shotAnimationTime2)
                    nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime2);
                else if (deltaT < 2 * GameProperties.shotAnimationTime2)
                    nowAngle = (deltaT - GameProperties.shotAnimationTime2)
                            * ((targetAngle - 180) / GameProperties.shotAnimationTime2);
                else nowAngle = 180;
                rightHand.setRotation(nowAngle * side);
            }
        }
        else
            rightHand.setRotation(180 * side);
        rightHand.speedX = speedX;
        rightHand.speedY = speedY;

        leftHandIdle.setX(x + 9 - 3 * side);
        leftHandIdle.setY(y + 25);
        leftHandIdle.speedX = speedX;
        leftHandIdle.speedY = speedY;

        if (isMoving) {
            bodyIdle.setVisible(false);
            bodyMoving.setVisible(true);
        }
        else {
            bodyIdle.setVisible(true);
            bodyMoving.setVisible(false);
        }
    }

    @Override
    public void onUpdate() {
        isMoving = false;
        speedX = 0;
        GameObject badmintonObj = inRoom.getObject("badminton");
        Badminton badminton = null;
        if (badmintonObj != null) {
            badminton = (Badminton) badmintonObj;
        }
        GameObject opponentObj = null;
        if (side == sideLeft) { // 右半场
            opponentObj = inRoom.getObject("stickman_left");
        }
        else { // 左半场
            opponentObj = inRoom.getObject("stickman_right");
        }
        ComputerDecision decision;
        if (isAIControlled && badminton != null && opponentObj != null) {
            // aiMaxLevel 作为构造参数传入：决策在构造器内完成，事后改字段已来不及；
            // 体力与实际移速一并传入，供体力管理决策（跳跃预算/重击预算/预判）使用；
            // 对手(玩家)体力传入供 5 级"玩家残血(<30)才扣杀"体力门判定
            decision = new ComputerDecision(aiMaxLevel, side, getCenterX(), getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight,
                    isShotting, isJumping, badminton.getCenterX(), badminton.getCenterY(), badminton.speedX, badminton.speedY, opponentObj.getCenterX(), opponentObj.getCenterY(),
                    energyRemain, getEffectiveMoveSpeed(),
                    (opponentObj instanceof StickMan opp) ? opp.energyRemain : 100.0);
        }
        else decision = new ComputerDecision();

        // 水平移动相关：与 AI 决策层共用同一移速公式（体力插值）
        double moveSpeed = getEffectiveMoveSpeed();
        if (isAIControlled) {
            if (decision.isMoveRight == true) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = moveSpeed;
            } else if (decision.isMoveLeft == true) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = -moveSpeed;
            }
        }
        else {
            if ((KeyInput.isKeyHolding(KeyCode.D) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.L) && side == sideLeft)) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = moveSpeed;
            } else if ((KeyInput.isKeyHolding(KeyCode.A) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.J) && side == sideLeft)) {
                if (getCenterY() + GameProperties.playerHeight >= GameProperties.floorY)
                    isMoving = true;
                speedX = -moveSpeed;
            }
        }

        if (side == 1) { // 左半场
            if (speedX < 0 && getCenterX() + speedX * GameProperties.frameTime <= GameProperties.playFieldLeft + GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.playFieldLeft + GameProperties.playerWidth/2 - spriteCenterX;
            }
            else if (speedX > 0 && getCenterX() + speedX * GameProperties.frameTime >= GameProperties.netPosition - GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.netPosition - GameProperties.playerWidth/2 - spriteCenterX;
            }
        }
        else { // 右半场
            if (speedX < 0 && getCenterX() + speedX * GameProperties.frameTime <= GameProperties.netPosition + GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.netPosition + GameProperties.playerWidth/2 - spriteCenterX;
            }
            else if (speedX > 0 && getCenterX() + speedX * GameProperties.frameTime >= GameProperties.playFieldRight - GameProperties.playerWidth/2) {
                speedX = 0;
                x = GameProperties.playFieldRight - GameProperties.playerWidth/2 - spriteCenterX;
            }
        }
        // 体力恢复
        if (!isJumping && !GameProperties.infiniteEnergyMode) {
            if (speedX == 0)
                energyRemain += GameProperties.idleEnergyRecover * GameProperties.frameTime;
            else energyRemain += GameProperties.moveEnergyRecover * GameProperties.frameTime;
            if (energyRemain > 100.0)
                energyRemain = 100.0;
        }

        if (isReadyingServe) {
            if (side == 1 && x + speedX * GameProperties.frameTime
                    >= GameProperties.netPosition - GameProperties.serveLineDistance - GameProperties.playerWidth/2 - spriteCenterX) { // 左半场
                x = GameProperties.netPosition - GameProperties.serveLineDistance - GameProperties.playerWidth/2 - spriteCenterX;
                speedX = 0;
            }
            else if (side == -1 && x + speedX * GameProperties.frameTime
                    <= GameProperties.netPosition + GameProperties.serveLineDistance - 10 + GameProperties.playerWidth/2 - spriteCenterX){ // 右半场
                x = GameProperties.netPosition + GameProperties.serveLineDistance - 10 + GameProperties.playerWidth/2 - spriteCenterX;
                speedX = 0;
            }
        }

        // 竖直移动相关
        if (isJumping) {
            jumpCooldownTimer -= GameProperties.frameTime;
            if (jumpCooldownTimer <= 0) {
                isJumping = false;
                jumpCooldownTimer = 0;
            }
        }
        else if (!GameProperties.infiniteEnergyMode && energyRemain < GameProperties.jumpEnergy) {
            // 体力不足时禁用跳跃
            // do nothing
        }
        else if (isReadyingServe) {
            // 发球时禁用跳跃
            // do nothing
        }
        else if (isAIControlled) {
            // !isJumping 防二段跳：风存/决策每帧可能重复置位 isJump，
            // 空中期间若再起跳会重置初速并重复扣体力（白跳+体力双倍消耗）
            if (decision.isJump == true && !isJumping) {
                if (!GameProperties.infiniteEnergyMode)
                    energyRemain -= GameProperties.jumpEnergy;
                isJumping = true;
                jumpCooldownTimer = GameProperties.jumpCooldown;
                speedY = -GameProperties.jumpSpeedY;
                if (MissTrace.isEnabled()) // 带 format 的实参先于方法早退求值，必须外层判断
                    MissTrace.event(String.format("E JUMP f=%d s=%s y=%.0f e=%.0f",
                            MissTrace.frame(), side > 0 ? "L" : "R", getCenterY(), energyRemain));
            }
        }
        else if ((KeyInput.isKeyHolding(KeyCode.W) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.I) && side == sideLeft)) {
            if (!GameProperties.infiniteEnergyMode)
                energyRemain -= GameProperties.jumpEnergy;
            isJumping = true;
            jumpCooldownTimer = GameProperties.jumpCooldown;
            speedY = -GameProperties.jumpSpeedY;
        }

        if (getCenterY() + GameProperties.playerHeight + speedY * GameProperties.frameTime >= GameProperties.floorY) {
            speedY = 0;
            y = GameProperties.floorY - GameProperties.playerHeight - spriteCenterY;
        }
        else
            speedY += GameProperties.jumpGravity;

        // 击球相关
        if (isShotting) { // 击球动画中
            shotCooldownTimer -= GameProperties.frameTime;
            if (shotCooldownTimer <= 0) {
                isShotting = false;
                shotCooldownTimer = 0;
                // 诊断：整段挥拍动画结束仍未命中 = 真实挥空（含球已过网/相位窗口错位）
                if (MissTrace.isEnabled() && !hasShotted && badminton != null)
                    MissTrace.event(String.format(
                            "E SWING_MISS f=%d s=%s | ball %.0f,%.0f,%.0f,%.0f | ai %.0f,%.0f | up=%b",
                            MissTrace.frame(), side > 0 ? "L" : "R",
                            badminton.getCenterX(), badminton.getCenterY(),
                            badminton.speedX, badminton.speedY,
                            getCenterX(), getCenterY(), shotType == shotTypeUp));
            }
            else if (hasShotted == false && badminton.isShotable
                    && (badminton.getCenterX() - GameProperties.netPosition) * side <= 0){
                if (shotType == shotTypeUp) { // 上方击球
                    double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                    if (deltaT < GameProperties.shotAnimationTime)
                    {
                        double targetAngle = 180 + 45 + GameProperties.hitAreaAngleUp;
                        double nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime);
                        nowAngle = nowAngle + 45;
                        if (side == -1)
                            nowAngle = 180 - nowAngle;
                        while (nowAngle < 0)
                            nowAngle += 360;
                        while (nowAngle > 360)
                            nowAngle -= 360;
                        if (badmintonObj != null) {
                            double racketX = getCenterX() + Math.cos(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                            double racketY = getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight
                                    + Math.sin(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                            // 击球判定用球心对球拍点（中心对中心）：旧版拿球左上角 getX/Y 去比
                            // 按玩家中心算出的球拍点，存在约10px系统偏移，墙边AI被空气墙卡住无法
                            // 再靠近时会把够得到的球误判成接不到。改球心即"球拍中心击球、减少边缘击球"
                            // 贴身兜底：球心贴近击球轴心(≤racketRadius)时在挥拍弧扫不到的"环内洞"里，
                            // 球穿过身体会挥拍打空，补判为命中
                            double pivotDX = badminton.getCenterX() - getCenterX();
                            double pivotDY = badminton.getCenterY()
                                    - (getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight);
                            boolean pivotHit = pivotDX * pivotDX + pivotDY * pivotDY
                                    <= GameProperties.racketRadius * GameProperties.racketRadius;
                            if (pivotHit || racketSweepHit(badminton.getCenterX(), badminton.getCenterY(), racketX, racketY)) {
                                // 判定为打到球
                                double hitAngle = nowAngle;
                                if (side == sideRight) {
                                    hitAngle += 90;
                                    if (hitAngle > 360)
                                        hitAngle -= 360;
                                    if (hitAngle > 340 && getCenterY() > GameProperties.floorY - GameProperties.netHeight - 60)
                                        hitAngle = 340;
                                }
                                else {
                                    hitAngle -= 90;
                                    if (hitAngle < 0)
                                        hitAngle += 360;
                                    if (hitAngle > 180 && hitAngle < 200 && getCenterY() > GameProperties.floorY - GameProperties.netHeight - 60)
                                        hitAngle = 200;
                                }
                                // AI 扣杀爆发：挥拍起手 0.06s 内球进圈，相位算出的是朝上角(>180)，
                                // 会落进 900~1300 档——"扣杀"成了地面重击挑球。扣杀意图下把朝上角
                                // 关于水平轴镜像(360-angle)成对称下压角：左右半场均得朝对方场地的
                                // 下压方向，进 hitSpeedFor 的 2500 扣球档（全游戏最大速）。
                                // 仅 AI 扣杀生效，人类手感与普通重击不动
                                if (isAIControlled && aiSmashIntent && isHeavyShot && hitAngle > 180)
                                    hitAngle = 360 - hitAngle;
                                // AI 击球随机偏差(按难度 ±18/14/10/12/5°)后再按难度决定过网兜底：
                                // 1-3级不兜底(允许砸网失误)，4/5级扣杀按概率跳过(35%/15%)，
                                // 其余必兜底；满级恒兜底
                                if (isAIControlled) {
                                    // 滑动窗口预设失误拍：直接压向网的下压角，跳过一切修正
                                    // （AI 自己的发球拍不算，不吃预设）
                                    if (MatchAimAssist.isArmed() && !aiMaxLevel && !isReadyingServe) {
                                        MatchAimAssist.consume(); // 同回合只失误这一次
                                        hitAngle = netWardMissAngle();
                                    } else {
                                        hitAngle = applyShotVariance(hitAngle);
                                        if (shouldGuardNet())
                                            hitAngle = adjustAINetSafeAngle(hitAngle, badminton.getCenterX(), badminton.getCenterY());
                                    }
                                    // 4-5级：本拍触发扣杀/接杀后置位回合精度惩罚（本拍偏差已按原档算完，
                                    // 仍在 heavyHit 改速前调用以读到真实来球速度）
                                    updateRallyAccDrop(badminton);
                                }
                                // 击球判定唯一权威是主机/单机；联机客户端只更新本地动画标志，
                                // 球的表现由服务器广播的 HIT 回显触发，避免双份音效/特效与判定分叉
                                boolean localAuthority = MatchController.getNetClient() == null || MatchController.isHost();
                                if (localAuthority) {
                                    // 先执行击球算出权威初速，再随消息上报，
                                    // 客户端直接采用该初速，避免重算公式/档位不一致导致轨迹分叉
                                    if (isHeavyShot)
                                        badminton.heavyHit(hitAngle);
                                    else
                                        badminton.lightHit(hitAngle);
                                    if (MatchController.getNetClient() != null) {
                                        String type = isHeavyShot ? "HEAVY" : "LIGHT";
                                        String hitMsg = String.format("HIT_REQUEST:%s:%.2f:%.2f:%.2f:%.2f:%.2f",
                                                type, hitAngle, badminton.getCenterX(), badminton.getCenterY(),
                                                badminton.speedX, badminton.speedY);
                                        MatchController.getNetClient().sendLine(hitMsg);
                                    }
                                }
                                hasShotted = true;
                                isReadyingServe = false;
                                traceHit(badminton, hitAngle); // 诊断：本拍命中事件
                                notifySmashSpeech(); // 单人右侧 AI 扣杀命中：登记剧情
                                notifyPlayerReturn(hitAngle); // 左半场回击/扣杀：过网确认后出惊讶或夸赞句
                            }
                        }
                    }
                }
                else { // 下方击球
                    double deltaT = GameProperties.shotCooldown - shotCooldownTimer;
                    if (deltaT < GameProperties.shotAnimationTime2) {
                        double targetAngle = 180 - 145 - GameProperties.hitAreaAngleDown;
                        double nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime2);
                        nowAngle = nowAngle + 45;
                        if (side == -1)
                            nowAngle = 180 - nowAngle;
                        while (nowAngle < 0)
                            nowAngle += 360;
                        while (nowAngle > 360)
                            nowAngle -= 360;
                        if ((side == sideRight && nowAngle < 110)||(side == sideLeft && nowAngle > 70)) {
                            if (badmintonObj != null) {
                                double racketX = getCenterX() + Math.cos(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                                double racketY = getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight
                                        + Math.sin(Math.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
                                // 与上方击球一致：用球心对球拍点，消除左上角偏移导致的边缘误判；
                                // 同样补贴身兜底，球穿过身体不挥空
                                double pivotDX = badminton.getCenterX() - getCenterX();
                                double pivotDY = badminton.getCenterY()
                                        - (getCenterY() + GameProperties.playerHeight - GameProperties.hitAreaCenterHeight);
                                boolean pivotHit = pivotDX * pivotDX + pivotDY * pivotDY
                                        <= GameProperties.racketRadius * GameProperties.racketRadius;
                                if (pivotHit || racketSweepHit(badminton.getCenterX(), badminton.getCenterY(), racketX, racketY)) {
                                    double hitAngle = nowAngle;
                                    if (side == sideRight) {
                                        hitAngle -= 90;
                                        if (hitAngle < 0)
                                            hitAngle += 360;
                                        if (hitAngle < 90 || hitAngle > 320)
                                            hitAngle = 320;
                                        else if (hitAngle < 290)
                                            hitAngle = 290;
                                    }
                                    else {
                                        hitAngle += 90;
                                        if (hitAngle > 360)
                                            hitAngle -= 360;
                                        if (hitAngle < 220)
                                            hitAngle = 220;
                                        else if (hitAngle > 250)
                                            hitAngle = 250;
                                    }
                                    // AI 击球随机偏差(按难度分层) + 过网兜底按难度决定
                                    // 1-3级不兜底(允许砸网失误)，4/5级扣杀按概率跳过(35%/15%)
                                    if (isAIControlled) {
                                        if (MatchAimAssist.isArmed() && !aiMaxLevel && !isReadyingServe) {
                                            MatchAimAssist.consume(); // 同回合只失误这一次
                                            hitAngle = netWardMissAngle(); // 预设失误拍：压网下压角
                                        } else {
                                            hitAngle = applyShotVariance(hitAngle);
                                            if (shouldGuardNet())
                                                hitAngle = adjustAINetSafeAngle(hitAngle, badminton.getCenterX(), badminton.getCenterY());
                                        }
                                        // 4-5级回合精度惩罚触发（本拍偏差已按原档算完，仍读真实来球速度）
                                        updateRallyAccDrop(badminton);
                                    }
                                    // 击球判定唯一权威是主机/单机；联机客户端只更新本地动画标志，
                                    // 球的表现由服务器广播的 HIT 回显触发
                                    boolean localAuthority = MatchController.getNetClient() == null || MatchController.isHost();
                                    if (localAuthority) {
                                        // 先击球算出权威初速再上报，客户端直接采用避免重算分叉
                                        if (isHeavyShot)
                                            badminton.heavyHit(hitAngle);
                                        else
                                            badminton.lightHit(hitAngle);
                                        if (MatchController.getNetClient() != null) {
                                            String type = isHeavyShot ? "HEAVY" : "LIGHT";
                                            String hitMsg = String.format("HIT_REQUEST:%s:%.2f:%.2f:%.2f:%.2f:%.2f",
                                                    type, hitAngle, badminton.getCenterX(), badminton.getCenterY(),
                                                    badminton.speedX, badminton.speedY);
                                            MatchController.getNetClient().sendLine(hitMsg);
                                        }
                                    }
                                    hasShotted = true;
                                    isReadyingServe = false;
                                    traceHit(badminton, hitAngle); // 诊断：本拍命中事件
                                    notifySmashSpeech(); // 单人右侧 AI 扣杀命中：登记剧情
                                    notifyPlayerReturn(hitAngle); // 左半场回击/扣杀：过网确认后出惊讶或夸赞句
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            if (isAIControlled) {
                // AI 跳杀风存：起跳与挥拍同帧启动时，挥拍首帧肩膀仅抬~11px 就命中，
                // 视觉是"扣完再跳"。跳杀(扣杀意图+起跳)先只起跳，风存6帧(~0.1s，
                // 肩膀抬约68px)后下帧再挥拍——先跳、腾空、再扣。
                // 风存期间用起跳帧锁定的扣杀快照(smashWindupPending)出招：
                // 若等风存结束再看当帧 decision，球况一变 AI 会跳起来却只挑高远
                boolean windupStart = decision.isShot && smashWindupFrames == 0
                        && decision.isJump && decision.isSmashIntent && !smashWindupDone;
                boolean swingNow;
                if (windupStart) {
                    smashWindupFrames = 6; // 本帧只起跳（isJump 分支已在上方执行）
                    smashWindupPending = true;
                    smashWindupDone = true; // 本拍走快照路径，屏蔽当帧重复出招
                    swingNow = false;
                }
                else if (smashWindupFrames > 0) {
                    smashWindupFrames--;
                    swingNow = (smashWindupFrames == 0); // 风存结束帧用快照挥拍
                }
                else {
                    swingNow = decision.isShot && !smashWindupDone;
                    if (!decision.isShot)
                        smashWindupDone = false; // 条件消失复位，避免漏掉下一拍的风存
                }
                // 起手校验：用当帧真实状态预演本拍接触窗口能否扫到球，明确打不到则不起手。
                // 白挥会白耗体力+进入0.5s挥拍冷却，冷却反而挡住随后真正可接的时刻
                // （2026-10-15 P2 实证：挥空冷却到 f1169、球 f1170 落地）。
                // 拒绝后下帧 decision 仍会重新决策/重试，球况一变立即恢复出拍
                if (swingNow && !canSwingHit(decision, smashWindupPending)) {
                    if (smashWindupPending) {
                        smashWindupPending = false;
                        smashWindupDone = false; // 解除屏蔽：后续帧按普通路径重新决策
                    }
                    if (MissTrace.isEnabled())
                        MissTrace.event(String.format("E SWING_SKIP f=%d s=%s | ball %.0f,%.0f,%.0f,%.0f | ai %.0f,%.0f",
                                MissTrace.frame(), side > 0 ? "L" : "R",
                                badminton.getCenterX(), badminton.getCenterY(),
                                badminton.speedX, badminton.speedY,
                                getCenterX(), getCenterY()));
                    swingNow = false;
                }
                if (swingNow) {
                    isShotting = true;
                    hasShotted = false;
                    if (MissTrace.isEnabled())
                        MissTrace.event(String.format(
                                "E SWING f=%d s=%s %s windup=%b | ball %.0f,%.0f,%.0f,%.0f | ai %.0f,%.0f",
                                MissTrace.frame(), side > 0 ? "L" : "R",
                                decision.isSmashIntent ? "smash" : decision.isHeavyhit ? "heavy" : "light",
                                smashWindupPending,
                                badminton != null ? badminton.getCenterX() : -1,
                                badminton != null ? badminton.getCenterY() : -1,
                                badminton != null ? badminton.speedX : -1,
                                badminton != null ? badminton.speedY : -1,
                                getCenterX(), getCenterY()));
                    if (smashWindupPending) {
                        // 跳杀快照：恒重击+扣杀意图，不受当帧重新决策影响
                        isHeavyShot = true;
                        aiSmashIntent = true;
                        smashWindupPending = false;
                    }
                    else {
                        isHeavyShot = decision.isHeavyhit;
                        aiSmashIntent = decision.isSmashIntent; // 扣杀意图随出招一并落地
                    }
                    if (isHeavyShot) {
                        if (GameProperties.infiniteEnergyMode)
                            isHeavyShot = true;
                        else if (energyRemain > GameProperties.heavyShotEnergy) {
                            isHeavyShot = true;
                            energyRemain -= GameProperties.heavyShotEnergy;
                        }
                        else {
                            isHeavyShot = false;
                            energyRemain -= GameProperties.lightShotEnergy;
                            if (energyRemain < 0)
                                energyRemain = 0;
                        }
                    }
                    else {
                        isHeavyShot = false;
                        energyRemain -= GameProperties.lightShotEnergy;
                        if (energyRemain < 0)
                            energyRemain = 0;
                    }
                    shotCooldownTimer = GameProperties.shotCooldown;
                    shotType = shotTypeUp;
                    if (badminton != null) {
                        if ((badminton.getCenterX() - 450) * side < 0 // 轮到本方击球
                                && badminton.getCenterY() > getCenterY() - 30)
                            shotType = shotTypeDown;
                        else
                            shotType = shotTypeUp;
                    }
                }
            }
            else if ((KeyInput.isKeyHolding(KeyCode.Q) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.U) && side == sideLeft)
                    || (KeyInput.isKeyHolding(KeyCode.E) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.O) && side == sideLeft)) {
                isShotting = true;
                hasShotted = false;
                if ((KeyInput.isKeyHolding(KeyCode.Q) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.U) && side == sideLeft)) {
                    isHeavyShot = false;
                    energyRemain -= GameProperties.lightShotEnergy;
                    if (energyRemain < 0)
                        energyRemain = 0;
                }
                else if ((KeyInput.isKeyHolding(KeyCode.E) && side == sideRight) || (KeyInput.isKeyHolding(KeyCode.O) && side == sideLeft)) {
                    if (GameProperties.infiniteEnergyMode)
                        isHeavyShot = true;
                    else if (energyRemain > GameProperties.heavyShotEnergy) { // 剩余体力大于 重击所耗体力 时消耗体力，否则无法重击，只能轻击
                        isHeavyShot = true;
                        energyRemain -= GameProperties.heavyShotEnergy;
                    }
                    else {
                        isHeavyShot = false;
                        energyRemain -= GameProperties.lightShotEnergy;
                        if (energyRemain < 0)
                            energyRemain = 0;
                    }
                }
                shotCooldownTimer = GameProperties.shotCooldown;
                shotType = shotTypeUp;
                if (badminton != null) {
                    if ((badminton.getCenterX() - 450) * side < 0 // 轮到本方击球
                            && badminton.getCenterY() > getCenterY() - 30 && (badminton.speedY == 0 || badminton.speedY > 150))
                        shotType = shotTypeDown;
                    else
                        shotType = shotTypeUp;
                }
            }
        }

        // 身体部件和头绑定
        bindBodyPart();
    }
}

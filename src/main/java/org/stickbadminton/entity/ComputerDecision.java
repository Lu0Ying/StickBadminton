package org.stickbadminton.entity;
import org.stickbadminton.logic.GameProperties;
import java.util.Random;

public class ComputerDecision {
    // 动作控制标志
    public boolean isLighthit = false;
    public boolean isHeavyhit = false;
    public boolean isMoveLeft = false;
    public boolean isMoveRight = false;
    public boolean isJump = false;
    public boolean isShot = false;
    public boolean isJumpCooldown = false;

    // 位置和状态信息（由调用方每帧传入）
    public double computerX = 0.0;
    public double computerY = 0.0;
    public double badmintonX = 0.0;
    public double badmintonY = 0.0;
    public double badmintonSpeedX = 0.0;
    public double badmintonSpeedY = 0.0;
    public double opponentX = 0.0;
    public double opponentY = 0.0;
    public double racketRadius;
    public double hitAreaRadius;
    public double highShotDistance = 150.0;
    public double defenceOpponentDistance = 100.0;
    public double superShotPoint;
    public double superShotAngleDeg = -1.0;
    public double difficultyLevel;

    // 反应时间
    private static final double REACTION_TIME_MIN = 0.05;
    private static final double REACTION_TIME_MAX = 0.7;

    // 网前判定参数
    private static final double NET_X_WINDOW = 140.0;
    private static final double NET_Y_MARGIN_ABOVE = 130.0;
    private static final double NET_Y_MARGIN_BELOW = 20.0;
    private static final double NET_ALIGN_DEADZONE = 4.0;
    private static final double NET_SAFE_OFFSET = 10.0;  // 稍加大安全距离，减少贴网失手
    private static final double WALL_BOUNCE_GAP = 22.0;  // 增大贴墙阈值

    // 杀球防守参数（新）
    private static final double SMASH_SPEED_THRESHOLD = 900.0;   // 合速度阈值
    private static final double SMASH_VY_THRESHOLD    = 350.0;   // 竖直向下速度阈值（y正向下）
    private static final double SMASH_INTERCEPT_Y_MIN = 30.0;    // 肩上方最小拦截高度
    private static final double SMASH_INTERCEPT_Y_MAX = 90.0;    // 肩上方最大拦截高度
    private static final double SMASH_CONTACT_OFFSET  = 6.0;     // 接触时的本方侧偏移，避免贴身打空
    private static final double SMASH_HIT_MARGIN      = 12.0;    // 击球圈放宽值

    // 扣杀决策过网仿真的安全边距上限(px)：接触发生在决策后约0.1s，球仍在下落，
    // 实际边距按 badmintonSpeedY*0.1 动态计算并以此封顶（固定45会把正常扣杀全拒掉）
    private static final double SMASH_LAG_MARGIN_CAP = 45.0;
    // 决策层压平迭代上限(度)：切线角天然比过网角更下压，向本方前上方每次调5°直到能过网；
    // 超过该上限说明本质不是扣杀球，交回常规分支（真正出球时 StickMan 接触层还有安全网兜底）
    private static final double SMASH_FLATTEN_MAX_DEG = 30.0;

    // 跨帧记忆：用于判定“刚刚墙反弹”
    private static double prevBallX = Double.NaN;
    private static double prevBallVX = Double.NaN;
    private static int wallBounceCooldownFrames = 0;   // 墙反弹后禁止起跳的帧数
    private static final int WALL_BOUNCE_COOLDOWN_DEFAULT = 12; // ~0.2s @60fps

    // 预判与状态
    private double predictedLandingX;
    private double predictedLandingY;
    private boolean isBallApproaching = false;
    private Random random = new Random();
    // G 键调试：p1 满级 AI。决策全部在构造器内完成，必须构造前定级，
    // 事后改 difficultyLevel 来不及（shouldReact 等随机门槛已在构造中执行过）
    private boolean maxLevel = false;
    // 体力管理（难度 ≥3 或 G 键满级启用）：低体力收敛跳跃/重击、站桩回血、按实际移速预判；
    // 1-2级不启用，保持原有的“乱”行为
    private boolean smartEnergy = false;
    private double energy = 100.0;
    // StickMan 传入的实际移速（体力插值 × 属性倍率），仅体力管理路径参与预判
    private double effectiveMoveSpeed;

    public ComputerDecision() {}

    public ComputerDecision(boolean maxLevel, int side,
                            double hitCenterX, double hitCenterY, boolean isShotCooldown, boolean isJumpCooldown,
                            double badmintonX, double badmintonY, double badmintonSpeedX, double badmintonSpeedY,
                            double opponentX, double opponentY,
                            double energyRemain, double effectiveMoveSpeed)
    {
        this.maxLevel = maxLevel;
        this.energy = energyRemain;
        this.effectiveMoveSpeed = effectiveMoveSpeed;
        this.smartEnergy = maxLevel || GameProperties.difficultyLevel >= GameProperties.energyManageDifficulty;
        // 初始化基本信息
        this.badmintonSpeedX = badmintonSpeedX;
        this.badmintonSpeedY = badmintonSpeedY;
        this.badmintonX = badmintonX;
        this.badmintonY = badmintonY;
        this.computerX = hitCenterX;
        this.computerY = hitCenterY;
        this.opponentX = opponentX;
        this.opponentY = opponentY;
        this.isJumpCooldown = isJumpCooldown;
        this.superShotPoint = computerY - 30;

        // 难度（如项目中有统一难度，可替换）
        // this.difficultyLevel = GameProperties.difficultyLevel;
        if (maxLevel) {
            // 满级覆盖：难度 1.0 + 满判定半径，不动全局 difficultyLevel（避免对手 AI 顺带满级）
            this.difficultyLevel = 1.0;
            this.racketRadius = GameProperties.racketRadius;
            this.hitAreaRadius = GameProperties.hitAreaRadius;
        }
        else if (GameProperties.difficultyLevel == 1)
            this.difficultyLevel = 0.5;
        else if (GameProperties.difficultyLevel == 2)
            this.difficultyLevel = 0.8; // 2级由0.7调至0.8；仍不启用体力管理，保持“乱”的行为
        else if (GameProperties.difficultyLevel == 3)
            this.difficultyLevel = 0.9;
        else if (GameProperties.difficultyLevel == 4)
            this.difficultyLevel = 1.0;
        else
            this.difficultyLevel = 1.0; // 5级：决策与4级同为1.0，属性另乘1.1（StickMan.getStatMultiplier）
        if(random.nextDouble()>difficultyLevel*0.1)
        {
            this.racketRadius = GameProperties.racketRadius*difficultyLevel;
            this.hitAreaRadius = GameProperties.hitAreaRadius*difficultyLevel;
        }
        else{
        this.racketRadius = GameProperties.racketRadius;
        this.hitAreaRadius = GameProperties.hitAreaRadius;
    }


        // 更新墙反弹冷却
        updateWallBounceCooldown();

        // 预判球的落点
        predictBallLanding();

        // 判断球是否向我方飞来
        isBallApproaching = isBallComingToMySide(side);

        // 执行决策
        if (isBallApproaching) {
            if (shouldReact()) {
                executeStrategy(side, isShotCooldown);
            }
        } else if (badmintonY < GameProperties.floorBallY - 50) {
            // 无球/回位
            offBallPositioning(side);
        }

        // 记忆当前球状态
        prevBallX = badmintonX;
        prevBallVX = badmintonSpeedX;
    }

    // 决策层预判用的移速：体力管理按实际移速（低体力跑得慢，预判要同步变慢，
    // 否则 AI 会误判“跑得到”而漏球）；1-2 级保持固定满速，保留原有的预判误差（“乱”的一部分）
    private double decisionMoveSpeed() {
        return smartEnergy ? effectiveMoveSpeed : GameProperties.moveSpeed;
    }

    // 无球时的基础站位
    private void offBallPositioning(int side) {
        // 左侧玩家可移动区间中心大约在 260（[91, 429] 中点）
        final double anchorLeft  = 260.0;
        // 右侧玩家可移动区间中心大约在 640（[471, 809] 中点）
        final double anchorRight = 640.0;

        double target = (side == 1) ? anchorLeft : anchorRight;
        // 体力管理：低体力时放大就位死区，站在锚点附近不动，
        // 利用站立回血 20/s 快速恢复（移动回血仅 3/s），避免来回踱步浪费体力
        double deadzone = (smartEnergy && energy < GameProperties.lowEnergyIdleThreshold) ? 30.0 : 2.0;
        if (Math.abs(computerX - target) <= deadzone) {
            isMoveLeft = isMoveRight = false;
            return;
        }
        if (computerX < target) { isMoveRight = true; isMoveLeft = false; }
        else                    { isMoveLeft  = true; isMoveRight = false; }
    }

    private void predictBallLanding() {
        predictedLandingX = preLanding(GameProperties.floorBallY);
        predictedLandingY = GameProperties.floorBallY;
    }

    private boolean isBallComingToMySide(int side) {
        boolean ballInMyArea = (side == 1 && badmintonX < GameProperties.netPosition) ||
                (side == -1 && badmintonX > GameProperties.netPosition);

        boolean ballComingToMe = (side == 1) ? (badmintonSpeedX < 0 || badmintonX < GameProperties.netPosition)
                : (badmintonSpeedX > 0 || badmintonX > GameProperties.netPosition);

        return ballInMyArea && ballComingToMe &&
                badmintonY > GameProperties.netHeight &&
                badmintonY < GameProperties.floorBallY - 30;
    }

    private boolean shouldReact() {
        if (maxLevel) return true; // 满级恒反应：消除随机门槛，保证双端决策同轨
        // 低难度也给到高反应，避免“等太久错过网前球”
        double reactionTime = REACTION_TIME_MIN;
        return random.nextDouble() > reactionTime;
    }

    private void executeStrategy(int side, boolean isShotCooldown) {
        //杀球防守
        if (handleSmashDefense(side, isShotCooldown)) return;

        //预测拦截
        if (handleFrontCourt(side, isShotCooldown)) return;

        //位置调整
        adjustPosition();

        //常规击球
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius)) {
            decideShotStrategy();
        }

        //考虑跳跃（对手远离球网；用绝对值使左右半场 AI 判定对称——
        //左侧 AI 的对手在右半场，旧式 netPosition-opponentX 恒为负导致永不跳跃）
        if (getDistance() < (100 + hitAreaRadius + racketRadius) &&
                Math.abs(opponentX - GameProperties.netPosition) > 100 &&
                Math.abs(computerX - badmintonX) < 16)
        {
            moveVertical();
        }
    }
    //杀球防守
    private boolean handleSmashDefense(int side, boolean isShotCooldown) {
        if (!isIncomingSmash(side)) return false;

        // 目标拦截高度：根据下坠速度自适应，越快越高拦截
        double vy = badmintonSpeedY;
        double offset = clamp(
                map(vy, SMASH_VY_THRESHOLD, 1200.0, SMASH_INTERCEPT_Y_MIN, SMASH_INTERCEPT_Y_MAX),
                SMASH_INTERCEPT_Y_MIN, SMASH_INTERCEPT_Y_MAX
        );
        double targetY = computerY - offset;

        if (badmintonY > targetY) {
            targetY = computerY - Math.max(10.0, SMASH_INTERCEPT_Y_MIN);
        }

        InterceptPrediction pred = predictAtY(targetY);

        // 异常保护：若无法预测，直接对位到当前球X
        double desiredX = Double.isFinite(pred.x) && pred.t > 0 ? pred.x : badmintonX;

        // 在“本方侧 + 少量偏移”处对位，避免贴身打不到
        desiredX += (side == 1 ? -SMASH_CONTACT_OFFSET : SMASH_CONTACT_OFFSET);

        // 边界（人物中心）约束
        double leftBound, rightBound;
        if (side == 1) {
            leftBound  = GameProperties.playFieldLeft + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.netPosition - GameProperties.playerWidth / 2.0;
        } else {
            leftBound  = GameProperties.netPosition + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.playFieldRight - GameProperties.playerWidth / 2.0;
        }
        desiredX = clamp(desiredX, leftBound, rightBound);

        // 横移就位
        double dx = desiredX - computerX;
        if (Math.abs(dx) > NET_ALIGN_DEADZONE) {
            if (dx > 0) { isMoveRight = true; isMoveLeft = false; }
            else        { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }

        // 杀球防守默认不跳，减少“起跳接空”
        isJump = false;

        // 进入击球圈：强制“上托”轻击（高远防守）
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius + SMASH_HIT_MARGIN)) {
            isShot = true;
            isLighthit = true;
            isHeavyhit = false;

            // 防守角度
            double base = (side == 1) ? 300.0 : 240.0;
            superShotAngleDeg = base;
            return true; // 已完成处理
        }

        // 若未进入击球圈，仍保持本分支主导，避免被其它策略打断
        return true;
    }

    private boolean isIncomingSmash(int side) {
        double netX = GameProperties.netPosition;
        // 到我方或即将入我方
        boolean onMySideOrEntering = (side == 1) ? (badmintonX <= netX + 40.0)
                : (badmintonX >= netX - 40.0);
        // 朝我方飞
        boolean comingToMe = (side == 1) ? (badmintonSpeedX < 0) : (badmintonSpeedX > 0);

        // 速度与高度条件
        double speed = getBallSpeed();
        boolean fastDown  = badmintonSpeedY > SMASH_VY_THRESHOLD;
        boolean speedHigh = speed > SMASH_SPEED_THRESHOLD;
        boolean notTooLow = badmintonY < GameProperties.floorBallY - 60.0;

        return onMySideOrEntering && comingToMe && fastDown && speedHigh && notTooLow;
    }
    //杀球防守结束

    // 网前对位
    private boolean handleFrontCourt(int side, boolean isShotCooldown) {
        if (!isFrontCourtScenario(side)) return false;

        // 网带顶Y
        double netTopY = GameProperties.floorBallY - GameProperties.netHeight;
        // 目标拦截高度：把球限制在网带下方 20~90 之间（便于压网/搓球）
        double targetY = clamp(badmintonY, netTopY + 20, netTopY + 90);

        // 预测该高度下的 X
        InterceptPrediction pred = predictAtY(targetY);
        double desiredX = pred.x;

        // 对位在“本方侧 + 安全偏移”
        double offset = Math.max(12.0, hitAreaRadius - NET_SAFE_OFFSET);
        if (side == 1) desiredX -= offset; else desiredX += offset;

        // 边界约束：人物可达边界（人物中心）
        double leftBound, rightBound;
        if (side == 1) {
            leftBound  = GameProperties.playFieldLeft + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.netPosition - GameProperties.playerWidth / 2.0;
        } else {
            leftBound  = GameProperties.netPosition + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.playFieldRight - GameProperties.playerWidth / 2.0;
        }
        desiredX = clamp(desiredX, leftBound, rightBound);

        // 就位
        double dx = desiredX - computerX;
        if (Math.abs(dx) > NET_ALIGN_DEADZONE) {
            if (dx > 0) { isMoveRight = true; isMoveLeft = false; }
            else        { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }

        // 网前禁止起跳
        isJump = false;

        // 近距离命中检测 + 轻击
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius + 8)) {
            isShot = true;
            isLighthit = true;
            isHeavyhit = false;

            //向对角下压角度
            superShotAngleDeg = (side == 1)
                    ? 20.0  + (random.nextDouble() - 0.5) * 12.0
                    : 160.0 + (random.nextDouble() - 0.5) * 12.0;
        }

        return true;
    }

    private boolean isFrontCourtScenario(int side) {
        // 墙反弹冷却内不抢网
        if (isLikelyRecentWallBounce()) return false;

        double netX = GameProperties.netPosition;
        double netTopY = GameProperties.floorBallY - GameProperties.netHeight;
        double clearanceToNetTop = badmintonY - netTopY; // >0 表示低于网带

        boolean nearNetHoriz = Math.abs(badmintonX - netX) <= NET_X_WINDOW;
        boolean nearNetVert  = (clearanceToNetTop > -NET_Y_MARGIN_BELOW) &&
                (clearanceToNetTop < NET_Y_MARGIN_ABOVE);
        boolean onMySideOrEntering =
                (side == 1 && badmintonX <= netX + 12) ||
                        (side == -1 && badmintonX >= netX - 12);

        boolean vyFriendly = Math.abs(badmintonSpeedY) < 750;

        return nearNetHoriz && nearNetVert && onMySideOrEntering && vyFriendly;
    }

    private void adjustPosition() {
        double idealX = predictedLandingX;
        double distance = idealX - computerX;
        double maxMoveDistance = decisionMoveSpeed() * GameProperties.frameTime;

        if(difficultyLevel == 1) {
            distance *= 1.0;
        } else {
            // 旧缩放(d*0.1+0.03)会把停止阈值放大到 50px 以上，低等级触及圈仅约 52px，
            // AI 停在圈边缘外漏球、显得站位呆；改为线性缩放：1级0.75、2级0.9、3级0.95、4/5级1.0，
            // 站位始终收敛到触及圈内，满级(difficultyLevel=1.0)行为与旧版完全一致，联机不分叉
            distance *= (0.5 + 0.5 * difficultyLevel);
        }

        if (Math.abs(distance) > maxMoveDistance) {
            if (distance > 0) { isMoveRight = true; isMoveLeft = false; }
            else               { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }
    }

    private void decideShotStrategy() {
        isShot = true;

        double opponentDistance = Math.abs(opponentX - computerX);
        double ballHeight = badmintonY;
        double ballSpeed = getBallSpeed();

        boolean canSmash =
                ballHeight < (computerY - 20) &&
                        ballHeight > (computerY - 120) &&
                        getDistance() < (hitAreaRadius + racketRadius) &&
                        Math.abs(badmintonSpeedY) < 500;

        if (canSmash && (!smartEnergy || badmintonSpeedY >= 0)) {
            if (!smartEnergy) {
                // 1-2级保持原有“一律跳杀”行为不变
                moveHorizontal(200);
                superShot();
                return;
            }
            // 高难度：几何判定能过网才扣杀；压不过网时落穿到下方常规分支挑/抽过网，
            // 避免扣杀直接砸网或砸在自己半场
            if (preciseSmash()) return;
        }

        // “对手/自己离网多远”一律用绝对值：旧式 netPosition-x 只在 AI 在右半场时为正，
        // 左侧 AI（对手在右）恒为负 → 分支判断全部失真，永远只会出高球轻击
        double opponentNetDist = Math.abs(opponentX - GameProperties.netPosition);
        double computerNetDist = Math.abs(computerX - GameProperties.netPosition);
        if (opponentDistance > 400 && opponentNetDist < defenceOpponentDistance) {
            if (ballSpeed > 800) {
                moveHorizontal(hitAreaRadius / 1.414);
                middleShot();
            } else {
                moveHorizontal(-hitAreaRadius);
                highShot();
            }
        } else if (opponentDistance > 200 && opponentNetDist < defenceOpponentDistance) {
            if (computerNetDist < highShotDistance) {
                moveHorizontal(-hitAreaRadius);
                highShot();
            } else {
                moveHorizontal(GameProperties.hitAreaCenterHeight / 1.5);
                middleShot();
            }
        } else {
            if (computerNetDist < 200 || opponentNetDist < defenceOpponentDistance) {
                moveHorizontal(-hitAreaRadius);
                highShot();
            } else if (ballSpeed > 600) {
                moveHorizontal(GameProperties.hitAreaCenterHeight / 1.2);
                middleShot();
            } else {
                moveHorizontal(-GameProperties.hitAreaCenterHeight / 1.5);
                highShot();
            }
        }
    }

    // 高难度扣杀智能：先按挥拍切线预期出球角做物理过网仿真，再按体力选择跳杀/站立重扣——
    // 实际出球角由挥拍扫掠相位决定（=球相对肩部极角的切线方向），决策层只能按当前几何预期，
    // 接触延迟的下落由 SMASH_LAG_MARGIN 放宽兜住，StickMan 接触层安全网再兜一道
    // 返回 true = 已执行扣杀；false = 预期压不过网，调用方落穿到常规挑/抽分支
    private boolean preciseSmash() {
        double heightAbove = computerY - badmintonY; // 球高出肩部的像素
        // 球相对肩部的极角（y 轴向下）：右半场出球角=极角-90，左半场=极角+90（挥拍切线）
        double alpha = StrictMath.toDegrees(StrictMath.atan2(badmintonY - computerY, badmintonX - computerX));
        if (alpha < 0) alpha += 360;
        boolean isRightSide = computerX > GameProperties.netPosition;
        double expectedAngle = isRightSide ? alpha - 90 : alpha + 90;
        if (expectedAngle < 0) expectedAngle += 360;
        else if (expectedAngle >= 360) expectedAngle -= 360;
        // 出球角不在扣球半圈（≥180° 是高球）→ 不算扣杀，交回常规分支
        if (expectedAngle >= 180) return false;
        // 切线角天然比"能过网的角度"更下压（球在肩前下方时斜率≈|dx/dy|），
        // 直接拿它仿真会把正常扣杀全拒掉（表现为 AI 不跳跃、不重击）。
        // 与 StickMan 接触层安全网同向：每次向本方前上方调 5°（右半场 +5、左半场 -5，
        // 绕360°均朝 270° 收敛），能过网即可扣杀；压平超过上限说明本质不是扣杀球。
        // 过网边距按决策到接触（~0.1s）内球的下落量动态计算，而非固定值
        double lagMargin = Math.min(SMASH_LAG_MARGIN_CAP, Math.max(0.0, badmintonSpeedY * 0.1));
        double flattenStep = isRightSide ? 5.0 : -5.0;
        boolean clearsNet = false;
        // 2500 是 angle<180 的 heavyHit 固定档
        for (double tuned = 0; tuned <= SMASH_FLATTEN_MAX_DEG + 1e-9; tuned += 5.0) {
            double candidate = expectedAngle + flattenStep * tuned;
            if (Badminton.launchClearsNet(badmintonX, badmintonY, candidate, 2500.0, lagMargin)) {
                clearsNet = true;
                break;
            }
        }
        if (!clearsNet) return false;
        // 跳杀总价 = 跳25 + 重击35 = 60：体力不足、球不够高、或起跳后球撑不到挥拍弧就改站立重扣
        if (energy > GameProperties.jumpEnergy + GameProperties.heavyShotEnergy
                && heightAbove >= GameProperties.smashJumpHeight
                && smashJumpKeepsContact()) {
            // 起跳够得到更高击球点，按原逻辑高位卡位
            moveHorizontal(200);
            superShot(true);
        } else {
            // 站立重扣：按球落到肩上40px处的横向位置提前就位，只耗35体力、无跳跃冷却
            moveHorizontal(GameProperties.smashStandContactHeight);
            superShot(false);
        }
        return true;
    }

    // 跳杀可达性终检：起跳初速700px/s，0.1s内肩膀抬高约60px，而发球类快坠球（vy≈300）
    // 同期下落约34px，肩膀会反超球——球落到肩膀下方后，上方挥拍扫掠弧（只扫肩上扇形）
    // 整个动画窗口都够不到球，起跳接空还白耗25体力。
    // 按 StickMan/Badminton 的逐帧语义（重力逐帧累加、不乘dt）推演接触时刻：
    // 球仍在起跳后的肩膀上方才跳杀，否则降级站立重扣（肩膀不动，球下坠进圈照打）
    private boolean smashJumpKeepsContact() {
        double shoulderY = computerY;
        double jumpVy = -GameProperties.jumpSpeedY;
        double ballY = badmintonY;
        double ballVy = badmintonSpeedY;
        // 决策到接触约 0.1s = 6 帧（挥拍动画 0.15s 的前中段）
        int contactFrames = 6;
        for (int i = 0; i < contactFrames; i++) {
            jumpVy += GameProperties.jumpGravity;
            shoulderY += jumpVy * GameProperties.frameTime;
            ballVy += GameProperties.badmintonGravity;
            ballY += ballVy * GameProperties.frameTime;
        }
        return ballY <= shoulderY;
    }

    // 预判球飞到目标高度 targetY 时的水平位置（y 轴向下）
    // 旧版循环条件方向写反：球在目标上方(下坠场景)时一次都不模拟，拿到的是当前球X；
    // 球在目标下方时又空转 300 步模拟“永远到不了”的轨迹，返回随速度漂移的假落点，
    // AI 跟着假目标反向跑——这正是移动方向偶尔变反的根因
    public double preLanding(double targetY) {
        double vy = badmintonSpeedY;
        double vx = badmintonSpeedX;
        double x = badmintonX;
        double y = badmintonY;

        // 球已在目标高度或更低且不再上升 → 永远不会再穿过目标，直接返回当前 X
        if (y >= targetY && vy >= 0) return x;

        double g = GameProperties.badmintonGravity;
        double dt = GameProperties.frameTime;
        int steps = 0, maxSteps = 300;
        double prevY = y;

        while (steps < maxSteps) {
            // 与 Badminton.onUpdate 逐步物理一致：阻力与重力按步施加，不乘 dt
            double speed = Math.sqrt(vx * vx + vy * vy);
            if (speed == 0) {
                vy += g;
            } else {
                double air = 0.00001 * (speed * speed);
                vx -= 2.7 * air * (vx / speed);
                vy += g - 0.5 * air * (vy / speed);
            }

            // 与球一致的空气墙反弹(0.6 恢复系数)：不模拟反弹会让预测点飞到墙外，AI 追着墙外目标反跑
            double nextX = x + vx * dt;
            if (nextX <= GameProperties.playFieldLeft || nextX >= GameProperties.playFieldRight) {
                double leftSq = (x - GameProperties.playFieldLeft) * (x - GameProperties.playFieldLeft);
                double rightSq = (GameProperties.playFieldRight - x) * (GameProperties.playFieldRight - x);
                x = leftSq < rightSq ? GameProperties.playFieldLeft : GameProperties.playFieldRight;
                vx = -vx * 0.6;
            }
            x += vx * dt;
            y += vy * dt;

            // 路径穿过目标高度即返回（同时兼容目标在球上方/下方两种场景）
            if ((prevY - targetY) * (y - targetY) <= 0) break;
            prevY = y;
            steps++;
        }
        return x;
    }

    // 水平移动到某一目标击球高度对应的X
    public void moveHorizontal(double targetHitOffset) {
        double targetY = computerY - targetHitOffset;
        double idealHitX = preLanding(targetY);

        double distance = idealHitX - computerX;
        double maxMoveDistance = decisionMoveSpeed() * GameProperties.frameTime;

        // 低难度也留一定精度
        if (random.nextDouble() > difficultyLevel) {
            distance *= (0.75 + random.nextDouble() * 0.2);
        }

        double leftBoundary = GameProperties.playFieldLeft + GameProperties.playerWidth / 2;
        double rightBoundary = GameProperties.playFieldRight - GameProperties.playerWidth / 2;

        if (computerX < leftBoundary) {
            isMoveRight = true;  isMoveLeft = false;
        } else if (computerX > rightBoundary) {
            isMoveLeft  = true;  isMoveRight = false;
        } else if (Math.abs(distance) > maxMoveDistance) {
            if (distance > 0) { isMoveRight = true; isMoveLeft = false; }
            else              { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }
    }

    // 垂直跳跃
    public void moveVertical() {
        if (isJump || isJumpCooldown) return;
        if (isLikelyRecentWallBounce()) return;

        double netX = GameProperties.netPosition;
        double vx = badmintonSpeedX;
        // 球正朝本方半场飞来（vx 指向网 = 指向我方）。旧符号写反，与 calculateTimeToNet
        // 内的 movingTowardsNet 互斥，timeToNet 恒为 0 → shouldJump 永远不通过，此入口从未生效
        boolean ballComingToMe = (computerX < netX && vx > 0) || (computerX > netX && vx < 0);
        if (!ballComingToMe) return;

        double timeToNet = calculateTimeToNet(netX, badmintonX, badmintonSpeedX, badmintonSpeedY);

        if (shouldJump(timeToNet)) {
            // 再做一次“可达性终检”（末刻取消）
            double idealHitY = computerY - GameProperties.hitAreaCenterHeight / 1.5;
            InterceptPrediction pred = predictAtY(idealHitY);
            if (pred.t > 0 && pred.t <= 1.0) {
                double reach = decisionMoveSpeed() * pred.t + hitAreaRadius + racketRadius + 8.0;
                if (Math.abs(pred.x - computerX) <= reach) {
                    isJump = true;
                }
            }
        }
    }

    private double calculateTimeToNet(double netX, double currentX, double vx, double vy) {
        double dt = GameProperties.frameTime, time = 0.0;
        double x = currentX, tvx = vx, tvy = vy;
        int steps = 0, maxSteps = 300;

        boolean movingTowardsNet = (computerX < netX && tvx > 0) || (computerX > netX && tvx < 0);
        if (!movingTowardsNet) return 0.0;

        while (Math.abs(x - netX) > 1 && steps < maxSteps) {
            double speed = Math.sqrt(tvx * tvx + tvy * tvy);
            double air = (speed == 0) ? 0 : 0.00001 * (speed * speed);
            if (speed == 0) {
                tvy += GameProperties.badmintonGravity * dt;
            } else {
                tvy += (GameProperties.badmintonGravity - 0.5 * air * (tvy / speed)) * dt;
                tvx -= 2.7 * air * (tvx / speed) * dt;
            }
            x += tvx * dt;
            time += dt;
            steps++;
        }
        return time;
    }

    private boolean shouldJump(double timeToNet) {
        boolean basicWindow = timeToNet > 0.22 && timeToNet < 0.9;
        if (!basicWindow) return false;
        if (isLikelyRecentWallBounce()) return false;
        // 高难度：球在站立触及圈(肩上105px)内时不起跳——站立就能打到，
        // 起跳白花25体力还打断横移，只对超出站立触及范围的高球起跳
        if (smartEnergy && badmintonY >= computerY - (GameProperties.hitAreaRadius + GameProperties.racketRadius)) return false;
        // 体力管理：起跳要花 25 体力，起跳后至少保留 15 余量确保还能出球，
        // 体力不足先不起跳，避免连跳把体力掏空后跑不动也打不出
        if (smartEnergy && energy < GameProperties.jumpEnergy + GameProperties.jumpEnergyReserve) return false;
        if (maxLevel) return true; // 满级恒起跳：跳过随机概率，双端一致

        // 难度概率（低难度也给较高概率，以免错过球）
        double p = Math.max(0.5, difficultyLevel);
        return random.nextDouble() < (0.9 * p);
    }

    // 强化“刚碰墙反弹”检测：结合速度符号翻转 + 临近墙体 + 冷却帧
    private boolean isLikelyRecentWallBounce() {
        if (wallBounceCooldownFrames > 0) return true;

        double leftGap  = badmintonX - GameProperties.playFieldLeft;
        double rightGap = GameProperties.playFieldRight - badmintonX;

        boolean nearLeftAndInward  = leftGap  >= 0 && leftGap  < WALL_BOUNCE_GAP && badmintonSpeedX > 0;
        boolean nearRightAndInward = rightGap >= 0 && rightGap < WALL_BOUNCE_GAP && badmintonSpeedX < 0;

        return nearLeftAndInward || nearRightAndInward;
    }

    // 跨帧维护墙反弹冷却
    private void updateWallBounceCooldown() {
        if (!Double.isNaN(prevBallVX)) {
            boolean signFlip = (prevBallVX >  80 && badmintonSpeedX < -80) ||
                    (prevBallVX < -80 && badmintonSpeedX >  80);
            boolean nearWall = Math.min(Math.abs(badmintonX - GameProperties.playFieldLeft),
                    Math.abs(GameProperties.playFieldRight - badmintonX)) < WALL_BOUNCE_GAP;
            if (signFlip && nearWall) {
                wallBounceCooldownFrames = Math.max(wallBounceCooldownFrames, WALL_BOUNCE_COOLDOWN_DEFAULT);
            }
        }
        if (wallBounceCooldownFrames > 0) {
            wallBounceCooldownFrames--; // 每帧递减
        }
    }

    // 预测球在目标高度 targetY 时的 X 与所需时间
    private InterceptPrediction predictAtY(double targetY) {
        double dt = GameProperties.frameTime;

        double x = badmintonX;
        double y = badmintonY;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;

        double time = 0.0;
        int steps = 0, maxSteps = 300;
        double prevY = y;

        while (steps < maxSteps) {
            double speed = Math.sqrt(vx * vx + vy * vy);

            if (speed == 0) {
                vy += GameProperties.badmintonGravity * dt;
            } else {
                double air = 0.00001 * (speed * speed);
                double ax = -2.7 * air * (vx / speed);
                double ay = GameProperties.badmintonGravity - 0.5 * air * (vy / speed);

                vx += ax * dt;
                vy += ay * dt;
            }

            x += vx * dt;
            y += vy * dt;
            time += dt;

            if ((prevY - targetY) * (y - targetY) <= 0) break;

            prevY = y;
            steps++;
        }

        return new InterceptPrediction(x, time, vy);
    }

    private static class InterceptPrediction {
        final double x;
        final double t;
        final double vy;
        InterceptPrediction(double x, double t, double vy) { this.x = x; this.t = t; this.vy = vy; }
    }

    // 重击（默认随挥起跳，兼容原有调用）
    public void superShot() {
        superShot(true);
    }

    // 重击；jump 为 false 时是站立重扣：省 25 体力、无跳跃冷却，衔接下一拍更快
    public void superShot(boolean jump) {
        // 体力管理：跳杀 = 跳25 + 重击35 = 60，站立重扣 = 35。体力不足则降级为普通击球；
        // 否则出招会被 StickMan 按“体力>35”规则静默降级成轻击，白跳一次还亏体力
        double cost = jump ? GameProperties.jumpEnergy + GameProperties.heavyShotEnergy
                : GameProperties.heavyShotEnergy;
        if (smartEnergy && energy <= cost) {
            middleShot();
            return;
        }
        isHeavyhit = true;
        isLighthit = false;
        isJump = jump;

        boolean isRightSide = computerX > GameProperties.netPosition;
        double opponentDistance = Math.abs(opponentX - computerX);
        double targetAngle = isRightSide
                ? ((opponentDistance > 300) ? 165 : 155)
                : ((opponentDistance > 300) ? 15  : 25);
        targetAngle += (new Random().nextDouble() - 0.5) * 10;
        superShotAngleDeg = targetAngle;
    }

    // 轻击高球
    public void highShot() { isLighthit = true; isHeavyhit = false; }

    // 中场球
    public void middleShot() {
        double distance = Math.abs(computerX - GameProperties.netPosition); // 自己离网距离，左右半场对称
        // 体力管理：体力已不够一次重击就直接轻击（重击门槛是体力>35，边缘值会被静默降级，
        // 主动降级可让意图与实际动作一致）；满级路径按体力判断也是确定性的，双端不分叉
        boolean energyLight = smartEnergy && energy <= GameProperties.heavyShotEnergy;
        // 满级恒重击：30% 轻击随机会令双端 energyRemain 分叉 → moveSpeed 分叉
        if (energyLight || (!maxLevel && new Random().nextDouble() < 0.3 && distance > 200)) {
            isLighthit = true; isHeavyhit = false;
        } else {
            isHeavyhit = true; isLighthit = false;
        }
    }

    public double getAngle() {
        // 使用标准 atan2(vy, vx)
        if (Math.abs(badmintonSpeedX) < 1e-6 && Math.abs(badmintonSpeedY) < 1e-6) return 0;
        double radians = Math.atan2(badmintonSpeedY, badmintonSpeedX);
        double degrees = Math.toDegrees(radians);
        if (degrees < 0) degrees += 360;
        return degrees;
    }

    private double getBallSpeed() {
        return Math.sqrt(badmintonSpeedX * badmintonSpeedX + badmintonSpeedY * badmintonSpeedY);
    }

    private double getDistance() {
        double dx = Math.abs(computerX - badmintonX);
        double dy = Math.abs(computerY - badmintonY);
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double map(double v, double inMin, double inMax, double outMin, double outMax) {
        if (inMax == inMin) return (outMin + outMax) * 0.5;
        double t = (v - inMin) / (inMax - inMin);
        t = Math.max(0.0, Math.min(1.0, t));
        return outMin + (outMax - outMin) * t;
    }
}
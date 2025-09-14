package org.stickbadminton.gamecomponent;
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

    public ComputerDecision() {}

    public ComputerDecision(int side,
                            double hitCenterX, double hitCenterY, boolean isShotCooldown, boolean isJumpCooldown,
                            double badmintonX, double badmintonY, double badmintonSpeedX, double badmintonSpeedY,
                            double opponentX, double opponentY)
    {
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
        if (GameProperties.difficultyLevel == 1)
            this.difficultyLevel = 0.5;
        else if (GameProperties.difficultyLevel == 2)
            this.difficultyLevel = 0.7;
        else if (GameProperties.difficultyLevel == 3)
            this.difficultyLevel = 0.8;
        else if (GameProperties.difficultyLevel == 4)
            this.difficultyLevel = 0.9;
        else
            this.difficultyLevel = 1.0;
        if(random.nextDouble()>difficultyLevel*0.1)
        {
            System.out.println("random");
            this.racketRadius = GameProperties.racketRadius*difficultyLevel;
            this.hitAreaRadius = GameProperties.hitAreaRadius*difficultyLevel;
        }
        else{
            System.out.println("666");
        this.racketRadius = GameProperties.racketRadius;
        this.hitAreaRadius = GameProperties.hitAreaRadius;
    }


        // 更新墙反弹冷却（跨帧记忆）
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

        // 记忆当前球状态，供下帧使用
        prevBallX = badmintonX;
        prevBallVX = badmintonSpeedX;
    }

    // 无球时的基础站位（左右镜像合理锚点）
    private void offBallPositioning(int side) {
        // 左侧玩家可移动区间中心大约在 260（[91, 429] 中点）
        final double anchorLeft  = 260.0;
        // 右侧玩家可移动区间中心大约在 640（[471, 809] 中点）
        final double anchorRight = 640.0;

        double target = (side == 1) ? anchorLeft : anchorRight;
        if (Math.abs(computerX - target) <= 2.0) {
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
        // 低难度也给到高反应，避免“等太久错过网前球”
        double reactionTime = REACTION_TIME_MIN;
        return random.nextDouble() > reactionTime;
    }

    private void executeStrategy(int side, boolean isShotCooldown) {
        // 0) 杀球防守（优先，且难度无关）
        if (handleSmashDefense(side, isShotCooldown)) return;

        // 1) 网前优先：预测拦截点对位 + 禁跳 + 轻击
        if (handleFrontCourt(side, isShotCooldown)) return;

        // 2) 常规位置调整
        adjustPosition();

        // 3) 常规击球策略（先考虑击球，再决定跳跃，避免起跳破坏站位）
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius)) {
            decideShotStrategy();
        }

        // 4) 再考虑跳跃（非网前）
        if (getDistance() < (100 + hitAreaRadius + racketRadius) &&
                (GameProperties.netPosition - opponentX > 100) &&
                Math.abs(computerX - badmintonX) < 16)
        {
            moveVertical();
        }
    }

    // ========== 新增：杀球防守 ==========
    private boolean handleSmashDefense(int side, boolean isShotCooldown) {
        if (!isIncomingSmash(side)) return false;

        // 目标拦截高度：根据下坠速度自适应，越快越高拦截
        double vy = badmintonSpeedY;
        double offset = clamp(
                map(vy, SMASH_VY_THRESHOLD, 1200.0, SMASH_INTERCEPT_Y_MIN, SMASH_INTERCEPT_Y_MAX),
                SMASH_INTERCEPT_Y_MIN, SMASH_INTERCEPT_Y_MAX
        );
        double targetY = computerY - offset;

        // 若球已低于该高度，降一点点再试
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

        // 横移就位（带死区）
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

            // 防守角度：左侧向右后场（约300°），右侧向左后场（约240°），加入轻微扰动
            double base = (side == 1) ? 300.0 : 240.0;
            superShotAngleDeg = base + (random.nextDouble() - 0.5) * 16.0; // ±8°
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
    // ========== 杀球防守结束 ==========

    // 网前专用：预测球在网带附近可控高度的X，去对位；禁跳，优先轻击
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

        // 就位（带死区）
        double dx = desiredX - computerX;
        if (Math.abs(dx) > NET_ALIGN_DEADZONE) {
            if (dx > 0) { isMoveRight = true; isMoveLeft = false; }
            else        { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }

        // 网前禁止起跳
        isJump = false;

        // 近距离命中检测 + 轻击（压网/搓球）
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius + 8)) {
            isShot = true;
            isLighthit = true;
            isHeavyhit = false;

            // 给出一个向对角下压的建议角度，供上层读取（可选）
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
        double maxMoveDistance = GameProperties.moveSpeed * GameProperties.frameTime;

        // 低难度也不缩小太多，保证能动到位0.34a
        if(difficultyLevel == 1) {
            distance *= 1.0;
        } else {
            distance *= (difficultyLevel * 0.1 + 0.03);
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

        if (canSmash) {
            moveHorizontal(200);
            superShot();
            return;
        }

        if (opponentDistance > 400 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
            if (ballSpeed > 800) {
                moveHorizontal(hitAreaRadius / 1.414);
                middleShot();
            } else {
                moveHorizontal(-hitAreaRadius);
                highShot();
            }
        } else if (opponentDistance > 200 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
            if (computerX - GameProperties.netPosition < highShotDistance) {
                moveHorizontal(-hitAreaRadius);
                highShot();
            } else {
                moveHorizontal(GameProperties.hitAreaCenterHeight / 1.5);
                middleShot();
            }
        } else {
            if (computerX - GameProperties.netPosition < 200 ||
                    GameProperties.netPosition - opponentX < defenceOpponentDistance) {
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

    // 预判水平落点
    public double preLanding(double targetY) {
        double y0 = badmintonY;
        double vy = badmintonSpeedY;
        double vx = badmintonSpeedX;

        double g = GameProperties.badmintonGravity;
        double dt = GameProperties.frameTime;

        double x = badmintonX;
        double y = y0;

        int steps = 0, maxSteps = 300;

        while (y > targetY && steps < maxSteps) {
            double speed = Math.sqrt(vx * vx + vy * vy);
            double air = (speed == 0) ? 0 : 0.00001 * (speed * speed);
            double ax = (speed == 0) ? 0 : -2.7 * air * (vx / speed);
            double ay = (speed == 0) ? g : g - 0.5 * air * (vy / speed);

            vx += ax * dt;
            vy += ay * dt;
            x += vx * dt;
            y += vy * dt;

            if (y <= targetY) break;
            steps++;
        }
        return x;
    }

    // 水平移动到某一目标击球高度对应的X
    public void moveHorizontal(double targetHitOffset) {
        double targetY = computerY - targetHitOffset;
        double idealHitX = preLanding(targetY);

        double distance = idealHitX - computerX;
        double maxMoveDistance = GameProperties.moveSpeed * GameProperties.frameTime;

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

    // 垂直跳跃决策（更强的“末刻取消”）
    public void moveVertical() {
        if (isJump || isJumpCooldown) return;
        if (isLikelyRecentWallBounce()) return;

        double netX = GameProperties.netPosition;
        double vx = badmintonSpeedX;
        boolean ballComingToMe = (computerX < netX && vx < 0) || (computerX > netX && vx > 0);
        if (!ballComingToMe) return;

        double timeToNet = calculateTimeToNet(netX, badmintonX, badmintonSpeedX, badmintonSpeedY);

        if (shouldJump(timeToNet)) {
            // 再做一次“可达性终检”（末刻取消）
            double idealHitY = computerY - GameProperties.hitAreaCenterHeight / 1.5;
            InterceptPrediction pred = predictAtY(idealHitY);
            if (pred.t > 0 && pred.t <= 1.0) {
                double reach = GameProperties.moveSpeed * pred.t + hitAreaRadius + racketRadius + 8.0;
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

    // 重击
    public void superShot() {
        isHeavyhit = true;
        isLighthit = false;
        isJump = true;

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
        double distance = computerX - GameProperties.netPosition;
        if (new Random().nextDouble() < 0.3 && distance > 200) {
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
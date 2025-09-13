package org.stickbadminton.gamecomponent;
import java.util.Random;
import org.stickbadminton.gamecomponent.GameProperties;
import org.stickbadminton.gamecomponent.Badminton;

public class ComputerDecision {
    // 动作控制标志
    public boolean isLighthit = false;
    public boolean isHeavyhit = false;
    public boolean isMoveLeft = false;
    public boolean isMoveRight = false;
    public boolean isJump = false;
    public boolean isShot = false;
    public boolean isJumpCooldown = false;

    // 位置和状态信息
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
    // superShot 计算出的目标角度（单位：度）。如你的 Badminton 类需要角度，可读取此字段。
    // angle < 180 会被 Badminton 识别为“扣球”，并固定速度为 2500。
    public double superShotAngleDeg = -1.0;
    public double DIFFICULTY_LEVEL;
    // AI难度和反应时间控制
    private static final double REACTION_TIME_MIN = 0.05; // 最小反应时间（秒）
    private static final double REACTION_TIME_MAX = 0.7; // 最大反应时间（秒）
    // 难度等级 0.0-1.0 (最高难度)
    // 注意：本类中引用了 DIFFICULTY_LEVEL，需在工程中定义（原工程应已有定义）

    // 预判和策略相关
    private double predictedLandingX;
    private double predictedLandingY;
    private boolean isBallApproaching = false;
    private Random random = new Random();

    public ComputerDecision() { }

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
        this.superShotPoint = computerY -30;
        //this.DIFFICULTY_LEVEL = GameProperties.difficultyLevel;
        this.DIFFICULTY_LEVEL = 0.1;
            this.racketRadius = GameProperties.racketRadius;
            this.hitAreaRadius = GameProperties.hitAreaRadius;
        // 预判球的落点
        predictBallLanding();

        // 判断球是否向我方飞来
        isBallApproaching = isBallComingToMySide(side);

        // 如果球向我方飞来，执行AI决策
        if (isBallApproaching) {
            // 添加反应时间延迟
            if (shouldReact())
            {
                executeStrategy(side, isShotCooldown);
            }
        }
        else if(badmintonSpeedX>0&&badmintonY<GameProperties.floorBallY-50){
            // 球在对方半场或未飞向我方：进行“无球/回位”站位
            offBallPositioning(side);
        }
    }

    // 新增：无球时的基础站位逻辑（对称处理左右两侧）
    private void offBallPositioning(int side) {
        // 右侧半场的回位锚点
        final double anchorRight = 675.0;
        // 左侧半场的回位锚点（按需求自行调整镜像位置）
        final double anchorLeft  = 675.0;

        if (side == -1) {
            // 我在右侧：当球在左侧半场时回到 anchorRight 附近
            if (badmintonX < GameProperties.netPosition && badmintonSpeedX<0) {
                if (computerX > anchorRight) {
                    isMoveLeft = true;  isMoveRight = false;
                } else if (computerX < anchorRight) {
                    isMoveRight = true; isMoveLeft  = false;
                } else {
                    isMoveRight = false; isMoveLeft = false;
                }
            }
        } else if (side == 1) {
            // 我在左侧：当球在右侧半场时回到 anchorLeft 附近
            if (badmintonX > GameProperties.netPosition) {
                if (computerX > anchorLeft) {
                    isMoveLeft = true;  isMoveRight = false;
                } else if (computerX < anchorLeft) {
                    isMoveRight = true; isMoveLeft  = false;
                } else {
                    isMoveRight = false; isMoveLeft = false;
                }
            }
        }
    }

    /**
     * 预判球的落点
     */
    private void predictBallLanding() {
        predictedLandingX = preLanding(GameProperties.floorBallY);
        predictedLandingY = GameProperties.floorBallY;
    }

    /**
     * 判断球是否向我方飞来
     */
    private boolean isBallComingToMySide(int side) {
        boolean ballInMyArea = (side == 1 && badmintonX < GameProperties.netPosition) ||
                (side == -1 && badmintonX > GameProperties.netPosition);

        // 关键修复：判断球是否正在向我方飞来
        boolean ballComingToMe = false;
        if (side == 1) {
            // 左侧玩家：球应该从右向左飞来（badmintonSpeedX < 0）
            ballComingToMe = badmintonSpeedX < 0||badmintonX < GameProperties.netPosition;
        } else if (side == -1) {
            // 右侧玩家：球应该从左向右飞来（badmintonSpeedX > 0）
            ballComingToMe = badmintonSpeedX > 0||badmintonX > GameProperties.netPosition;
        }

        // 球在我方区域、正在向我方飞来、且高度合适
        return ballInMyArea && ballComingToMe &&
                badmintonY > GameProperties.netHeight &&
                badmintonY < GameProperties.floorBallY - 30;
    }

    /**
     * 模拟反应时间，增加AI的真实感
     */
    private boolean shouldReact() {
        // 最高难度下，AI几乎总是能及时反应
        double reactionTime = REACTION_TIME_MIN;
        return random.nextDouble() > reactionTime; // 最高难度下反应时间极短
    }

    /**
     * 执行AI策略
     */
    private void executeStrategy(int side, boolean isShotCooldown) {

        // 首先进行位置调整
            adjustPosition();

        // 最后决定是否跳跃
        if (getDistance()<(100+hitAreaRadius+racketRadius)&&
                (GameProperties.netPosition - opponentX > 100)
                && Math.abs(computerX-badmintonX)<10)
        {
            moveVertical();
        }
        // 然后决定击球策略
        if (!isShotCooldown && getDistance()<(hitAreaRadius+racketRadius)) {
            decideShotStrategy();
        }



        if(badmintonX < GameProperties.netPosition)
        {
            System.out.println("move");
            if(computerX > 725)
            {
                isMoveLeft = true;
                isMoveRight = false;
            }
            else if(computerX < 725)
            {
                isMoveRight = true;
                isMoveLeft = false;
            }
            else
            {
                isMoveRight = false;
                isMoveLeft = false;
            }
        }
    }

    /**
     * 调整位置以更好地击球
     */
    private void adjustPosition() {
        // 计算理想击球位置
        double idealX = predictedLandingX;
        double distance = idealX - computerX;
        double maxMoveDistance = GameProperties.moveSpeed * GameProperties.frameTime;

        // 根据难度调整移动精度 - 最高难度下移动非常精确
        double moveAccuracy = DIFFICULTY_LEVEL;
            //distance *= 0.034;简单难度基石
        distance *=1;
        if (Math.abs(distance) > maxMoveDistance) {
            if (distance > 0) {
                isMoveRight = true;
                isMoveLeft = false;
            } else {
                isMoveLeft = true;
                isMoveRight = false;
            }
        } else {
            isMoveLeft = isMoveRight = false;
        }
    }

    /**
     * 决定击球策略 - 优化版本
     */
    private void decideShotStrategy() {
        isShot = true;

        // 基础数据
        double opponentDistance = Math.abs(opponentX - computerX);
        double ballHeight = badmintonY;
        double ballSpeed = getBallSpeed();

        // 放宽的“可扣杀”窗口
        boolean canSmash =
                ballHeight < (computerY - 20) &&
                        ballHeight > (computerY - 120) &&
                        getDistance() < (hitAreaRadius + racketRadius) &&
                        Math.abs(badmintonSpeedY) < 500;

        // 优先：只要满足扣杀窗口，直接 superShot
        if (canSmash) {
            moveHorizontal(200);
            System.out.println("superShot (direct) | ballY=" + badmintonY
                    + " compY=" + computerY
                    + " dist=" + getDistance()
                    + " vy=" + badmintonSpeedY);
            superShot();
            return;
        }

        // 其余策略
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

    /*TODO:进行人物站位的计算(根据羽球实时位置)
    进行跳跃高精度杀球superShot()
    起高球highShot()
    中场球middleShot()
    反应时间reactTime()
    击球方法(复用)
     */

    //预判水平落点
    public double preLanding(double targetY) {
        double y0 = badmintonY;
        double vy = badmintonSpeedY;
        double vx = badmintonSpeedX;

        double g = GameProperties.badmintonGravity;
        double dt = GameProperties.frameTime; // 每帧时间间隔

        double x = badmintonX;
        double y = y0;

        int steps = 0;
        int maxSteps = 300;

        while (y > targetY && steps < maxSteps) {
            double airResistance = 0.00001 * (Math.pow(vx, 2) + Math.pow(vy, 2));
            double ax = -2.7 * airResistance * (vx / Math.sqrt(Math.pow(vx, 2) + Math.pow(vy, 2)));
            double ay = g - 0.5 * airResistance * (vy / Math.sqrt(Math.pow(vx, 2) + Math.pow(vy, 2)));
            vx += ax * dt;
            vy += ay * dt;
            x += vx * dt;
            y += vy * dt;
            if (y <= targetY) {
                break;
            }
            steps++;
        }

        return x;
    }

    /**
     * 水平移动逻辑 - 优化版本
     */
    public void moveHorizontal(double targetHitOffset) {
        // 目标击球 Y 坐标
        double targetY = computerY - targetHitOffset;

        // 预测在 targetY 高度时，球的 X 位置
        double idealHitX = preLanding(targetY);

        // 计算移动距离和方向
        double distance = idealHitX - computerX;
        double maxMoveDistance = GameProperties.moveSpeed * GameProperties.frameTime;

        // 根据难度调整移动精度 - 最高难度下移动非常精确
        if (random.nextDouble() > DIFFICULTY_LEVEL) {
            // 最高难度下误差极小
            distance *= (0.5 + random.nextDouble() * 0.3);
        }

        // 边界检查
        double leftBoundary = GameProperties.playFieldLeft + GameProperties.playerWidth/2;
        double rightBoundary = GameProperties.playFieldRight - GameProperties.playerWidth/2;

        if (computerX < leftBoundary) {
            isMoveRight = true;
            isMoveLeft = false;
        } else if (computerX > rightBoundary) {
            isMoveLeft = true;
            isMoveRight = false;
        } else if (Math.abs(distance) > maxMoveDistance) {
            if (distance > 0) {
                isMoveRight = true;
                isMoveLeft = false;
            } else {
                isMoveLeft = true;
                isMoveRight = false;
            }
        } else {
            isMoveLeft = isMoveRight = false;
        }
    }

    /**
     * 垂直移动逻辑 - 优化版本
     */
    public void moveVertical() {
        // 1. 避免重复跳跃
        if (isJump || isJumpCooldown) {
            return;
        }

        // 1.5 避免刚碰墙反弹后盲跳
        if (isLikelyRecentWallBounce()) {
            return;
        }

        // 2. 定义关键常量
        double netX = GameProperties.netPosition;
        double currentX = badmintonX;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;

        // 3. 判断球是否向我方飞来
        boolean ballComingToMe = false;
        if (computerX < netX) {
            // 左侧玩家：球应该从右向左飞来（vx < 0）
            ballComingToMe = vx < 0;
        } else if (computerX > netX) {
            // 右侧玩家：球应该从左向右飞来（vx > 0）
            ballComingToMe = vx > 0;
        }
        if (!ballComingToMe) {
            return;
        }

        // 4. 计算球到达球网的时间
        double timeToNet = calculateTimeToNet(netX, currentX, vx, vy);

        // 5. 智能跳跃决策
        if (shouldJump(timeToNet, currentX, vy)) {
            isJump = true;
        }
    }

    /**
     * 计算球到达球网的时间
     */
    private double calculateTimeToNet(double netX, double currentX, double vx, double vy) {
        double dt = GameProperties.frameTime;
        double timeToNetInSeconds = 0.0;
        double x = currentX;
        double tempVx = vx;
        double tempVy = vy;
        int maxSteps = 300;
        int steps = 0;

        // 判断球是否正在向我方球网移动
        boolean movingTowardsNet = (computerX < netX && tempVx > 0) || (computerX > netX && tempVx < 0);

        if (movingTowardsNet) {
            while (Math.abs(x - netX) > 1 && steps < maxSteps) {
                double speed = Math.sqrt(tempVx * tempVx + tempVy * tempVy);
                double airResistance;
                if (speed == 0) {
                    airResistance = 0;
                    tempVy += GameProperties.badmintonGravity * dt;
                } else {
                    airResistance = 0.00001 * (speed * speed);
                    tempVy += (GameProperties.badmintonGravity - 0.5 * airResistance * (tempVy / speed)) * dt;
                    tempVx -= 2.7 * airResistance * (tempVx / speed) * dt;
                }
                x += tempVx * dt;
                timeToNetInSeconds += dt;
                steps++;
            }
        }

        return timeToNetInSeconds;
    }

    /**
     * 智能跳跃决策
     */
    private boolean shouldJump(double timeToNet, double currentX, double vy) {
        // 基本跳跃窗口（加上上限，避免“过早/过晚”起跳）
        boolean basicJumpCondition = timeToNet > 0.2 && timeToNet < 1.0;

        // 避免刚碰墙反弹后盲跳
        if (isLikelyRecentWallBounce()) {
            return false;
        }

        // 理想击球高度（略低于肩部，便于进攻/控球）
        double idealHitY = computerY - GameProperties.hitAreaCenterHeight / 1.5;

        // 预测：球在 idealHitY 时的X与所需时间
        InterceptPrediction pred = predictAtY(idealHitY);

        // 若无法在有限时间内到达该高度，或时间过长，放弃跳跃
        if (pred.t <= 0 || pred.t > 1.2) {
            return false;
        }

        // 计算在 pred.t 时间内的水平可达范围（移动距离 + 击球圈范围）
        double horizontalReach = GameProperties.moveSpeed * pred.t + hitAreaRadius + racketRadius + 10.0;
        boolean interceptReachable = Math.abs(pred.x - computerX) <= horizontalReach;

        // 贴墙不可打（减少墙边盲跳）
        double minWallDist = Math.min(pred.x - GameProperties.playFieldLeft, GameProperties.playFieldRight - pred.x);
        boolean tooCloseToWall = minWallDist < Math.max(12.0, racketRadius * 0.5);

        // 高度条件：不要在球明显过低时仍跳
        boolean heightCondition = badmintonY < idealHitY + 100;

        if (!interceptReachable || tooCloseToWall || !heightCondition) {
            return false;
        }

        // 进攻/防守加成
        boolean aggressiveJump = (opponentX < 150 && random.nextDouble() < 0.8 * DIFFICULTY_LEVEL);
        boolean defensiveJump = ((currentX - GameProperties.netPosition > 250) &&
                badmintonY < computerY - 150 && vy > 0);

        // 难度调节：最高难度基本不失误
        double jumpProbability = DIFFICULTY_LEVEL;
        if (random.nextDouble() > jumpProbability * 0.95) {
            return false; // 模拟失误
        }

        return basicJumpCondition || aggressiveJump || defensiveJump;
    }

    /**
     * 判断是否为“疑似刚刚发生的墙面反弹”
     * 规则：球贴近左右空气墙，且水平速度朝场内方向（反弹后典型特征）
     */
    private boolean isLikelyRecentWallBounce() {
        double leftGap = badmintonX - GameProperties.playFieldLeft;
        double rightGap = GameProperties.playFieldRight - badmintonX;

        boolean nearLeftAndInward = leftGap >= 0 && leftGap < 18.0 && badmintonSpeedX > 0;
        boolean nearRightAndInward = rightGap >= 0 && rightGap < 18.0 && badmintonSpeedX < 0;

        return nearLeftAndInward || nearRightAndInward;
    }

    /**
     * 预测球在目标高度 targetY 时的 X 位置与所需时间
     */
    private InterceptPrediction predictAtY(double targetY) {
        double dt = GameProperties.frameTime;

        double x = badmintonX;
        double y = badmintonY;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;

        double time = 0.0;
        int steps = 0;
        int maxSteps = 300;

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

            // 穿越或到达目标高度时停止
            if ((prevY - targetY) * (y - targetY) <= 0) {
                break;
            }

            prevY = y;
            steps++;
        }

        return new InterceptPrediction(x, time, vy);
    }

    // 小型结果结构
    private static class InterceptPrediction {
        final double x;
        final double t;
        final double vy;
        InterceptPrediction(double x, double t, double vy) {
            this.x = x;
            this.t = t;
            this.vy = vy;
        }
    }

    /**
     * 杀球 - 重击（重写）
     * 说明：
     * - 这里仅计算"角度"并写入 superShotAngleDeg。
     * - 你的 Badminton 类里会在 angle < 180 时把合速度固定为 2500，并据此计算分量。
     */
    // 重写并简化：仅在 badmintonY 接近 superShotPoint（按一帧内允许误差）时才触发扣杀，
    public void superShot() {
        // Set action flags for a heavy hit with jumping
        isHeavyhit = true;
        isLighthit = false;
        isJump = true; // Always jump for superShot

        // Log for debugging
        System.out.println("superShot executed at y=" + badmintonY + ", targetPoint=" + superShotPoint);

        // Determine which side of the court we're on
        boolean isRightSide = computerX > GameProperties.netPosition;

        // Calculate the target angle for the shot
        // Angle < 180 will be treated as a "smash" by Badminton.java with speed 2500
        double targetAngle;

        // Calculate distance to opponent
        double opponentDistance = Math.abs(opponentX - computerX);

        if (isRightSide) {
            // We're on the right side, aiming left
            if (opponentDistance > 300) {
                // Opponent is far, aim toward the back corner (steep angle)
                targetAngle = 165;
            } else {
                // Opponent is close, aim between opponent and net (flatter angle)
                targetAngle = 155;
            }
        } else {
            // We're on the left side, aiming right
            if (opponentDistance > 300) {
                // Opponent is far, aim toward the back corner (steep angle)
                targetAngle = 15;
            } else {
                // Opponent is close, aim between opponent and net (flatter angle)
                targetAngle = 25;
            }
        }

        // Add slight randomness for unpredictability (±5 degrees)
        targetAngle += (new Random().nextDouble() - 0.5) * 10;

        // Log the decision
        System.out.println("superShot angle: " + targetAngle + " degrees, opponent at: " + opponentX);

        // Set the superShot angle for Badminton class to use
        superShotAngleDeg = targetAngle;
    }

    /**
     * 高球 - 轻击
     */
    public void highShot() {
        System.out.println("highShot");
        isLighthit = true;
        isHeavyhit = false;
    }

    /**
     * 中场球 - 轻击/重击
     */
    public void middleShot() {
        System.out.println("middleShot");
        double distance = computerX -  GameProperties.netPosition;
        if(new Random().nextDouble() < 0.3 && distance > 200)
        {
            isLighthit = true;
            isHeavyhit = false;
        }
        else
        {
            isHeavyhit = true;
            isLighthit = false;
        }

    }

    public double getAngle() {
        // 速度为 0 时，返回上一次角度或默认值
        if (Math.abs(badmintonSpeedX) < 1e-6 && Math.abs(badmintonY) < 1e-6) {
            return 0; // 或返回 0，或缓存 lastAngle
        }
        // 这里保留原逻辑（如需可改为 atan2(vy, vx)）
        double radians = Math.atan2(badmintonSpeedX, badmintonY);
        double degrees = Math.toDegrees(radians);
        if (degrees < 0) {
            degrees += 360;
        }
        return degrees;
    }

    /**
     * 获取球的当前速度大小
     */
    private double getBallSpeed() {
        return Math.sqrt(badmintonSpeedX * badmintonSpeedX + badmintonSpeedY * badmintonSpeedY);
    }

    private double getDistance()
    {
        double distanceX = Math.abs(computerX - badmintonX);
        double distanceY = Math.abs(computerY - badmintonY);
        return Math.sqrt(distanceX * distanceX + distanceY * distanceY);
    }
}
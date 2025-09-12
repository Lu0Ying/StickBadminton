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

    // AI难度和反应时间控制
    private static final double REACTION_TIME_MIN = 0.05; // 最小反应时间（秒）
    private static final double REACTION_TIME_MAX = 0.1; // 最大反应时间（秒）
    private static final double DIFFICULTY_LEVEL = 1.0; // 难度等级 0.0-1.0 (最高难度)

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
//        if (random.nextDouble() > 0.95*DIFFICULTY_LEVEL)
//        {
//            this.racketRadius = GameProperties.racketRadius*(1.0/DIFFICULTY_LEVEL);
//            this.hitAreaRadius = GameProperties.hitAreaRadius*(1.0/DIFFICULTY_LEVEL);
//        }
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
        else if(badmintonSpeedX>0&&badmintonY<GameProperties.floorBallY){
            // 球在对方半场或未飞向我方：进行“无球/回位”站位
            offBallPositioning(side);
        }
    }
    // 新增：无球时的基础站位逻辑（对称处理左右两侧）
    private void offBallPositioning(int side) {
        // 右侧半场的回位锚点，原来你的 725
        final double anchorRight = 675.0;
        // 左侧半场的回位锚点，镜像一个位置（可按手感调整，比如 175）
        final double anchorLeft  = 675.0;

        if (side == -1) {
            // 我在右侧：当球在左侧半场时回到 725 附近
            if (badmintonX < GameProperties.netPosition) {
                if (computerX > anchorRight) {
                    isMoveLeft = true;  isMoveRight = false;
                } else if (computerX < anchorRight) {
                    isMoveRight = true; isMoveLeft  = false;
                } else {
                    isMoveRight = false; isMoveLeft = false;
                }
            }
        } else if (side == 1) {
            // 我在左侧：当球在右侧半场时回到 175 附近
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
        double reactionTime = REACTION_TIME_MIN +
                (REACTION_TIME_MAX - REACTION_TIME_MIN) * (1.0 - DIFFICULTY_LEVEL);
        return random.nextDouble() > reactionTime * 0.1; // 最高难度下反应时间极短
    }

    /**
     * 执行AI策略
     */
    private void executeStrategy(int side, boolean isShotCooldown) {

        // 首先进行位置调整
        adjustPosition();

        // 然后决定击球策略
        if (!isShotCooldown && getDistance()<(hitAreaRadius+racketRadius)) {
            decideShotStrategy();
        }

        // 最后决定是否跳跃
//
        if (getDistance()<(100+hitAreaRadius+racketRadius)&&
                (computerX-GameProperties.netPosition)>150 && GameProperties.netPosition - opponentX > 100)
                {
            moveVertical();
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
        if (random.nextDouble() > moveAccuracy) {
            distance *= (0.9 + random.nextDouble() * 0.1); // 最高难度下误差极小
        }

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
     * 决定击球策略 - 最高难度优化版本
     */
//    private void decideShotStrategy() {
//        isShot = true;
//
//        // 根据对手位置和球的位置选择击球策略
//        double opponentDistance = Math.abs(opponentX - computerX);
//        double ballHeight = badmintonY;
//        double ballSpeed = getBallSpeed();
//
//        // 最高难度下的智能击球策略
//        if (opponentDistance > 400 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
//            // 对手距离较远，优先选择杀球
//            if (ballHeight < computerY+racketRadius+hitAreaRadius - racketRadius/3)
//            {
//                //(hitAreaRadius+2.0/3*racketRadius)*0.866
//                moveHorizontal((hitAreaRadius+2.0/3*racketRadius)*0.866);
//                superShot();
//            }
//            else if(computerX - GameProperties.netPosition < highShotDistance)
//            {
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            }
//            else if (ballSpeed > 800) {
//                // 高速球时选择中场球
//                //hitAreaRadius/1.414
//                moveHorizontal(hitAreaRadius/1.414);
//                middleShot();
//            } else {
//                // 低速球时选择高球
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            }
//        } else if (opponentDistance > 200 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
//            // 中等距离，根据球速和高度选择策略
//            if(computerX - GameProperties.netPosition < highShotDistance)
//            {
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            }
//            else if (ballHeight < computerY+hitAreaRadius) {
//                moveHorizontal(GameProperties.hitAreaCenterHeight/2);
//                superShot();
//            } else {
//                moveHorizontal(GameProperties.hitAreaCenterHeight/1.5);
//                middleShot();
//            }
//        } else {
//            // 对手距离较近，选择高球或快速中场球
//            if(computerX - GameProperties.netPosition < 200|| GameProperties.netPosition - opponentX < defenceOpponentDistance)
//            {
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            }
//            else if (ballSpeed > 600) {
//                moveHorizontal(GameProperties.hitAreaCenterHeight/1.2);
//                middleShot();
//            } else {
//                moveHorizontal(-GameProperties.hitAreaCenterHeight/1.5);
//                highShot();
//            }
//        }
//    }
    /**
     * 决定击球策略 - 优化版本
     */
//    private void decideShotStrategy() {
//        isShot = true;
//        // 基础数据
//        double opponentDistance = Math.abs(opponentX - computerX);
//        double ballHeight = badmintonY;
//        double ballSpeed = getBallSpeed();
//        double idealSmashHeight = computerY - 40; // 理想扣杀高度
//
//        // 判断是否适合扣杀的条件
//        boolean canSmash = ballHeight < idealSmashHeight && // 球在合适的高度
//                ballHeight > computerY - 80 && // 球不能太低
//                badmintonSpeedY >= -200 && // 球不能向上飞得太快
//                getDistance() < (hitAreaRadius + racketRadius) * 0.8; // 距离合适
//
//        // 最高难度下的智能击球策略
//        if (opponentDistance > 400 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
//            // 对手距离较远，优先选择杀球
//            if (canSmash) {
//                moveHorizontal(30); // 向上移动30像素的击球位置
//                superShot();
//            } else if (computerX - GameProperties.netPosition < highShotDistance) {
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            } else if (ballSpeed > 800) {
//                moveHorizontal(hitAreaRadius/1.414);
//                middleShot();
//            } else {
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            }
//        } else if (opponentDistance > 200 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
//            // 中等距离，根据球速和高度选择策略
//            if (computerX - GameProperties.netPosition < highShotDistance) {
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            } else if (canSmash && opponentDistance > 250) {
//                moveHorizontal(20); // 稍微向上的击球位置
//                superShot();
//            } else {
//                moveHorizontal(GameProperties.hitAreaCenterHeight/1.5);
//                middleShot();
//            }
//        } else {
//            // 对手距离较近，选择高球或快速中场球
//            if (computerX - GameProperties.netPosition < 200 ||
//                    GameProperties.netPosition - opponentX < defenceOpponentDistance) {
//                moveHorizontal(-hitAreaRadius);
//                highShot();
//            } else if (ballSpeed > 600) {
//                moveHorizontal(GameProperties.hitAreaCenterHeight/1.2);
//                middleShot();
//            } else {
//                moveHorizontal(-GameProperties.hitAreaCenterHeight/1.5);
//                highShot();
//            }
//        }
//    }
    private void decideShotStrategy() {
        isShot = true;

        // 基础数据
        double opponentDistance = Math.abs(opponentX - computerX);
        double ballHeight = badmintonY;
        double ballSpeed = getBallSpeed();

        // 放宽的“可扣杀”窗口：
        // - 高度：在肩部上方一定范围（更宽一些，-120~-20）
        // - 距离：在击球圈+球拍半径之内（不再乘 0.8）
        // - 竖直速度：不过快（避免超高速上升/下降）
        boolean canSmash =
                ballHeight < (computerY - 20) &&
                        ballHeight > (computerY - 120) &&
                        getDistance() < (hitAreaRadius + racketRadius) &&
                        Math.abs(badmintonSpeedY) < 500;

        // 优先：只要满足扣杀窗口，直接 superShot，不再被对手距离分支卡住
        if (canSmash) {
            // 轻微微调站位，提高命中
            moveHorizontal(-superShotPoint);
            System.out.println("superShot (direct) | ballY=" + badmintonY
                    + " compY=" + computerY
                    + " dist=" + getDistance()
                    + " vy=" + badmintonSpeedY);
            superShot();
            return;
        }

        // 下面保留原有分支做战术补充（当不满足扣杀窗口时）
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
            distance *= (0.95 + random.nextDouble() * 0.05);
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

        // 2. 定义关键常量
        double netX = GameProperties.netPosition;
        double currentX = badmintonX;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;

        // 3. 判断球是否向我方飞来 - 修复逻辑
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

        //5. 智能跳跃决策
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
        // 基础跳跃条件
        boolean basicJumpCondition = timeToNet > 0.2 ; // 0.2-0.5秒窗口

        // 高度条件
        double idealHitY = computerY - GameProperties.hitAreaCenterHeight / 1.5;
        boolean heightCondition = badmintonY < idealHitY + 100;

        // 进攻性跳跃 - 当对手站位靠后时，最高难度下更积极
        boolean aggressiveJump = (opponentX < 150 && random.nextDouble() < 0.8 * DIFFICULTY_LEVEL);

        // 防守性跳跃 - 当球已经过网且高度合适时
        boolean defensiveJump = ((currentX - GameProperties.netPosition > 250) &&
                badmintonY < computerY - 150 && vy > 0);

        // 根据难度调整跳跃概率 - 最高难度下几乎不会失误
        double jumpProbability = DIFFICULTY_LEVEL;
        if (random.nextDouble() > jumpProbability * 0.95) { // 最高难度下失误率极低
            return false; // 模拟AI失误
        }

        return (basicJumpCondition && heightCondition) || aggressiveJump || defensiveJump;
    }
    /**
     * 判断是否应该进行进攻性跳跃
     */
    /**
     * 杀球 - 重击（重写）
     * 说明：
     * - 这里仅计算“角度”并写入 superShotAngleDeg。
     * - 你的 Badminton 类里会在 angle < 180 时把合速度固定为 2500，并据此计算分量。
     */
    /**
     * 杀球 - 重击（重写）
     * 说明：
     * - 这里仅计算"角度"并写入 superShotAngleDeg。
     * - 你的 Badminton 类里会在 angle < 180 时把合速度固定为 2500，并据此计算分量。
     */
    // 重写并简化：仅在 badmintonY 接近 superShotPoint（按一帧内允许误差）时才触发扣杀，
// 不再强求擦网，只需把球打到对方半场即可。
    public void superShot() {
        // 只在球下落阶段考虑扣杀
        if (badmintonSpeedY <= 0) {
            return;
        }

        // 计算跳跃最高点的高度
        double jumpHeight = (GameProperties.jumpSpeedY * GameProperties.jumpSpeedY) / (2 * GameProperties.jumpGravity);
        double superShotPoint = computerY - jumpHeight;

        // 计算球到达superShotPoint所需时间（考虑空气阻力）
        double timeToHitPoint = calculateTimeToReachYWithAirResistance(superShotPoint);

        // 计算球员跳到最高点所需时间
        double timeToJumpPeak = GameProperties.jumpSpeedY / GameProperties.jumpGravity;

        // 如果球到达时间与跳跃时间匹配，则触发扣杀和跳跃
        if (timeToHitPoint >= 0 && Math.abs(timeToHitPoint - timeToJumpPeak) < 0.1) {
            System.out.println("superShot triggered at Y=" + superShotPoint +
                    " (currentY=" + badmintonY + ", timeToHit=" + timeToHitPoint + ")");
            isHeavyhit = true;
            isLighthit = false;
            isShot = true;
            isJump = true; // 设置跳跃标志

            // 计算扣杀角度和目标落点
            calculateSuperShotAngle();
        }
    }

    /**
     * 计算球到达指定Y坐标所需的时间（秒），考虑空气阻力
     * 使用更精确的数值积分方法
     */
    private double calculateTimeToReachYWithAirResistance(double targetY) {
        if (Math.abs(badmintonY - targetY) < 1.0) {
            return 0; // 已经非常接近目标高度
        }

        // 基本运动参数
        double g = GameProperties.badmintonGravity;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;
        double currentY = badmintonY;
        double dt = GameProperties.frameTime / 10.0; // 使用更小的步长提高精度

        // 模拟球的运动，直到达到目标高度
        double timeElapsed = 0;
        int maxSteps = 2000; // 防止无限循环

        while ((currentY < targetY && vy > 0) || (currentY > targetY && vy < 0)) {
            if (timeElapsed > 5.0 || maxSteps-- <= 0) {
                return -1; // 超时或步数过多，无法到达目标高度
            }

            // 计算空气阻力
            double speed = Math.sqrt(vx * vx + vy * vy);
            double airResistance = 0.00001 * (speed * speed);

            // 计算加速度分量
            double ax = -2.7 * airResistance * (vx / Math.max(speed, 0.001)); // 避免除以零
            double ay = g - 0.5 * airResistance * (vy / Math.max(speed, 0.001));

            // 更新速度
            vx += ax * dt;
            vy += ay * dt;

            // 更新位置
            currentY += vy * dt;

            // 更新时间
            timeElapsed += dt;

            // 检查是否达到目标高度
            if (Math.abs(currentY - targetY) < 1.0) {
                return timeElapsed;
            }
        }

        return -1; // 无法到达目标高度
    }

    /**
     * 计算超级扣杀的角度，考虑空气阻力
     */
    private void calculateSuperShotAngle() {
        final double x0 = badmintonX;
        final double y0 = badmintonY;
        final double xNet = GameProperties.netPosition;
        final double s = 2500.0; // 扣杀速度

        // 确定对方半场范围
        double halfLeft, halfRight;
        if (computerX < xNet) { // 我在左半场，目标右半场
            halfLeft = xNet + GameProperties.playerWidth;
            halfRight = GameProperties.playFieldRight - 40;
        } else { // 我在右半场，目标左半场
            halfLeft = GameProperties.playFieldLeft + 40;
            halfRight = xNet - GameProperties.playerWidth;
        }

        // 选择远离对手的落点
        double mid = 0.5 * (halfLeft + halfRight);
        double targetX = (opponentX < mid) ? (halfRight - 20) : (halfLeft + 20);
        double yTarget = GameProperties.floorBallY - 6.0;

        // 计算角度，考虑空气阻力
        double angle = calculateOptimalAngleWithAirResistance(x0, y0, targetX, yTarget, s);

        // 限制角度范围
        angle = Math.max(15.0, Math.min(55.0, angle));

        // 保存角度
        superShotAngleDeg = angle;

        System.out.println("Super shot angle: " + angle + " degrees, targetX: " + targetX);
    }

    /**
     * 计算最优发射角度，考虑空气阻力
     * 使用更精确的二分法寻找最佳角度
     */
    private double calculateOptimalAngleWithAirResistance(double x0, double y0, double targetX, double targetY, double initialSpeed) {
        double bestAngle = 45.0; // 默认角度
        double minDistance = Double.MAX_VALUE;

        // 测试多个角度，使用更精细的步长
        for (double angle = 15.0; angle <= 55.0; angle += 1.0) {
            // 模拟球的轨迹
            double[] landingPoint = simulateTrajectory(x0, y0, angle, initialSpeed);
            double distance = Math.abs(landingPoint[0] - targetX);

            // 更新最佳角度
            if (distance < minDistance) {
                minDistance = distance;
                bestAngle = angle;
            }
        }

        // 在最佳角度附近进行更精细的搜索
        double fineTunedAngle = bestAngle;
        double fineTunedMinDistance = minDistance;

        for (double angle = bestAngle - 2.0; angle <= bestAngle + 2.0; angle += 0.5) {
            if (angle < 15.0 || angle > 55.0) continue;

            double[] landingPoint = simulateTrajectory(x0, y0, angle, initialSpeed);
            double distance = Math.abs(landingPoint[0] - targetX);

            if (distance < fineTunedMinDistance) {
                fineTunedMinDistance = distance;
                fineTunedAngle = angle;
            }
        }

        return fineTunedAngle;
    }

    /**
     * 模拟球的轨迹，考虑空气阻力
     * 使用更精确的模拟方法
     */
    private double[] simulateTrajectory(double startX, double startY, double angle, double initialSpeed) {
        double g = GameProperties.badmintonGravity;
        double dt = GameProperties.frameTime / 5.0; // 使用更小的步长提高精度

        // 初始速度分量
        double vx = initialSpeed * Math.cos(Math.toRadians(angle));
        double vy = initialSpeed * Math.sin(Math.toRadians(angle));

        // 初始位置
        double x = startX;
        double y = startY;

        int maxSteps = 2000;
        int steps = 0;

        // 模拟直到球落地或超出最大步数
        while (y < GameProperties.floorBallY && steps < maxSteps) {
            // 计算空气阻力
            double speed = Math.sqrt(vx * vx + vy * vy);
            double airResistance = 0.00001 * (speed * speed);

            // 计算加速度分量
            double ax = -2.7 * airResistance * (vx / Math.max(speed, 0.001)); // 避免除以零
            double ay = g - 0.5 * airResistance * (vy / Math.max(speed, 0.001));

            // 更新速度
            vx += ax * dt;
            vy += ay * dt;

            // 更新位置
            x += vx * dt;
            y += vy * dt;

            steps++;

            // 检查是否过网
            if (Math.abs(x - GameProperties.netPosition) < 10 && y > GameProperties.floorBallY - GameProperties.netHeight) {
                // 如果球会触网，调整角度
                return new double[]{x, y};
            }
        }

        return new double[]{x, y};
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
     * 中场球 - 轻击
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

        // 使用 Math.atan2(dy, dx) 计算弧度
        // 注意：Math.atan2(y, x) 返回的是 (-π, π] 弧度
        double radians = Math.atan2(badmintonSpeedX, badmintonY);

        // 转为角度，并转为 [0, 360)
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
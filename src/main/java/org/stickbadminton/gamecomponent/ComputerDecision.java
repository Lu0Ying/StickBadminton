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
        if(new Random().nextDouble() < 0.8 && getDistance()<(hitAreaRadius+racketRadius) &&
                (computerX-GameProperties.netPosition)>150 &&
                GameProperties.netPosition - opponentX > 100) {
            moveVertical();
        }
        if(badmintonX < GameProperties.netPosition)
        {
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
    private void decideShotStrategy() {
        isShot = true;

        // 根据对手位置和球的位置选择击球策略
        double opponentDistance = Math.abs(opponentX - computerX);
        double ballHeight = badmintonY;
        double ballSpeed = getBallSpeed();

        // 最高难度下的智能击球策略
        if (opponentDistance > 400 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
            // 对手距离较远，优先选择杀球
            if (ballHeight < computerY+racketRadius+hitAreaRadius - racketRadius/3)
            {
                //(hitAreaRadius+2.0/3*racketRadius)*0.866
                moveHorizontal((hitAreaRadius+2.0/3*racketRadius)*0.866);
                superShot();
            }
            else if(computerX - GameProperties.netPosition < highShotDistance)
            {
                moveHorizontal(-hitAreaRadius);
                highShot();
            }
            else if (ballSpeed > 800) {
                // 高速球时选择中场球
                //hitAreaRadius/1.414
                moveHorizontal(hitAreaRadius/1.414);
                middleShot();
            } else {
                // 低速球时选择高球
                moveHorizontal(-hitAreaRadius);
                highShot();
            }
        } else if (opponentDistance > 200 && GameProperties.netPosition - opponentX < defenceOpponentDistance) {
            // 中等距离，根据球速和高度选择策略
            if(computerX - GameProperties.netPosition < highShotDistance)
            {
                moveHorizontal(-hitAreaRadius);
                highShot();
            }
            else if (ballHeight < computerY+hitAreaRadius) {
                moveHorizontal(GameProperties.hitAreaCenterHeight/2);
                superShot();
            } else {
                moveHorizontal(GameProperties.hitAreaCenterHeight/1.5);
                middleShot();
            }
        } else {
            // 对手距离较近，选择高球或快速中场球
            if(computerX - GameProperties.netPosition < 200|| GameProperties.netPosition - opponentX < defenceOpponentDistance)
            {
                moveHorizontal(-hitAreaRadius);
                highShot();
            }
            else if (ballSpeed > 600) {
                moveHorizontal(GameProperties.hitAreaCenterHeight/1.2);
                middleShot();
            } else {
                moveHorizontal(-GameProperties.hitAreaCenterHeight/1.5);
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
        // 基础跳跃条件
        boolean basicJumpCondition = timeToNet > 0.2 && timeToNet < 0.5; // 0.2-0.5秒窗口

        // 高度条件
        double idealHitY = computerY - GameProperties.hitAreaCenterHeight / 1.5;
        boolean heightCondition = badmintonY < idealHitY + 100;

        // 进攻性跳跃 - 当对手站位靠后时，最高难度下更积极
        boolean aggressiveJump = (opponentX < 150 && random.nextDouble() < 0.8 * DIFFICULTY_LEVEL);

        // 防守性跳跃 - 当球已经过网且高度合适时
        boolean defensiveJump = (currentX > GameProperties.netPosition &&
                               badmintonY < computerY - 100 && vy > 0);

        // 根据难度调整跳跃概率 - 最高难度下几乎不会失误
        double jumpProbability = DIFFICULTY_LEVEL;
        if (random.nextDouble() > jumpProbability * 0.95) { // 最高难度下失误率极低
            return false; // 模拟AI失误
        }

        return (basicJumpCondition && heightCondition) || aggressiveJump || defensiveJump;
    }
    /**
     * 杀球 - 重击
     */
    public void superShot()
    {
        // 标记这是一次“重击型”出球（延续原有语义）
        isHeavyhit = true;
        isShot = true;
        double netY = GameProperties.floorBallY - GameProperties.netHeight + 20;
        // 基础数据
        double g = GameProperties.badmintonGravity;
        double x0 = badmintonX;
        double y0 = badmintonY;
        double xNet = GameProperties.netPosition;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;
        double approachNetTime = calculateTimeToNet(xNet,x0,vx,vy);
        // 请用你工程中的“网顶世界坐标”替换此字段名（若没有，可据实际计算）
        double netTopY = GameProperties.floorBallY - GameProperties.netHeight; // TODO: 若命名不同请替换
        double clearance = 6.0;                  // “擦网”余量（像素，可调 3~10）
        double yAtNet = netTopY + clearance;

        // 判断出球方向（站在网左打向右，或相反）
        final int dir = (computerX < xNet) ? +1 : -1;

        // 到网的水平距离（保证为正）

    }

    /**
     * 高球 - 轻击
     */
    public void highShot() {
        isLighthit = true;
        isHeavyhit = false;
    }

    /**
     * 中场球 - 轻击
     */
    public void middleShot() {
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
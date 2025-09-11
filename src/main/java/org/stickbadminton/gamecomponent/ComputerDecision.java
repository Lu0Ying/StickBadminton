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
    public boolean isJumpCooldown;
    
    // 位置和状态信息
    public double computerX = 0.0;
    public double computerY = 0.0;
    public double badmintonX;
    public double badmintonY;
    public double badmintonSpeedX;
    public double badmintonSpeedY;
    public double opponentX;
    public double opponentY;
    
    // AI难度和反应时间控制
    private static final double REACTION_TIME_MIN = 0.05; // 最小反应时间（秒）
    private static final double REACTION_TIME_MAX = 0.1; // 最大反应时间（秒）
    private static final double DIFFICULTY_LEVEL = 1.0; // 难度等级 0.0-1.0 (最高难度)
    
    // 预判和策略相关
    private double predictedLandingX;
    private double predictedLandingY;
    private boolean isBallApproaching = false;
    private Random random = new Random();
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
        predictedLandingX = preLanding(GameProperties.floorY);
        predictedLandingY = GameProperties.floorY;
    }
    
    /**
     * 判断球是否向我方飞来
     */
    private boolean isBallComingToMySide(int side) {
        boolean ballInMyArea = (side == 1 && badmintonX < GameProperties.netPosition) || 
                              (side == -1 && badmintonX > GameProperties.netPosition);
        
        // 球在我方区域且高度合适
        return ballInMyArea && badmintonY > GameProperties.netHeight && 
               badmintonY < GameProperties.floorY - 30;
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
        if (!isShotCooldown) {
            decideShotStrategy();
        }
        
        // 最后决定是否跳跃
        moveVertical();
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
        if (opponentDistance > 200) {
            // 对手距离较远，优先选择杀球
            if (ballHeight < computerY - GameProperties.hitAreaCenterHeight/2)
            {
                moveHorizontal(GameProperties.hitAreaCenterHeight/3);
                superShot();
            } else if (ballSpeed > 800) {
                // 高速球时选择中场球
                moveHorizontal(GameProperties.hitAreaCenterHeight/1.5);
                middleShot();
            } else {
                // 低速球时选择高球
                moveHorizontal(-GameProperties.hitAreaCenterHeight/1.5);
                highShot();
            }
        } else if (opponentDistance > 100) {
            // 中等距离，根据球速和高度选择策略
            if (ballHeight < computerY - GameProperties.hitAreaCenterHeight/3) {
                moveHorizontal(GameProperties.hitAreaCenterHeight/2);
                superShot();
            } else {
                moveHorizontal(GameProperties.hitAreaCenterHeight/1.5);
                middleShot();
            }
        } else {
            // 对手距离较近，选择高球或快速中场球
            if (ballSpeed > 600) {
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

        while (y > targetY) {
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

        // 3. 判断球是否向我方飞来
        boolean ballComingToMe = (computerX < netX && vx > 0) || (computerX > netX && vx < 0);
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
    public void superShot() {
        isHeavyhit = true;
        isLighthit = false;
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
        isLighthit = true;
        isHeavyhit = false;
    }
    
    /**
     * 获取AI难度等级
     */
    public static double getDifficultyLevel() {
        return DIFFICULTY_LEVEL;
    }
    
    /**
     * 设置AI难度等级 (0.0-1.0)
     */
    public static void setDifficultyLevel(double level) {
        if (level >= 0.0 && level <= 1.0) {
            // 注意：这里需要修改为实例变量或使用其他方式存储
            // 当前是静态常量，无法直接修改
        }
    }
    
    /**
     * 检查位置是否在有效范围内
     */
    private boolean isValidPosition(double x, double y) {
        return x >= GameProperties.playFieldLeft && 
               x <= GameProperties.playFieldRight &&
               y >= 0 && 
               y <= GameProperties.floorY;
    }
    
    /**
     * 计算两点之间的距离
     */
    private double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
    
    /**
     * 获取球的当前速度大小
     */
    private double getBallSpeed() {
        return Math.sqrt(badmintonSpeedX * badmintonSpeedX + badmintonSpeedY * badmintonSpeedY);
    }
    
    /**
     * 判断球是否在击球范围内
     */
    private boolean isInHitRange() {
        double distance = calculateDistance(computerX, computerY, badmintonX, badmintonY);
        return distance <= GameProperties.hitAreaRadius + GameProperties.racketRadius &&
               distance >= GameProperties.hitAreaRadius - GameProperties.racketRadius;
    }
}

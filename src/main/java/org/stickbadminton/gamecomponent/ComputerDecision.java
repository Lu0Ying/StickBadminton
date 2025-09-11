package org.stickbadminton.gamecomponent;
import java.util.Random;
import org.stickbadminton.gamecomponent.GameProperties;
import org.stickbadminton.gamecomponent.Badminton;
public class ComputerDecision {
    public boolean isLighthit = false;
    public boolean isHeavyhit = false;
    public boolean isMoveLeft = false;
    public boolean isMoveRight = false;
    public boolean isJump = false;
    public boolean isShot = false;
    public boolean isJumpCooldown;
    public double computerX = 0.0;//?
    public double computerY = 0.0;//?
    public double badmintonX;
    public double badmintonY;
    public double badmintonSpeedX;
    public double badmintonSpeedY;
    public double opponentX;
    public double gravity = 13.0;
    public ComputerDecision(int side,
                            double hitCenterX, double hitCenterY, boolean isShotCooldown, boolean isJumpCooldown,
                            double badmintonX, double badmintonY, double badmintonSpeedX, double badmintonSpeedY,
                            double opponentX, double opponentY)
    {
        // side: 当前人物的朝向（即是屏幕中的哪个火柴人，side = 1 时为左侧火柴人，side = -1 时为右侧火柴人）
        // hitCenterX, hitCenterY: 球拍旋转轴心
        // isShotCooldown, isJumpCooldown: 是否处于击球 / 跳跃冷却期，冷却期中不能再次进行相同操作
        // badmintonX, badmintonY, badmintonSpeedX, badmintonSpeedY: 羽毛球的位置及速度
        // opponentX, opponentY: 对方火柴人的人头的中心点位置坐标
        // 重要: 更多常量参数见 GameProperties 类中
        // 在此构造函数中对 isMoveLeft, isMoveRight, isJump, isShot 赋值
        this.badmintonSpeedX = badmintonSpeedX;
        this.badmintonSpeedY = badmintonSpeedY;
        this.badmintonX = badmintonX;
        this.badmintonY = badmintonY;
        this.computerX = hitCenterX;
        this.computerY = hitCenterY;
        this.opponentX = opponentX;
        this.isJumpCooldown = isJumpCooldown;
        Random random = new Random();
        double rand = random.nextDouble();
        if(badmintonX > GameProperties.netPosition && badmintonY > GameProperties.netHeight)
        {
            if(!isShotCooldown && badmintonY > GameProperties.floorY)
            {
                isShot =  true;
                //打到拍子的上三分之一
                if(opponentX > 300)
                {
                    moveHorizontal(GameProperties.hitAreaCenterHeight/3);
                    superShot();
                }
                //以此类推
                else if (opponentX > 150)
                {
                    moveHorizontal(GameProperties.hitAreaCenterHeight/1.5);
                    middleShot();
                }
                else
                {
                    moveHorizontal(-GameProperties.hitAreaCenterHeight/1.5);
                    highShot();
                }
            }
            moveVertical();
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

    //水平移动逻辑
    public void moveHorizontal(double targetHitOffset) {
        // 目标击球 Y 坐标
        // 注意：Y 向下为正，所以减去偏移
        double targetY = computerY - targetHitOffset;

        // 预测在 targetY 高度时，球的 X 位置
        double idealHitX = preLanding(targetY);

        // 计算移动距离和方向
        double distance = idealHitX - computerX;
        double maxMoveDistance = GameProperties.moveSpeed * GameProperties.frameTime;

        if (distance > maxMoveDistance) {
            isMoveRight = true;
            isMoveLeft = false;
        } else if (distance < -maxMoveDistance) {
            isMoveLeft = true;
            isMoveRight = false;
        } else {
            isMoveLeft = isMoveRight = false;
        }
    }
    public void moveVertical() {
        // 1. 避免重复跳跃
        if (isJump || isJumpCooldown) {
            return;
        }

        // 2. 定义关键常量
        double netX = GameProperties.netPosition;         // 球网 X 位置
        double currentX = badmintonX;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;

        // 3. 只有当球向我方飞来时才考虑起跳（vx > 0 表示向右）
        if (vx <= 0) {
            return; // 球没往我这边飞
        }

        // 4. 计算球到达球网需要的时间（单位：帧）
        double distanceToNet = netX - currentX; // 注意：球从左向右飞，netX > currentX

        if (distanceToNet <= 0) {
            // 球已经过网 → 进入我方区域，可以考虑起跳
            distanceToNet = 0; // 视为瞬间过网
        }

        // 假设 vx 不恒定，考虑空气阻力，使用数值模拟计算到达球网所需时间（秒）
        double dt = GameProperties.frameTime;
        double timeToNetInSeconds = 0.0;
        double x = badmintonX;
        int maxSteps = 300;
        int steps = 0;

        if (vx > 0 && badmintonX < GameProperties.netPosition) {
            while (x < GameProperties.netPosition && steps < maxSteps) {
                double speed = Math.sqrt(vx * vx + vy * vy);
                double airResistance;
                if (speed == 0) {
                    airResistance = 0;
                    vy += GameProperties.badmintonGravity;
                } else {
                    airResistance = 0.00001 * (speed * speed);
                    vy += GameProperties.badmintonGravity - 0.5 * airResistance * (vy / speed);
                    vx -= 2.7 * airResistance * (vx / speed);
                }
                x += vx * dt;
                timeToNetInSeconds += dt;
                steps++;
            }
        }

        // 转换为帧数
        int framesToNet = (int)(timeToNetInSeconds / dt);

        // 判断是否在最佳起跳窗口内（18~22 帧），且球尚未过低
        int jumpWindow = 20; // 理想起跳提前帧数
        int tolerance = 2;   // 容差 ±2 帧

        if (Math.abs(framesToNet - jumpWindow) <= tolerance) {
            double idealHitY = computerY - GameProperties.hitAreaCenterHeight / 1.5;
            if (badmintonY < idealHitY + 100) {
                isJump = true;
            }
        }

        //如果对手站位靠后，更倾向于跳起进攻
        if (opponentX < 150 && Math.random() < 0.35)
        {
            isJump = true;
        }
        if (currentX > netX && badmintonY < computerY - 100 && vy > 0)
        {
            isJump = true;
        }
    }
    public void superShot()
    {
        isHeavyhit = true;
        isLighthit = false;
    }
    public void highShot()
    {
        isLighthit = true;
        isHeavyhit = false;
    }
    public void middleShot()
    {
        isLighthit = true;
        isHeavyhit = false;
    }
}

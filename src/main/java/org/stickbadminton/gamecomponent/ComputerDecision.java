package org.stickbadminton.gamecomponent;

public class ComputerDecision {
    public boolean isMoveLeft = false;
    public boolean isMoveRight = false;
    public boolean isJump = false;
    public boolean isShot = false;
    public ComputerDecision(int side,
                            double hitCenterX, double hitCenterY, boolean isShotCooldown, boolean isJumpCooldown,
                            double badmintonX, double badmintonY, double badmintonSpeedX, double badmintonSpeedY,
                            double opponentX, double opponentY) {
        // side: 当前人物的朝向（即是屏幕中的哪个火柴人，side = 1 时为左侧火柴人，side = -1 时为右侧火柴人）
        // hitCenterX, hitCenterY: 球拍旋转轴心
        // isShotCooldown, isJumpCooldown: 是否处于击球 / 跳跃冷却期，冷却期中不能再次进行相同操作
        // badmintonX, badmintonY, badmintonSpeedX, badmintonSpeedY: 羽毛球的位置及速度
        // opponentX, opponentY: 对方火柴人的人头的中心点位置坐标
        // 重要: 更多常量参数见 GameProperties 类中
        // 在此构造函数中对 isMoveLeft, isMoveRight, isJump, isShot 赋值
    }
}

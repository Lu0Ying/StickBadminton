package org.stickbadminton.logic;

public class GameProperties {
    // 每帧的时间，即 fps 帧率的倒数
    public static final double frameTime = 1 / 60.0;
    // 房间尺寸，也为窗口尺寸
    public static final double roomWidth = 900.0;
    public static final double roomHeight = 600.0;
    // 左右空气墙的位置，羽毛球碰空气墙反弹，火柴人移动受空气墙阻挡
    public static final double playFieldLeft = 70.0;
    public static final double playFieldRight = 830.0;
    // 人物的碰撞箱宽度
    // 即人物的移动范围为：左边火柴人 (playFieldLeft + playerWidth/2) ~ (netPosition - playerWidth/2)
    //                 右边火柴人 (netPosition + playerWidth/2) ~ (playFieldRight - playerWidth/2)
    public static final double playerWidth = 42.0;
    // 人物的高度 (站立时头的中心距离地板的高度)
    public static final double playerHeight = 72.0;
    // 地板的 y 坐标，人物站立在地板上，羽毛球碰到地板则丢分
    public static final double floorY = 550.0;
    public static final double floorBallY = 565.0;
    // 球网的位置 (x 坐标)
    public static final double netPosition = 450.0;
    // 球网高
    public static final double netHeight = 120.0;
    // 发球线到球网的距离
    public static final double serveLineDistance = 150.0;
    // 远近判定的距离
    public static final double powerDistance = 50.0;
    // 击球时，判定区域为以肩膀为圆心的圆环区域的两段（上方击球分一段扇形，下方捞球分一段扇形）
    // 肩膀（圆心）到火柴人脚的高度
    public static final double hitAreaCenterHeight = 56.0;
    // 圆环的基础半径
    public static final double hitAreaRadius = 60.0;
    // 球拍半径
    public static final double racketRadius = 45.0;
    // 上方击球的圆心角 - 45°（面前的一段扇形 + 身后的 45° 扇形）
    public static final double hitAreaAngleUp = 75.0;
    // 下方击球的圆心角 - 20°（面前的一段扇形 + 身后的 20° 扇形）
    public static final double hitAreaAngleDown = 75.0;
    // 上方击球动画时长（单位：秒）
    public static final double shotAnimationTime = 0.15;
    // 下方击球动画时长（单位：秒）
    public static final double shotAnimationTime2 = 0.17;
    // 击球冷却时间（单位：秒）
    public static final double shotCooldown = 0.5;
    // 跳跃冷却时间（单位：秒）
    public static final double jumpCooldown = 0.67;
    // 人物跳跃的初速度
    public static final double jumpSpeedY = 700.0;
    // 人物在跳跃过程中受到的重力
    public static final double jumpGravity = 35.0;
    // 羽毛球重力
    public static final double badmintonGravity = 13.0;
    // 人物移动速度
    public static final double moveSpeed = 250.0;
    // 重击所耗体力 (人物体力满格为 100)
    public static final double heavyShotEnergy = 35.0;
    // 轻击所耗体力 (体力归0时也能轻击)
    public static final double lightShotEnergy = 8.0;
    // 跳跃所耗体力
    public static final double jumpEnergy = 25.0;
    // 平地移动时所恢复体力 (单位：体力/秒)
    public static final double moveEnergyRecover = 3.0;
    // 站着不动时所恢复体力 (单位：体力/秒)
    public static final double idleEnergyRecover = 20.0;
    // 体力为 0 时的移动速度 (插值用)
    public static final double moveSpeed0 = 100.0;
    // 满速移动所需的体力阈值（移速插值上界，StickMan 移动与 AI 决策预判共用同一公式）
    public static final double fullSpeedEnergy = 40.0;
    // 难度达到该等级（含 G 键满级）时，AI 启用完整体力管理
    public static final int energyManageDifficulty = 3;
    // 低体力站桩回血阈值：低于此值 AI 放大就位死区、站着回血（站立 20/s 远高于移动 3/s）
    public static final double lowEnergyIdleThreshold = 60.0;
    // 起跳后需保留的体力余量，保证跳完至少还能轻击
    public static final double jumpEnergyReserve = 15.0;
    // 5级 AI 属性倍率：决策以4级为基础，移速与击打力度 ×1.1
    public static final double level5StatBoost = 1.1;
    // 高难度AI跳杀的最低球高(肩上像素)：球低于此高度改站立重扣——
    // 球本就在站立触及圈(105px)内，起跳白花25体力还要等0.67s冷却
    public static final double smashJumpHeight = 60.0;
    // 站立重扣的卡位击球点(肩上像素)：按球落到该高度的横向位置提前就位
    public static final double smashStandContactHeight = 40.0;


    // 以下为变量
    // 人机难度
    public static int difficultyLevel; // 1 ~ 5
    // 双方人物类型
    public static int characterType1 = 1;
    public static int characterType2 = 1;
    // 单人模式(1) 还是双人模式(2)
    public static int matchMode = 2;
    // 无限体力模式
    public static boolean infiniteEnergyMode = false;
}

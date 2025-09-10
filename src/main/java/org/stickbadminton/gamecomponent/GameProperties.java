package org.stickbadminton.gamecomponent;

public class GameProperties {
    // 房间尺寸，也为窗口尺寸
    public static final double roomWidth = 900.0;
    public static final double roomHeight = 600.0;
    // 左右空气墙的位置，羽毛球碰空气墙反弹，火柴人移动受空气墙阻挡
    public static final double playFieldLeft = 50.0;
    public static final double playFieldRight = 850.0;
    // 人物的碰撞箱宽度
    // 即人物的移动范围为：左边火柴人 (playFieldLeft + playerWidth/2) ~ (netPosition - playerWidth/2)
    //                 右边火柴人 (netPosition + playerWidth/2) ~ (playFieldRight - playerWidth/2)
    public static final double playerWidth = 24.0;
    // 人物的高度 (站立时头的中心距离地板的高度)
    public static final double playerHeight = 72.0;
    // 地板的 y 坐标，人物站立在地板上，羽毛球碰到地板则丢分
    public static final double floorY = 550.0;
    // 球网的位置 (x 坐标)
    public static final double netPosition = 450.0;
    // 球网高
    public static final double netHeight = 120.0;
    // 击球时，判定区域为以肩膀为圆心的圆环区域的两段（上方击球分一段扇形，下方捞球分一段扇形）
    // 肩膀（圆心）到火柴人脚的高度
    public static final double hitAreaCenterHeight = 56.0;
    // 圆环的内半径和外半径
    public static final double hitAreaRadiusOuter = 67.0;
    public static final double hitAreaRadiusInner = 42.0;
    // 上方击球的圆心角的一半（竖直方向为对称轴）
    public static final double hitAreaAngleUp = 90.0;
    // 下方击球的圆心角（面前的一段扇形）
    public static final double hitAreaAngleDown = 45.0;
    // 击球冷却时间（单位：秒）
    public static final double shotCooldown = 0.5;
    // 跳跃冷却时间（单位：秒）
    public static final double jumpCooldown = 0.5;
}

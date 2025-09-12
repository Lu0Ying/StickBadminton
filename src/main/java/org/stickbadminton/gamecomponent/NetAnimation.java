package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import javafx.util.Duration;
import org.stickbadminton.GameObject;

/**
 * 网和触网动画类
 * 管理静态网和触网动画的播放
 */
public class NetAnimation extends GameObject {
    // 静态网的GameObject实例
    private GameObject netStatic;
    // 触网动画的GameObject实例（6帧动画）
    private GameObject netAnimation;
    // 动画播放状态标志
    private boolean isAnimating = false;
    // 动画计时器
    private double animationTime = 0;
    // 动画总持续时间（秒）
    private final double ANIMATION_DURATION = 1;
    // 网的中心点X坐标
    private final double netCenterX = GameProperties.netPosition-3;
    // 网的底部Y坐标
    private final double netBottomY = GameProperties.floorY+145;
    // 缩放因子
    private final double scaleFactor = 2.0;

    //构造函数 - 初始化网和动画
    public NetAnimation() {
        // 调用父类构造函数，创建基础网对象
        super("net", new Image("net_static.png"));

        // 初始化静态网对象
        netStatic = new GameObject("net_static", new Image("net_static.png"));

        // 应用缩放
        netStatic.getEntity().setScaleX(scaleFactor);
        netStatic.getEntity().setScaleY(scaleFactor);

        // 设置静态网位置（网的中心点对齐，底部对齐地板）
        double staticNetWidth = netStatic.getSprite().getTexture().getWidth() * scaleFactor;
        netStatic.setPosition(netCenterX - staticNetWidth / 2,
                netBottomY - GameProperties.netHeight * scaleFactor);
        // 设置渲染层级
        netStatic.setPaintIndex(10);

        // 初始化触网动画（6帧动画）
        setupNetAnimation();

        // 设置基础网对象不可见（因为使用netStatic来显示）
        setVisible(false);
    }

    //设置触网动画资源（6帧动画）
    private void setupNetAnimation() {
        try {
            // 加载6帧动画图片资源
            Image animationImage = new Image("net_crash_animation.png");
            // 动画帧数
            int frameCount = 6;
            // 计算每帧宽度
            int frameWidth = (int) animationImage.getWidth() / frameCount;
            // 计算每帧持续时间
            double frameDuration = ANIMATION_DURATION / frameCount;

            // 创建动画GameObject（6帧动画）
            netAnimation = new GameObject("net_animation", animationImage, frameWidth,
                    Duration.seconds(frameDuration));
            // 应用相同的缩放
            netAnimation.getEntity().setScaleX(scaleFactor);
            netAnimation.getEntity().setScaleY(scaleFactor);
            // 初始设置为不可见
            netAnimation.setVisible(false);
            // 设置动画位置（与静态网位置完全一致，考虑缩放）
            double animationWidth = frameWidth * scaleFactor;
            netAnimation.setPosition(netCenterX - animationWidth / 2,
                    netBottomY - GameProperties.netHeight * scaleFactor);
            // 设置比静态网更高的渲染层级
            netAnimation.setPaintIndex(11);

        } catch (Exception e) {
            // 动画加载失败时的错误处理
            System.err.println("无法加载触网动画: " + e.getMessage());
            // 使用静态网作为后备方案
            netAnimation = new GameObject("net_animation_fallback", new Image("net_static.png"));
            // 应用缩放
            netAnimation.getEntity().setScaleX(scaleFactor);
            netAnimation.getEntity().setScaleY(scaleFactor);
            // 设置后备动画位置（与静态网位置一致）
            double fallbackWidth = netAnimation.getSprite().getTexture().getWidth() * scaleFactor;
            netAnimation.setPosition(netCenterX - fallbackWidth / 2,
                    netBottomY - GameProperties.netHeight * scaleFactor);
        }
    }

    //每帧更新方法
    @Override
    public void onUpdate() {
        // 如果正在播放动画，更新动画状态
        if (isAnimating) {
            updateAnimation();
        }
    }

    //更新动画状态
    private void updateAnimation() {
        // 增加动画时间
        animationTime += GameProperties.frameTime;
        // 检查动画是否结束
        if (animationTime >= ANIMATION_DURATION) {
            // 停止动画
            stopAnimation();
        }
        // 不需要其他特效，只需要播放6帧动画
    }

    //播放触网动画
    public void playCrashAnimation() {
        // 设置动画状态
        isAnimating = true;
        // 重置动画计时器
        animationTime = 0;
        // 隐藏静态网
        netStatic.setVisible(false);
        // 显示动画网
        netAnimation.setVisible(true);
        // 从头开始播放6帧动画
        netAnimation.getSprite().playWithoutLoop();
    }

    //停止动画并重置状态
    private void stopAnimation() {
        // 重置动画状态
        isAnimating = false;
        // 重置计时器
        animationTime = 0;
        // 显示静态网
        netStatic.setVisible(true);
        // 隐藏动画网
        netAnimation.setVisible(false);
        // 停止动画播放
        netAnimation.getSprite().stop();
        // 重置动画网位置（确保位置一致）
        double animationWidth = (netAnimation.getSprite().getTexture().getWidth() / 6) * scaleFactor;
        netAnimation.setPosition(netCenterX - animationWidth / 2,
                netBottomY - GameProperties.netHeight * scaleFactor);
    }

    //激活网对象
    @Override
    public void activate() {
        // 激活父对象（但父对象不可见）
        super.activate();
        // 激活静态网
        netStatic.activate();
        // 激活动画网
        netAnimation.activate();
        // 初始显示静态网
        netStatic.setVisible(true);
        netAnimation.setVisible(false);
    }

    //禁用网对象
    @Override
    public void deactivate() {
        // 禁用静态网
        netStatic.deactivate();
        // 禁用动画网
        netAnimation.deactivate();
        super.deactivate();
    }

    //设置可见性
    @Override
    public void setVisible(boolean visible) {
        // 设置父对象可见性（父对象始终不可见）
        super.setVisible(false);
        if (visible) {
            // 根据动画状态设置子对象可见性
            netStatic.setVisible(!isAnimating);
            netAnimation.setVisible(isAnimating);
        } else {
            // 隐藏所有子对象
            netStatic.setVisible(false);
            netAnimation.setVisible(false);
        }
    }

    //可能会用到的函数
    public GameObject getNetStatic() {
        return netStatic;
    }
    public GameObject getNetAnimation() {
        return netAnimation;
    }

    public boolean isAnimating() {
        return isAnimating;
    }
}
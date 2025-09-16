package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.gamecomponent.network.NetworkClient;

public class BadmintonNet extends Badminton {

    private NetworkClient netClient;

    public BadmintonNet(NetworkClient netClient) {
        super();
        this.netClient = netClient;
        this.isNetworkControlled = true;
    }

    public BadmintonNet(int _sideServe, NetworkClient netClient) {
        super(_sideServe);
        this.netClient = netClient;
        this.isNetworkControlled = true;
    }

    @Override
    public void onUpdate() {
        // 在联网模式下，不进行本地物理计算，仅依赖服务器状态更新
        // 可以在这里更新粒子或其他非物理视觉效果，如果需要基于当前速度
        if (TouchedTime > 10) {
            ParticleFX.updataFire(emitter, speedX, speedY);
        } else {
            ParticleFX.closeParticle(emitter);
            TouchedTime++;
        }
    }

    public void applyServerState(double x, double y, double speedX, double speedY, double rotation) {
        setPosition(x, y);
        this.speedX = speedX;
        this.speedY = speedY;
        setRotation(rotation);

        // 本地计算拖尾等视觉效果，但不影响物理
        // 碰撞事件（如落地、触网）由服务器广播处理，不在本地检测
    }

    // 重写击球方法：发送请求到服务器，而不是本地应用
    @Override
    public void lightHit(double angle) {
        if (netClient != null) {
            netClient.sendLine("HIT:light:" + angle);
        }
    }

    @Override
    public void heavyHit(double angle) {
        if (netClient != null) {
            netClient.sendLine("HIT:heavy:" + angle);
        }
    }

    // 禁用本地碰撞处理方法，由服务器权威决定并广播事件
    @Override
    public void onHitGround() {
        // no-op in network mode
    }

    @Override
    public void onNetCrashed() {
        // no-op in network mode
    }
}
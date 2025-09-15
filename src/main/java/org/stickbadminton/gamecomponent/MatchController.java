// MatchController.java
package org.stickbadminton.gamecomponent;

import com.almasb.fxgl.texture.AnimatedTexture;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import org.stickbadminton.gamecomponent.network.NetworkClient;

public class MatchController extends GameObject {

    private boolean isHost = false;
    private NetworkClient netClient;

    public MatchController() {
        super("controller", (AnimatedTexture) null); // 假设无图像
    }

    public void setHost(boolean isHost) {
        this.isHost = isHost;
    }

    public void setNetClient(NetworkClient netClient) {
        this.netClient = netClient;
    }

    public void matchStart() {
        // 初始化比赛
        // 如果是主机，启动某些逻辑；否则等待服务端
    }

    @Override
    public void onUpdate() {
        if (netClient == null) {
            // 单机模式：正常更新
            updateLocalBallState();
        } else {
            // 联网模式：不更新本地球状态，等待服务端
            // 但可以更新其他如玩家动画
        }
    }

    private void updateLocalBallState() {
        // 单机模式的球更新逻辑
    }

    // 由服务端数据更新球状态
    public void applyBallState(double x, double y, double vx, double vy) {
        BadmintonNet badminton = (BadmintonNet) inRoom.getObject("badminton");
        if (badminton != null) {
            badminton.setPosition(x, y);
            badminton.speedX = vx;
            badminton.speedY = vy;
            // 更新其他属性如果需要
        }
    }

    // 示例：击球事件
    public void onPlayerHit(double angle, boolean isHeavy) {
        if (netClient != null) {
            netClient.sendHit(angle, isHeavy);
        } else {
            // 单机处理
        }
    }

    // 示例：球落地
    public void onBallGroundHit(int side) {
        if (isHost && netClient != null) {
            // 主机计算得分并广播
            // 但根据要求，服务器已处理
        } else {
            // 客户端忽略本地落地
        }
    }
}
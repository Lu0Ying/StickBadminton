package org.stickbadminton.logic;
import org.stickbadminton.Sprite;
import org.stickbadminton.entity.Badminton;
import org.stickbadminton.entity.StickMan;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.ui.UIDigitView;
import org.stickbadminton.ui.UIGameOver;

import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;
import org.stickbadminton.KeyInput;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.UIObject;
import org.stickbadminton.network.NetworkClient;


public class MatchController extends GameObject{
    private double ballHitGroundTimer = 0.0;
    private double scoreChangeTimer = 0.0;
    private int lastPointWinner = 0; // -1 -> 右侧, 1 -> 左侧
    private boolean isServeReadying = false; // 是否处于发球阶段
    public int serveSide = 1; // -1 -> 右侧发球, 1 -> 左侧发球
    private double playBGMTimer = 0.0;
    private double matchEndTimer = 0.0;

    // 添加字段
    private static boolean isHost = false;
    private static NetworkClient netClient = null;

    // 本球得分后的目标分：由 onBallGroundHit（本地）/applyRemoteScore（主机消息）计算，
    // changeScore 时统一 set —— 双端 set 同一目标值，避免各自 +1 增量产生计数偏差
    private int pendingLeft = 0;
    private int pendingRight = 0;


    // 设置方法（static：会话状态全局唯一，选人房/对战房/退出路径统一经 endSession 收口）
    public static void setHost(boolean Host) {
        isHost = Host;
    }

    public static void setNetClient(NetworkClient Client) {
        netClient = Client;
    }

    // 可选：添加 getter
    public static boolean isHost() {
        return isHost;
    }

    public static NetworkClient getNetClient() {
        return netClient;
    }

    // 会话结束：断开连接并复位网络静态状态（幂等），回主菜单等出口统一调用
    public static void endSession() {
        if (netClient != null) {
            netClient.disconnect();
            netClient = null;
        }
        isHost = false;
        org.stickbadminton.KeyInput.disableNetworkMode();
    }

    // 一次性诊断日志：客户端首条球状态应用
    private boolean ballRecvLogged = false;

    // G 键上一帧按住状态（边沿检测，按一次切换一次）
    private boolean gPrevHeld = false;

    /**
     * 调试用：p1（左半场 stickman_left）在满级 AI 与玩家之间切换。
     * 单人/双人/联机三个模块统一从这里进入——联机时 G 经服务器 P1 白名单广播，
     * 双端注入后各自在本方法触发，状态保持一致。
     */
    private void toggleP1AI() {
        GameObject p1Obj = inRoom.getObject("stickman_left");
        if (!(p1Obj instanceof StickMan p1)) return;
        p1.aiMaxLevel = !p1.aiMaxLevel;
        p1.isAIControlled = p1.aiMaxLevel;
        System.out.println("[Debug] p1 -> " + (p1.aiMaxLevel ? "AI(max)" : "player"));
    }

    public MatchController() {
        super("controller", Sprite.loadImage("/images/stickman/stickman_head1.png"));
        setVisible(false);
    }

    public void applyBallState(double x, double y, double speedX, double speedY) {
        // 忽略自己上报的回环（服务器广播含发送者）：否则主机的球被标记为网络控制，
        // onUpdate 提前 return，本地落地/触网检测与发球跟随全部中断，球飞出屏幕
        if (isHost) return;

        Badminton ball = (Badminton) inRoom.getObject("badminton");
        if (ball == null) return;

        if (!ballRecvLogged) {
            ballRecvLogged = true;
            System.out.println("[Ball] client first apply: " + x + "," + y);
        }

        double dx = x - ball.getCenterX();
        double dy = y - ball.getCenterY();
        // 跳变（重置后的新球在屏幕外 y=700、快照复位）直接就位，避免客机看到球从底部飘入；
        // 正常发球准备期心跳跟随距离 <30px，仍走平滑插值
        double lerpFactor = (Math.abs(dx) > 60 || Math.abs(dy) > 60) ? 1.0 : 0.35;
        ball.setPositionWithCenter(
                ball.getCenterX() + dx * lerpFactor,
                ball.getCenterY() + dy * lerpFactor
        );
        ball.speedX = ball.speedX + (speedX - ball.speedX) * lerpFactor;
        ball.speedY = ball.speedY + (speedY - ball.speedY) * lerpFactor;

        ball.isNetworkControlled = true;
    }

    public void matchStart() {
        ballHitGroundTimer = 0.0;
        scoreChangeTimer = 0.01;
        playBGMTimer = 0.0;
        MissTrace.resetRally(); // 失分诊断：开球即开始记录首个球
    }
    public void onBallGroundHit(int winner) {
        ballHitGroundTimer = 1.0;
        lastPointWinner = winner;

        // 计算本球得分后的目标分
        pendingLeft = readDigit("score_left");
        pendingRight = readDigit("score_right");
        if (winner == -1) pendingRight = (pendingRight + 1) % 10;
        else pendingLeft = (pendingLeft + 1) % 10;

        // 主机上报比分，客户端收到后启动同一套计分/重置流程
        if (isHost && netClient != null) {
            netClient.sendLine(String.format("SCORE:%d:%d", pendingLeft, pendingRight));
        }
    }

    /**
     * 联机客户端/旁观者：收到主机上报的比分，启动与本地完全相同的
     * 计分 → 音效 → 球员回位 → 重置球 → 比赛结束流程，实现双端状态对齐。
     */
    public void applyRemoteScore(int left, int right) {
        if (isHost) return; // 忽略自己上报的回显，避免双触发

        int localLeft = readDigit("score_left");
        int localRight = readDigit("score_right");
        if (left == localLeft && right == localRight) return; // 相同快照（开局 0:0 等），直接吸收

        pendingLeft = left;
        pendingRight = right;
        // 增量方即得分方；中途加入快照（比分跳变）时以领先方近似，仅影响结束显示与发球方
        if (left > localLeft) lastPointWinner = 1;
        else if (right > localRight) lastPointWinner = -1;
        else lastPointWinner = (left >= right) ? 1 : -1;

        // 已在得分流程中则只更新目标分，不重启计时（避免重复快照打断流程）
        if (ballHitGroundTimer <= 0.0 && scoreChangeTimer <= 0.01) {
            ballHitGroundTimer = 1.0;
        }
    }

    /** 发球方快照对齐（主要服务中途加入/旁观者；正常流程由 lastPointWinner 推出） */
    public void applyRemoteServe(int side) {
        serveSide = (side >= 0) ? 1 : -1;
    }

    private int readDigit(String uiName) {
        UIObject view = inRoom.getUiObject(uiName);
        return (view instanceof UIDigitView) ? ((UIDigitView) view).getCurrentNumber() : 0;
    }

    public void changeScore() {
        SoundPlay.playSound("add_score.mp3", 1.0);
        // 统一 set 目标分（双端同值），而非各自 +1
        UIObject scoreLeft = inRoom.getUiObject("score_left");
        if (scoreLeft instanceof UIDigitView) {
            ((UIDigitView) scoreLeft).setCurrentNumber(pendingLeft);
        }
        UIObject scoreRight = inRoom.getUiObject("score_right");
        if (scoreRight instanceof UIDigitView) {
            ((UIDigitView) scoreRight).setCurrentNumber(pendingRight);
        }

        //判断获胜
        if (pendingLeft >= 9 || pendingRight >= 9) {
            // 游戏结束，显示结果
            matchEndTimer = 1.5;
        }
    }

    public void resetBall() {
        if (inRoom.getObject("badminton") != null)
            inRoom.removeObject(inRoom.getObject("badminton"));
        Badminton badminton = (Badminton) inRoom.addObject(new Badminton(serveSide));
        badminton.setPosition(450 - 200 * serveSide, 700);
        badminton.speedX = 0;
        badminton.speedY = 0;

        // 联机客户端：新球立即标记网络控制，
        // 否则开局 scoreChangeTimer=0.01 分支重建的球会退化成本地物理并脱离同步
        if (netClient != null && !isHost) {
            badminton.isNetworkControlled = true;
        }

        // 主机立即发送新状态
        if (isHost && netClient != null) {
            String ballState = String.format("BALL:%.2f:%.2f:%.2f:%.2f", badminton.getCenterX(), badminton.getCenterY(), 0.0, 0.0);
            netClient.sendLine(ballState);
        }
    }

    private void showGameResult(String winner) {
        // 创建游戏结束UI
        UIGameOver gameOverUI = new UIGameOver(winner);

        // 将UI添加到当前房间
        if (inRoom != null) {
            // 居中显示
            double centerX = (GameProperties.roomWidth - 300) / 2; // 假设UI宽度为300
            double centerY = (GameProperties.roomHeight - 200) / 2; // 假设UI高度为200

            inRoom.addUiObject(gameOverUI, (int) centerX, (int) centerY);

            // 暂停游戏逻辑
            // 可以添加一个游戏暂停的状态变量来控制更新逻辑
        }
    }
    @Override
    public void onUpdate() {
        // G 键边沿检测：把 p1（左半场）在满级 AI 与玩家间切换，按一次切换一次。
        // 联机时 G 走服务器 P1 白名单广播、双端注入后各自触发，状态保持一致
        boolean gHeld = KeyInput.isKeyHolding(KeyCode.G);
        if (gHeld && !gPrevHeld) {
            toggleP1AI();
        }
        gPrevHeld = gHeld;

        if (matchEndTimer > 0.0) {
            matchEndTimer -= GameProperties.frameTime;
            if (matchEndTimer <= 0.0) {
                SoundPlay.stopBackgroundMusic();
                showGameResult(lastPointWinner == 1 ? "玩家 1" : "玩家 2");
                deactivate();
                return;
            }
        }
        if (ballHitGroundTimer > 0.0) { // 羽毛球落地后到记分牌改变前等待时间
            ballHitGroundTimer -= GameProperties.frameTime;
            if (ballHitGroundTimer <= 0.0) {
                changeScore();
                ballHitGroundTimer = 0.0;
                scoreChangeTimer = 2.0;
                serveSide = lastPointWinner;
                // 主机广播权威发球方：客机 applyRemoteServe 直接采纳，
                // 不再依赖分数差推导，也纠正服务器快照里滞后的 serveSide
                if (isHost && netClient != null) {
                    netClient.sendLine("SERVE:" + serveSide);
                }
            }
        }
        else if (scoreChangeTimer > 0.0) { // 记分牌改变后到重置玩家和球的位置等待时间
            scoreChangeTimer -= GameProperties.frameTime;
            if (scoreChangeTimer <= 0.0) {
                if (serveSide == StickMan.sideLeft) {
                    StickMan stickmanRight = (StickMan) inRoom.getObject("stickman_right");
                    if (stickmanRight.isShotting)
                        scoreChangeTimer = 0.04;
                    else {
                        scoreChangeTimer = 0.0;
                        stickmanRight.setY(GameProperties.floorY - GameProperties.playerHeight - 11);
                        stickmanRight.isJumping = false;
                        stickmanRight.isShotting = false;
                        stickmanRight.isReadyingServe = true;
                        resetBall();
                    }
                }
                else {
                    StickMan stickmanLeft = (StickMan) inRoom.getObject("stickman_left");
                    if (stickmanLeft.isShotting)
                        scoreChangeTimer = 0.04;
                    else {
                        scoreChangeTimer = 0.0;
                        stickmanLeft.setY(GameProperties.floorY - GameProperties.playerHeight - 11);
                        stickmanLeft.isJumping = false;
                        stickmanLeft.isShotting = false;
                        stickmanLeft.isReadyingServe = true;
                        resetBall();
                    }
                }
            }
        }
        if (playBGMTimer <= 1.5) {
            playBGMTimer += GameProperties.frameTime;
            if (playBGMTimer > 1.5) {
                SoundPlay.setBackgroundMusic("ingame_bgm.mp3");
                SoundPlay.playBackgroundMusic();
            }
        }
    }
}

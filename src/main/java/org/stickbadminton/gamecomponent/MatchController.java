package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.UIObject;
import org.stickbadminton.gamecomponent.network.NetworkClient;


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


    // 添加 setter 方法
    public void setHost(boolean Host) {
        isHost = Host;
    }

    public void setNetClient(NetworkClient Client) {
        netClient = Client;
    }

    // 可选：添加 getter
    public static boolean isHost() {
        return isHost;
    }

    public static NetworkClient getNetClient() {
        return netClient;
    }

    public MatchController() {
        super("controller", new Image("stickman_head1.png"));
        setVisible(false);
    }

    public void applyBallState(double x, double y, double speedX, double speedY) {
        Badminton ball = (Badminton) inRoom.getObject("badminton");
        if (ball == null) return;

        double lerpFactor = 0.2;
        ball.setPositionWithCenter(
                ball.getCenterX() + (x - ball.getCenterX()) * lerpFactor,
                ball.getCenterY() + (y - ball.getCenterY()) * lerpFactor
        );
        ball.speedX = ball.speedX + (speedX - ball.speedX) * lerpFactor;
        ball.speedY = ball.speedY + (speedY - ball.speedY) * lerpFactor;

        // 新增: 重置预测
        ball.lastSpeedX = speedX;
        ball.lastSpeedY = speedY;
        ball.predictTimer = 0;

        ball.isNetworkControlled = true;
    }

    public void matchStart() {
        ballHitGroundTimer = 0.0;
        scoreChangeTimer = 0.01;
        playBGMTimer = 0.0;
    }
    public void onBallGroundHit(int winner) {
        ballHitGroundTimer = 1.0;
        lastPointWinner = winner;

        // 新增: 主机发送得分
        if (isHost) {
            UIObject scoreLeft = inRoom.getUiObject("score_left");
            UIObject scoreRight = inRoom.getUiObject("score_right");
            int leftScore = (scoreLeft instanceof UIDigitView) ? ((UIDigitView) scoreLeft).getCurrentNumber() : 0;
            int rightScore = (scoreRight instanceof UIDigitView) ? ((UIDigitView) scoreRight).getCurrentNumber() : 0;
            if (winner == -1) rightScore = (rightScore + 1) % 10;
            else leftScore = (leftScore + 1) % 10;
            netClient.sendLine(String.format("SCORE:%d:%d", leftScore, rightScore));
        }
    }
    public void changeScore() {
        SoundPlay.playSound("add_score.mp3", 1.0);
        if (lastPointWinner == -1) {
            UIObject scoreRight = inRoom.getUiObject("score_right");
            if (scoreRight instanceof UIDigitView) {
                UIDigitView scoreView = (UIDigitView) scoreRight;
                scoreView.setCurrentNumber((scoreView.getCurrentNumber() + 1) % 10);
            }
        } else {
            UIObject scoreLeft = inRoom.getUiObject("score_left");
            if (scoreLeft instanceof UIDigitView) {
                UIDigitView scoreView = (UIDigitView) scoreLeft;
                scoreView.setCurrentNumber((scoreView.getCurrentNumber() + 1) % 10);
            }
        }

        //判断获胜
        UIObject scoreLeft = inRoom.getUiObject("score_left");
        UIObject scoreRight = inRoom.getUiObject("score_right");

        if (scoreLeft instanceof UIDigitView && scoreRight instanceof UIDigitView) {
            UIDigitView leftView = (UIDigitView) scoreLeft;
            UIDigitView rightView = (UIDigitView) scoreRight;

            if (leftView.getCurrentNumber() >= 9 || rightView.getCurrentNumber() >= 9) {
                // 游戏结束，显示结果
                matchEndTimer = 1.5;
            }
        }
    }

    public void resetBall() {
        if (inRoom.getObject("badminton") != null)
            inRoom.removeObject(inRoom.getObject("badminton"));
        GameObject badminton = inRoom.addObject(new Badminton(serveSide));
        badminton.setPosition(450 - 200 * serveSide, 700);
        badminton.speedX = 0;
        badminton.speedY = 0;

        // 新增: 主机立即发送新状态
        if (isHost&& netClient != null) {
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

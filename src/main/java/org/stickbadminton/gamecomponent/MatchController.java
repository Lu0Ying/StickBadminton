package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.UIObject;


public class MatchController extends GameObject{
    private double ballHitGroundTimer = 0.0;
    private double scoreChangeTimer = 0.0;
    private int lastPointWinner = 0; // -1 -> 右侧, 1 -> 左侧
    private boolean isServeReadying = false; // 是否处于发球阶段
    public int serveSide = 1; // -1 -> 右侧发球, 1 -> 左侧发球
    public MatchController() {
        super("controller", new Image("stickman_head1.png"));
        setVisible(false);
    }
    public void matchStart() {
        ballHitGroundTimer = 0.0;
        scoreChangeTimer = 0.01;
    }
    public void onBallGroundHit(int winner) {
        ballHitGroundTimer = 1.0;
        lastPointWinner = winner;
    }
    public void changeScore() {
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
                showGameResult(leftView.getCurrentNumber() >= 9 ? "Player1" : "Player2");
                deactivate();
                return;
            }
        }
    }

    public void resetBall() {
        if (inRoom.getObject("badminton") != null)
            inRoom.removeObject(inRoom.getObject("badminton"));
        inRoom.addObject(new Badminton(serveSide)).setPosition(450 - 200 * serveSide, 700);
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
        if (ballHitGroundTimer > 0.0) { // 羽毛球落地后到记分牌改变前等待时间
            ballHitGroundTimer -= GameProperties.frameTime;
            if (ballHitGroundTimer < 0.0) {
                changeScore();
                ballHitGroundTimer = 0.0;
                scoreChangeTimer = 2.0;
                serveSide = lastPointWinner;
            }
        }
        else if (scoreChangeTimer > 0.0) { // 记分牌改变后到重置玩家和球的位置等待时间
            scoreChangeTimer -= GameProperties.frameTime;
            if (scoreChangeTimer < 0.0) {
                if (serveSide == StickMan.sideLeft) {
                    StickMan stickmanRight = (StickMan) inRoom.getObject("stickman_right");
                    if (stickmanRight.isShotting)
                        scoreChangeTimer = 0.04;
                    else {
                        scoreChangeTimer = 0.0;
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
                        stickmanLeft.isReadyingServe = true;
                        resetBall();
                    }
                }
            }
        }
        else if (isServeReadying) {
            if (serveSide == 1) { // 左侧人物发球

            }
            else { // 右侧人物发球

            }
        }
    }
}

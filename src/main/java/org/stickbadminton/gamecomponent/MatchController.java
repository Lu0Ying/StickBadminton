package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;


public class MatchController extends GameObject{
    private double ballHitGroundTimer = 0.0;
    private double scoreChangeTimer = 0.0;
    private int lastPointWinner = 0; // -1 -> 右侧, 1 -> 左侧
    private boolean isServeReadying = false; // 是否处于发球阶段
    public int serveSide = 0; // -1 -> 右侧发球, 1 -> 左侧发球
    public StickMan leftStickman, rightStickman;
    public Badminton badminton;
    public UIDigitView scoreLeft, scoreRight;
    public MatchController() {
        super("controller", new Image("stickman_head.png"));
        setVisible(false);
    }
    public void initInRoom() {
        if (inRoom == null) {
            System.err.println("MatchControl.initInRoom: inRoom is null");
            return;
        }
        leftStickman = (StickMan) inRoom.getObject("stickman_left");
        rightStickman = (StickMan) inRoom.getObject("stickman_right");
        // badminton = (Badminton) inRoom.getObject("badminton");
        scoreLeft = (UIDigitView) inRoom.getUiObject("score_left");
        scoreRight = (UIDigitView) inRoom.getUiObject("score_right");
    }
    public void onBallGroundHit() {

    }
    public void changeScore() {

    }
    public void resetPlayerAndBall() {

    }
    @Override
    public void onUpdate() {
        if (ballHitGroundTimer > 0.0) { // 羽毛球落地后到记分牌改变前等待时间
            ballHitGroundTimer = GameProperties.frameTime;
            if (ballHitGroundTimer < 0.0) {
                changeScore();
                ballHitGroundTimer = 0.0;
                scoreChangeTimer = 2.0;
            }
        }
        else if (scoreChangeTimer > 0.0) { // 记分牌改变后到重置玩家和球的位置等待时间
            scoreChangeTimer -= GameProperties.frameTime;
            if (scoreChangeTimer < 0.0) {
                resetPlayerAndBall();
                scoreChangeTimer = 0.0;
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

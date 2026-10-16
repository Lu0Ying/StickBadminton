package org.stickbadminton.ui;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.logic.MatchController;
import org.stickbadminton.room.RoomGameplay;
import org.stickbadminton.room.RoomStickmanSelect;
import org.stickbadminton.room.RoomTitle;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;
import org.stickbadminton.UIObject;

public class UIGameOver extends UIObject {
    // 最终难度（5 级）人机挑战通关后的文案
    private static final String FINAL_WIN_TEXT = "恭喜挑战成功！";
    private static final int MAX_DIFFICULTY = 5;

    private VBox container;

    public UIGameOver(String winner) {
        container = new VBox(20);
        container.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-padding: 30px; -fx-alignment: center;");
        container.setLayoutX(30);

        // 单人（人机）模式：胜方为玩家 1，其余为电脑
        boolean singleMode = GameProperties.matchMode == 1;
        boolean playerWon = winner.equals("玩家 1");
        boolean finalLevel = GameProperties.difficultyLevel >= MAX_DIFFICULTY;
        boolean canNextChallenge = singleMode && playerWon && !finalLevel
                && MatchController.getNetClient() == null;

        Label winnerLabel;
        if (singleMode && finalLevel && playerWon) {
            // 通关最终难度：专用祝贺文案
            winnerLabel = new Label(FINAL_WIN_TEXT);
            SoundPlay.playSound("cheer.mp3", 0.5);
        } else if (singleMode) {
            // 人机模式：胜方为玩家时才出欢呼音，否则叹气
            winnerLabel = new Label(playerWon ? "玩家 获胜!" : "电脑 获胜!");
            SoundPlay.playSound(playerWon ? "cheer.mp3" : "sigh.mp3", playerWon ? 0.5 : 0.3);
        } else {
            winnerLabel = new Label(winner + " 获胜!");
            SoundPlay.playSound("cheer.mp3", 0.5);
        }
        winnerLabel.setFont(Font.font("Arial", 36));
        winnerLabel.setTextFill(Color.WHITE);

        Button restartButton = newButton("重新开始", e -> onRestart());

        // 继续挑战：升一档难度重开一局（仅单人获胜且未通关时出现）
        Button nextChallengeButton = newButton("继续挑战", e -> onNextChallenge());

        // 更换角色：回选人界面（仅单人模式，联机对局角色由服务端分配）
        Button changeCharacterButton = newButton("更换角色", e -> onChangeCharacter());

        Button mainMenuButton = newButton("返回主菜单", e -> onMainMenu());

        container.getChildren().add(winnerLabel);
        container.getChildren().add(restartButton);
        if (canNextChallenge) container.getChildren().add(nextChallengeButton);
        if (singleMode) container.getChildren().add(changeCharacterButton);
        container.getChildren().add(mainMenuButton);
    }

    // 统一按钮样式，避免每个按钮重复写一长串 style
    private Button newButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        button.setOnAction(handler);
        return button;
    }

    private void onRestart() {
        // 联机对局不可原房间重开（网络会话已绑定旧房间），改走主菜单重新匹配
        if (MatchController.getNetClient() != null) {
            onMainMenu();
            return;
        }
        // 重新开始当前房间
        switchToRoom(new RoomGameplay());
    }

    // 继续挑战：难度与 AI 外观同步升一档（选人界面里两者同号），再开一局
    private void onNextChallenge() {
        int nextLevel = Math.min(GameProperties.difficultyLevel + 1, MAX_DIFFICULTY);
        GameProperties.difficultyLevel = nextLevel;
        GameProperties.characterType2 = nextLevel;
        switchToRoom(new RoomGameplay());
    }

    private void onChangeCharacter() {
        // 返回选人界面；网络会话（若有）先断开，避免残留席位
        MatchController.endSession();
        switchToRoom(new RoomStickmanSelect());
    }

    private void onMainMenu() {
        // 返回主菜单
        switchToRoom(new RoomTitle());
    }

    private void switchToRoom(Room target) {
        if (getParentRoom() != null) {
            new SwitchRoomEffect(getParentRoom(), target);
        }
    }

    @Override
    public javafx.scene.Node getUINode() {
        return container;
    }
}

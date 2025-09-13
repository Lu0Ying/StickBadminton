package org.stickbadminton.gamecomponent;

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
    private VBox container;
    private Label winnerLabel;
    private Button mainMenuButton;
    private Button restartButton;

    public UIGameOver(String winner) {
        container = new VBox(20);
        container.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-padding: 30px; -fx-alignment: center;");

        if (GameProperties.matchMode == 1) {
            if (winner.equals("玩家 2")) {
                winnerLabel = new Label("电脑 获胜!");
                SoundPlay.playSound("sigh.mp3", 0.3);
            } else {
                winnerLabel = new Label("玩家 获胜!");
                SoundPlay.playSound("cheer.mp3", 0.5);
            }
        }
        else {
            winnerLabel = new Label(winner + " 获胜!");
            SoundPlay.playSound("cheer.mp3", 0.5);
        }
        winnerLabel.setFont(Font.font("Arial", 36));
        winnerLabel.setTextFill(Color.WHITE);

        restartButton = new Button("重新开始");
        restartButton.setStyle("-fx-font-size: 18px; -fx-padding: 10px 20px;");
        restartButton.setOnAction(e -> onRestart());

        mainMenuButton = new Button("返回主菜单");
        mainMenuButton.setStyle("-fx-font-size: 18px; -fx-padding: 10px 20px;");
        mainMenuButton.setOnAction(e -> onMainMenu());

        container.getChildren().addAll(winnerLabel, restartButton, mainMenuButton);
    }

    private void onRestart() {
        // 重新开始当前房间
        if (getParentRoom() != null) {
            Room currentRoom = getParentRoom();
            RoomGameplay newRoom = new RoomGameplay();
            new SwitchRoomEffect(currentRoom, newRoom);
        }
    }

    private void onMainMenu() {
        // 返回主菜单
        if (getParentRoom() != null) {
            Room currentRoom = getParentRoom();
            RoomTitle roomTitle = new RoomTitle();
            new SwitchRoomEffect(currentRoom, roomTitle);
        }
    }

    @Override
    public javafx.scene.Node getUINode() {
        return container;
    }
}
package org.stickbadminton;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.particle.ParticleSystem;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import com.almasb.fxgl.particle.ParticleEmitters;
import com.almasb.fxgl.particle.ParticleEmitter;
import com.almasb.fxgl.particle.ParticleComponent;

import javafx.geometry.Pos;
import javafx.scene.layout.Pane;
import javafx.scene.Node;
import org.stickbadminton.logic.MatchController;
import org.stickbadminton.room.RoomGameplay;
import org.stickbadminton.room.RoomStickmanSelect;
import org.stickbadminton.room.RoomTitle;

public class MyGameMenu extends FXGLMenu {

    public MyGameMenu(MenuType type) {
        super(type);

        Rectangle bg = new Rectangle(FXGL.getAppWidth(), FXGL.getAppHeight(), Color.color(0, 0, 0, 0.7));
        getContentRoot().getChildren().add(bg);

        // ==== 标题 ====
        Text title = FXGL.getUIFactoryService().newText("Game Paused", Color.WHITE, 48.0);
        title.setStyle("-fx-font-family: 'Consolas'; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 32px; ");

        DropShadow glow = new DropShadow();
        glow.setColor(Color.rgb(255, 215, 0, 0.6));
        glow.setRadius(30);
        glow.setSpread(0.4);
        title.setEffect(glow);

        // ==== 菜单按钮 ====
        Button resumeButton = new Button("继续游戏");
        resumeButton.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        resumeButton.setOnAction(e -> fireResume());

        Button exitButton = new Button("退出");
        exitButton.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        exitButton.setOnAction(e -> fireExit());

        Button backButton = new Button("返回主菜单");
        backButton.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        backButton.setOnAction(e -> {
            FXGL.getGameController().startNewGame();
        });

        // 中途重开：仅对战中有效（菜单是全局单例，标题界面按 ESC 也会出现）
        Button restartButton = new Button("重新开始");
        restartButton.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        restartButton.setOnAction(e -> {
            if (!(Room.getCurrent() instanceof RoomGameplay)) return;
            switchRoom(MatchController.getNetClient() != null ? new RoomTitle() : new RoomGameplay());
        });

        // 更换角色：返回选人界面；联机会话先断开，避免残留席位
        Button changeCharacterButton = new Button("更换角色");
        changeCharacterButton.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        changeCharacterButton.setOnAction(e -> {
            if (!(Room.getCurrent() instanceof RoomGameplay)) return;
            MatchController.endSession();
            switchRoom(new RoomStickmanSelect());
        });

        VBox box = new VBox(30, title, resumeButton, restartButton, changeCharacterButton, backButton, exitButton);
        box.setAlignment(Pos.CENTER);
        box.setTranslateX(FXGL.getAppWidth() / 2.0 - 150);
        box.setTranslateY(FXGL.getAppHeight() / 2.0 - 210);

        getContentRoot().getChildren().add(box);
        getInput().addAction(new UserAction("Exit Menu") {
            @Override
            protected void onActionBegin() {
                SoundPlay.playBackgroundMusic();
            }
        }, KeyCode.ESCAPE);
    }

    // 关闭本菜单并切房间：菜单打开期间引擎是暂停的，必须先恢复否则切换动画的组件不推进
    private void switchRoom(Room target) {
        Room from = Room.getCurrent();
        fireResume(); // 关闭 SubScene
        FXGL.getGameController().resumeEngine(); // 兜底：确保引擎恢复跑帧
        SoundPlay.playBackgroundMusic(); // 与 ESC 退出菜单的口径一致，恢复被菜单停掉的 BGM
        if (from != null) {
            new SwitchRoomEffect(from, target);
        }
    }

    @Override
    public void onUpdate(double tpf) {
        SoundPlay.stopBackgroundMusic();
    }
}

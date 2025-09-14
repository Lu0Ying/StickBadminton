package org.stickbadminton;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.particle.ParticleSystem;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
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
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.layout.Pane;
import javafx.scene.Node;
import org.stickbadminton.gamecomponent.RoomTitle;

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
        resumeButton.setOnAction(e -> {SoundPlay.playBackgroundMusic(); fireResume();});

        Button exitButton = new Button("退出");
        exitButton.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        exitButton.setOnAction(e -> fireExit());

        Button backButton = new Button("返回主菜单");
        backButton.setStyle("-fx-font-family: '黑体'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
        backButton.setOnAction(e -> {
            SoundPlay.playBackgroundMusic(); fireResume();
            FXGL.getGameWorld().getEntities().clear();
            FXGL.getGameScene().clearUINodes();
            RoomTitle roomTitle = new RoomTitle();
            roomTitle.enter();
        });

        VBox box = new VBox(30, title, resumeButton, backButton, exitButton);
        box.setAlignment(Pos.CENTER);
        box.setTranslateX(FXGL.getAppWidth() / 2.0 - 150);
        box.setTranslateY(FXGL.getAppHeight() / 2.0 - 150);

        getContentRoot().getChildren().add(box);
    }

    @Override
    public void onUpdate(double tpf) {
        SoundPlay.stopBackgroundMusic();
    }
}

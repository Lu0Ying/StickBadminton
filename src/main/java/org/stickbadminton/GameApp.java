package org.stickbadminton;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;
import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import org.stickbadminton.room.RoomGameplay;
import org.stickbadminton.room.RoomTitle;

public class GameApp extends GameApplication {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(900);
        settings.setHeight(600);
        settings.setManualResizeEnabled(true); // 可拉伸窗口大小
        settings.setScaleAffectedOnResize(true);
        settings.setPreserveResizeRatio(true);
        settings.setTitle("火柴人打羽毛球");
        settings.setVersion("v1.2");
        settings.setTicksPerSecond(60);
        settings.setPauseMusicWhenMinimized(true);
        settings.setMenuKey(KeyCode.ESCAPE);
        settings.setSceneFactory(new SceneFactory() {
            @Override
            public FXGLMenu newGameMenu() {
                return new MyGameMenu(MenuType.GAME_MENU);
            }
        });
    }

    @Override
    protected void initGame() {
        // 运行时窗口/任务栏图标：jpackage --icon 只设 exe 文件图标，运行中的窗口要单独设置。
        // initGame 在 FXGL 后台线程执行，Stage 图标必须回到 FX 应用线程修改
        Platform.runLater(() -> FXGL.getPrimaryStage().getIcons().add(Sprite.loadImage("/images/logo.png")));
        SoundPlay.initSoundPool();
        RoomTitle roomTitle = new RoomTitle();
        roomTitle.enter();
        KeyInput.initInput();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

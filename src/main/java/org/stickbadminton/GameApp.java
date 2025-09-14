package org.stickbadminton;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;
import javafx.scene.input.KeyCode;
import org.stickbadminton.gamecomponent.RoomGameplay;
import org.stickbadminton.gamecomponent.RoomTitle;

public class GameApp extends GameApplication {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(900);
        settings.setHeight(600);
        settings.setManualResizeEnabled(true); // 可拉伸窗口大小
        settings.setScaleAffectedOnResize(true);
        settings.setPreserveResizeRatio(true);
        settings.setTitle("火柴人打羽毛球");
        settings.setVersion("v0.1");
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
        SoundPlay.initSoundPool();
        RoomTitle roomTitle = new RoomTitle();
        roomTitle.enter();
        KeyInput.initInput();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

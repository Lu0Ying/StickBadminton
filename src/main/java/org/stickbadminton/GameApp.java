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
        settings.setMenuKey(KeyCode.P);
        settings.setSceneFactory(new SceneFactory() {
            @Override
            public FXGLMenu newGameMenu() {
                return new MyGameMenu(MenuType.GAME_MENU);
            }
        });
    }

    // 字段保存，方便退出时 stop()
    //private org.stickbadminton.gamecomponent.network.NetworkClient netClient;
    @Override
    protected void initGame() {
        SoundPlay.initSoundPool();
        RoomTitle roomTitle = new RoomTitle();
        roomTitle.enter();
        KeyInput.initInput();
        // KeyInput.initInput(); 之后
//        org.stickbadminton.gamecomponent.network.NetworkClient client =
//                new org.stickbadminton.gamecomponent.network.NetworkClient("127.0.0.1", 9000);
//        client.start();
//// 绑定身份（可选，抢占 p1 / p2）
//        client.sendLine("HELLO:p1"); // 第二个客户端可用 HELLO:p2
    }

    public static void main(String[] args) {
        launch(args);
    }
}

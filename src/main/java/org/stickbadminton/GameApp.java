package org.stickbadminton;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.GameScene;
import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.stickbadminton.gamecomponent.Room1;

import static com.almasb.fxgl.dsl.FXGLForKtKt.getGameScene;

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
    }

    // 字段保存，方便退出时 stop()
    private org.stickbadminton.gamecomponent.network.NetworkClient netClient;
    @Override
    protected void initGame() {
        SoundPlay.initSoundPool();
        Room1 room1 = new Room1();
        room1.enter();
        KeyInput.initInput();
        // KeyInput.initInput(); 之后
        org.stickbadminton.gamecomponent.network.NetworkClient client =
                new org.stickbadminton.gamecomponent.network.NetworkClient("127.0.0.1", 9000);
        client.start();
// 绑定身份（可选，抢占 p1 / p2）
        client.sendLine("HELLO:p1"); // 第二个客户端可用 HELLO:p2
    }

    public static void main(String[] args) {
        launch(args);
    }
}

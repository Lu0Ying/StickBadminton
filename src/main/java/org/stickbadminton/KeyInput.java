package org.stickbadminton;

import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.security.Key;
import java.util.HashSet;
import java.util.Set;

public class KeyInput {
    public static Set<KeyCode> keys = new HashSet<KeyCode>();

    public static void initInput() {
        FXGL.getPrimaryStage().getScene().addEventFilter(KeyEvent.KEY_PRESSED,e -> {
            keys.add(e.getCode());
        });

        FXGL.getPrimaryStage().getScene().addEventFilter(KeyEvent.KEY_RELEASED,e -> {
            keys.remove(e.getCode());
        });
    }
}

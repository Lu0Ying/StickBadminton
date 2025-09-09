package org.stickbadminton;

import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.input.KeyCode;

import java.util.HashSet;
import java.util.Set;

public class KeyInput {
    public static Set<KeyCode> keys = new HashSet<KeyCode>();

    public static void initInput() {
        FXGL.getPrimaryStage().getScene().getRoot().getScene().setOnKeyPressed(e -> {
            keys.add(e.getCode());
        });

        FXGL.getPrimaryStage().getScene().getRoot().getScene().setOnKeyReleased(e -> {
            keys.remove(e.getCode());
        });
    }
}

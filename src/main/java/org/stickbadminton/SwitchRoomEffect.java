package org.stickbadminton;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.Texture;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.scene.Scene;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.gamecomponent.GameProperties;

import java.awt.*;

import static java.awt.Color.*;

public class SwitchRoomEffect {
    public Room roomStart;
    public Room roomTo;
    Entity entity;


    public SwitchRoomEffect(Room roomStart, Room roomTo) {
        Rectangle blackScreen = new Rectangle(0, 0, FXGL.getAppWidth(), FXGL.getAppHeight());
        blackScreen.setOpacity(0.0);
        blackScreen.setViewOrder(-1.0);
        FXGL.getGameScene().addUINode(blackScreen);

        // 淡入(0.3s) -> 停留(0.2s) -> 淡出(0.3s)
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(0.3), blackScreen);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition stay = new PauseTransition(Duration.seconds(0.2));

        FadeTransition fadeOut = new FadeTransition(Duration.seconds(0.3), blackScreen);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        SequentialTransition seq = new SequentialTransition(fadeIn, stay, fadeOut);
        seq.setOnFinished(e -> {
            FXGL.getGameScene().removeUINode(blackScreen);
        });
        seq.play();

        this.roomStart = roomStart;
        this.roomTo = roomTo;
        entity = FXGL.entityBuilder().at(0,0).
                view(new Rectangle(0, 0, GameProperties.roomWidth, GameProperties.roomHeight))
                .buildAndAttach();
        entity.addComponent(new RoomSwitchControl(this));
        entity.setOpacity(0.0);
    }
}

class RoomSwitchControl extends Component {
    private SwitchRoomEffect target;
    private double timer = 0.0;
    public RoomSwitchControl(SwitchRoomEffect target) {
        this.target = target;
    }

    @Override
    public void onUpdate(double tpf) {
        timer += tpf;
        if (timer <= 0.5) {
            // do nothing
        }
        else {
            target.roomStart.leave();
            target.roomTo.enter();
            getEntity().removeFromWorld();
        }
    }
}
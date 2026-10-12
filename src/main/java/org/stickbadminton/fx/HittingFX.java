package org.stickbadminton.fx;
import org.stickbadminton.Sprite;
import org.stickbadminton.logic.GameProperties;

import javafx.scene.image.Image;
import javafx.util.Duration;
import org.stickbadminton.GameObject;

public class HittingFX extends GameObject {
    private double liveTimer;
    public HittingFX() {
        super("hittingFX",Sprite.loadImage("/images/game/hit_FX.png"), 387, Duration.seconds(0.5));
        entity.setScaleX(0.3);
        entity.setScaleY(0.3);
        setCenterPosition(193.5, 115);
        liveTimer = 0.5;
        x = 0.0;
        y = 0.0;
    }
    @Override
    public void onUpdate() {
        liveTimer -= GameProperties.frameTime;
        if (liveTimer <= 0) {
            deactivate();
        }
    }
}

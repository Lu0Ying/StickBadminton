package org.stickbadminton.ui;
import org.stickbadminton.logic.GameProperties;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Sprite;

public class HintKeyboard extends GameObject{
    private double liveTimer = 0;
    public HintKeyboard() {
        super("hint", Sprite.loadImage(GameProperties.matchMode == 1 ? "/images/hud/ingame_hint_singleplayer.png" : "/images/hud/ingame_hint.png"));
    }
    @Override
    public void onUpdate() {
        liveTimer += GameProperties.frameTime;
        if (liveTimer > 3) {
            if (liveTimer > 4) {
                setOpacity(0.0);
                deactivate();
            }
            else
                setOpacity(1.0 - (liveTimer - 3) * 1.0);
        }
    }
}

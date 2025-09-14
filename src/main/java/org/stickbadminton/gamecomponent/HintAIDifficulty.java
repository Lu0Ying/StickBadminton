package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;

public class HintAIDifficulty extends GameObject{
    public boolean isOpened = false;
    private double openTimer = 0.0;
    private final double transitionTime = 0.1;
    public HintAIDifficulty() {
        super("hintAIDifficulty", new Image("stickmanselect_difficultyhint.png"));
        setOpacity(0.0);
    }
    @Override
    public void onUpdate() {
        if (isOpened) {
            if (openTimer < transitionTime) {
                setOpacity(openTimer / transitionTime);
                openTimer += GameProperties.frameTime;
            }
            else {
                openTimer = transitionTime;
                setOpacity(1.0);
            }
        }
        else {
            if (openTimer > transitionTime)
                openTimer -= GameProperties.frameTime;
            else if (openTimer > 0.0) {
                openTimer -= GameProperties.frameTime;
                setOpacity(openTimer / transitionTime);
            }
            else {
                openTimer = 0.0;
                setOpacity(0.0);
            }
        }
    }
}

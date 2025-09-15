package org.stickbadminton.gamecomponent;

import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitter;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;
import org.stickbadminton.*;

public class InfiniteEnergySettingDisplay extends GameObject {
    private GameObject offDisplay;
    // this : onDisplay
    InfiniteEnergySettingDisplay() {
        super("infinite_energy_on", new Image("infinite_energy_on.png"));
        offDisplay = new GameObject("infinite_energy_off", new Image("infinite_energy_off.png"));
        this.setOpacity(0.0);
        this.entity.setScaleX(0.7);
        this.entity.setScaleY(0.7);
        offDisplay.getEntity().setScaleX(0.7);
        offDisplay.getEntity().setScaleY(0.7);
    }
    @Override
    public void activate() {
        super.activate();
        offDisplay.activate();
    }
    @Override
    public void deactivate() {
        offDisplay.deactivate();
        super.deactivate();
    }
    @Override
    public void setPosition(double x, double y) {
        super.setPosition(x, y);
        offDisplay.setPosition(x,y);
    }

    @Override
    public void onUpdate() {
        if (GameProperties.infiniteEnergyMode) {
            this.setOpacity(1.0);
            offDisplay.setOpacity(0.0);
        }
        else {
            this.setOpacity(0.0);
            offDisplay.setOpacity(1.0);
        }
    }
}

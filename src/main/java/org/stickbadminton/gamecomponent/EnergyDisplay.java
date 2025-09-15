package org.stickbadminton.gamecomponent;

import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitter;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import org.stickbadminton.GameObject;
import org.stickbadminton.*;

import java.util.List;

public class EnergyDisplay extends GameObject{
    private GameObject border;
    private double displayedEnergy = 100.0;
    private int side;
    public EnergyDisplay(int side) {
        super("energydisplay_" + side, new Image("energydisplay_fill.png"));
        border = new GameObject("energydisplay_" + side + "_border", new Image("energydisplay_border.png"));
        this.side = side;
        if (side == 1) {
            setCenterPosition(0, 0);
        }
        else {
            setCenterPosition(194, 0);
        }
    }

    @Override
    public void onUpdate() {
        double targetEnergy;
        if (side == 1) {
            GameObject stickmanObj = inRoom.getObject("stickman_left");
            if (stickmanObj == null)
                return;
            targetEnergy = ((StickMan)stickmanObj).energyRemain;
        }
        else {
            GameObject stickmanObj = inRoom.getObject("stickman_right");
            if (stickmanObj == null)
                return;
            targetEnergy = ((StickMan)stickmanObj).energyRemain;
        }
        displayedEnergy += (targetEnergy - displayedEnergy) * 0.1;
        entity.setScaleX(displayedEnergy / 100.0);
        // 改变色相
        ColorAdjust colorAdjust = new ColorAdjust();
        colorAdjust.setBrightness(-(1.0 - entity.getScaleX()));
        entity.getViewComponent().getChildren().getFirst().setEffect(colorAdjust);
    }

    @Override
    public void setPosition(double x, double y) {
        super.setPosition(x, y);
        border.setPosition(x - 3, y);
    }

    @Override
    public void activate() {
        super.activate();
        border.activate();
    }
    @Override
    public void deactivate() {
        super.deactivate();
        border.deactivate();
    }
}

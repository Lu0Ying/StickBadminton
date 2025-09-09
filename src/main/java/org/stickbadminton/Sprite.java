package org.stickbadminton;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.entity.components.ViewComponent;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class Sprite {
    private Entity parent;
    private AnimatedTexture texture;

    public AnimatedTexture getTexture() { return texture; }

    public Sprite(Entity parent) {
        this.parent = parent;
        texture = null;
    }
    public Sprite(Entity parent, AnimatedTexture texture) {
        this.parent = parent;
        this.texture = texture;
    }
    public Sprite(Entity parent, Image image) {
        this.parent = parent;
        AnimationChannel anim = new AnimationChannel(image, 1, (int)image.getWidth(), (int)image.getHeight(),
                Duration.INDEFINITE, 0, 0);
        AnimatedTexture animatedTexture = new AnimatedTexture(anim);
        this.texture = animatedTexture;
    }
    public Sprite(Entity parent, Image image, int subImageWidth, Duration duration) {
        this.parent = parent;
        int imageNum = (int)image.getWidth()/subImageWidth;
        AnimationChannel anim = new AnimationChannel(image, imageNum, subImageWidth, (int)image.getHeight(),
                duration, 0, imageNum - 1);
        AnimatedTexture animatedTexture = new AnimatedTexture(anim);
        this.texture = animatedTexture;
    }

    void activate() {
        parent.getViewComponent().clearChildren();
        parent.getViewComponent().addChild(texture);
    }
    void playWithoutLoop() {
        texture.play();
    }
    void play() {
        texture.loop();
    }
    void stop() {
        texture.stop();
    }
}

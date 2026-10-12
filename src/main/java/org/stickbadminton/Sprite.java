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

    /**
     * 统一的图片资源加载入口：先由本模块的类把 classpath 路径解析成 URL，再交给 JavaFX。
     * 不能直接 new Image("相对路径")——模块化运行时 JavaFX 通过 ClassLoader 跨模块
     * 查资源会返回 null，必须在本模块内先完成 URL 解析（与 SoundPlay 的音频加载同理）。
     *
     * @param resourcePath 以 / 开头的 classpath 路径，如 "/images/game/badminton.png"
     */
    public static Image loadImage(String resourcePath) {
        var url = Sprite.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("资源未找到: " + resourcePath);
        }
        return new Image(url.toExternalForm());
    }

    public void activate() {
        parent.getViewComponent().clearChildren();
        parent.getViewComponent().addChild(texture);
    }
    public void playWithoutLoop() {
        texture.play();
    }
    public void play() {
        texture.loop();
    }
    public void stop() { texture.stop(); }
}

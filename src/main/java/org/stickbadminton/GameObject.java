package org.stickbadminton;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.Set;

public class GameObject {

    protected String name;
    protected Room inRoom;

    protected Sprite sprite;
    protected Entity entity;
    protected double x;
    protected double y;
    protected double rotation;
    protected double centerX = 0.0;
    protected double centerY = 0.0;
    public double speedX;
    public double speedY;
    public double speedRotation;

    String getName() {
        return name;
    }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getRotation() { return rotation; }
    public void setX(double x) { this.x = x; entity.setX(x); }
    public void setY(double y) { this.y = y; entity.setY(y); }
    public void setPosition(double x, double y) {setX(x);setY(y);}
    public void setCenterPosition(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
        entity.getTransformComponent().setRotationOrigin(new Point2D(centerX, centerY));
    }
    public void setRotation(double rotation) { this.rotation = rotation; entity.setRotation(rotation); }

    public GameObject(String name, AnimatedTexture texture) {
        this.name = name;
        entity = new Entity();
        this.sprite = new Sprite(entity, texture);
        this.sprite.play();
        entity.getViewComponent().addChild(sprite.getTexture());
        entity.addComponent(new ObjectControl(this));
        x = 0.0;
        y = 0.0;
        rotation = 0.0;
    }

    public GameObject(String name, Image image) {
        this.name = name;
        entity = new Entity();
        this.sprite = new Sprite(entity, image);
        this.sprite.play();
        entity.getViewComponent().addChild(sprite.getTexture());
        entity.addComponent(new ObjectControl(this));
        x = 0.0;
        y = 0.0;
        rotation = 0.0;
    }

    public GameObject(String name, Image image, int subImageWidth, Duration duration) {
        this.name = name;
        entity = new Entity();
        this.sprite = new Sprite(entity, image, subImageWidth, duration);
        this.sprite.play();
        entity.getViewComponent().addChild(sprite.getTexture());
        entity.addComponent(new ObjectControl(this));
        x = 0.0;
        y = 0.0;
        rotation = 0.0;
    }

    public void onUpdate() {
    }

    public void activate() {
        FXGL.getGameWorld().addEntity(entity);
    }

    public void deactivate() {
        FXGL.getGameWorld().removeEntity(entity);
    }
}

class ObjectControl extends Component {
    private GameObject target;
    public ObjectControl(GameObject target) {
        this.target = target;
    }
    public final double FRAME_TIME = 1 / 60.0;

    @Override
    public void onUpdate(double tpf) {
        //System.out.println("onUpdate called, tpf=" + tpf);
        target.onUpdate();
        target.x += target.speedX * FRAME_TIME;
        target.y += target.speedY * FRAME_TIME;
        target.rotation += target.speedRotation * FRAME_TIME;
        target.entity.setX(target.x);
        target.entity.setY(target.y);
        target.entity.setRotation(target.rotation);
    }
}
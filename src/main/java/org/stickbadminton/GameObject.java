package org.stickbadminton;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.util.Duration;
import org.stickbadminton.logic.GameProperties;

public class GameObject {

    protected String name;
    protected Room inRoom;

    protected Sprite sprite;
    protected Entity entity;
    protected boolean isActive = false;
    protected double x;
    protected double y;
    protected double rotation;
    protected double spriteCenterX = 0.0;
    protected double spriteCenterY = 0.0;
    public double speedX;
    public double speedY;
    public double speedRotation;

    String getName() {
        return name;
    }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getCenterX() { return x + spriteCenterX; }
    public double getCenterY() { return y + spriteCenterY; }
    public double getRotation() { return rotation; }
    public Sprite getSprite() { return sprite; }
    public Entity getEntity() { return entity; }
    public void setName(String name) { this.name = name; }
    public void setX(double x) { this.x = x; entity.setX(x); }
    public void setY(double y) { this.y = y; entity.setY(y); }
    public void setPosition(double x, double y) {setX(x);setY(y);}
    public void setPositionWithCenter(double x, double y) {setX(x - spriteCenterX);setY(y - spriteCenterY);}
    public void setCenterPosition(double spriteCenterX, double spriteCenterY) {
        this.spriteCenterX = spriteCenterX;
        this.spriteCenterY = spriteCenterY;
        entity.getTransformComponent().setRotationOrigin(new Point2D(spriteCenterX, spriteCenterY));
        entity.getTransformComponent().setScaleOrigin(new Point2D(spriteCenterX, spriteCenterY));
    }
    public void setRotation(double rotation) { this.rotation = rotation; entity.setRotation(rotation); }
    public void setPaintIndex(int index) { entity.getViewComponent().setZIndex(index); }
    public void setOpacity(double opacity) { entity.getViewComponent().setOpacity(opacity); }
    public void setVisible(boolean visible) { entity.getViewComponent().setVisible(visible); }

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
        isActive = true;
    }

    public void deactivate() {
        FXGL.getGameWorld().removeEntity(entity);
        isActive = false;
        if (inRoom != null && inRoom.isClearing() == false)
            inRoom.removeObjectFromList(this);
    }
}

class ObjectControl extends Component {
    private GameObject target;

    // 固定逻辑帧累加器：逻辑步长恒为 GameProperties.frameTime(1/60)，
    // 与渲染帧率解耦——掉帧补步、高刷屏不加速，双端状态序列由步数决定而与本机帧率无关
    private double tickAccumulator = 0;

    // 单渲染帧最多补的逻辑步数：防止断点/窗口拖动等长暂停后螺旋追帧
    private static final int MAX_STEPS_PER_FRAME = 5;

    public ObjectControl(GameObject target) {
        this.target = target;
    }

    @Override
    public void onUpdate(double tpf) {
        tickAccumulator += tpf;
        int steps = 0;
        while (tickAccumulator >= GameProperties.frameTime && steps < MAX_STEPS_PER_FRAME) {
            target.onUpdate();
            target.x += target.speedX * GameProperties.frameTime;
            target.y += target.speedY * GameProperties.frameTime;
            target.rotation += target.speedRotation * GameProperties.frameTime;
            tickAccumulator -= GameProperties.frameTime;
            steps++;
        }
        // 超额时间过多说明经历长暂停：丢弃余量，避免恢复后持续补帧
        if (tickAccumulator > GameProperties.frameTime * MAX_STEPS_PER_FRAME) {
            tickAccumulator = 0;
        }
        // 渲染同步：每渲染帧把逻辑位置写给 entity（120Hz 下视觉每两帧更新一次，可接受）
        target.entity.setX(target.x);
        target.entity.setY(target.y);
        target.entity.setRotation(target.rotation);
    }
}
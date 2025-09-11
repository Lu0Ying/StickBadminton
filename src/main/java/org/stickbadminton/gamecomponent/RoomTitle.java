package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;

public class RoomTitle extends Room{
    public RoomTitle() {
        // 背景: title_background.png
        // 开始按钮: button_titlestart.png
        // 双人游戏按钮：button_2player.png
        // 重要：现在按钮 UI 物体已被封装进入 UIImageButton 类中，用这个类来做按钮，具体用法见 Room1 中的 “ui 按钮” (19行起)
    }
}

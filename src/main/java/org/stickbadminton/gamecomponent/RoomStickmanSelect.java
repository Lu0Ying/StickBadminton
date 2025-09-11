package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;

public class RoomStickmanSelect extends Room{
    public RoomStickmanSelect(){
        // 选人界面
        // 背景、按钮等素材已存在resources文件夹中
        // 由背景和5个选人按钮和start按钮和undo按钮构成
        // 背景: stickmanselect_background.png
        // 按钮: selectbutton_1.png 等
        // start按钮(放屏幕中下位置): button_start.png
        // undo按钮(放start按钮上面，和start按钮居中对齐):button_undo.png
        // 按钮物件用写好的 UIImageButton 类，具体用法见 Room1
        // 先别管选好的人怎么显示，两边用 UIDigitView 代替，显示数字编号 1 到 5
    }
}

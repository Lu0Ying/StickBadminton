package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;

public class Room1 extends Room {
    public Room1() {
        //背景
        addObject(new GameObject("background", new Image("ingame_background.png")));
        //测试用物体，以后版本移除
        //addObject(new Box());
        //火柴人(未完成)
        addObject(new StickMan(-1)).setPosition(700, 300);
        addObject(new StickMan(1)).setPosition(200, 300);
        //羽毛球(未完成)
        addObject(new Badminton()).setPosition(100, 100);
        //ui按钮
        UIImageButton button1 = new UIImageButton("button_titlestart.png");
        button1.setOnAction(e -> {
            Room2 room2 = new Room2();
            this.leave();
            room2.enter();
        });
        addUiObject(button1, 300, 400);
        //测试黄色按钮

        //显示数字，电子显像管风格

        UIDigitView digitView = new UIDigitView(0);
        addUiObject(digitView, 390, 21).setName("score_left");
        UIDigitView digitView2 = new UIDigitView(0);
        addUiObject(digitView2, 484, 21).setName("score_right");
        /*
         *（测试用）
         */
        //添加点击加一的按钮
        Button incrementButton = new Button("数字+1");
        incrementButton.setOnAction(e -> digitView.setCurrentNumber((digitView.getCurrentNumber() + 1)%10));
        addUiNode(incrementButton, 300, 80);
        //添加重置按钮
        Button reloadButton = new Button("重置");
        reloadButton.setOnAction(e -> digitView.setCurrentNumber(0));
        addUiNode(reloadButton, 300, 100);

        //添加触网
        NetAnimation net = new NetAnimation();
        addObject(net, "net");


    }
}

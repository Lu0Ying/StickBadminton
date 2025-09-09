package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import javafx.scene.image.ImageView;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;

public class Room1 extends Room {
    public Room1() {
        //背景
        addObject(new GameObject("background", new Image("ingame_background.png")));
        //测试用物体，以后版本移除
        //addObject(new Box());
        //火柴人(未完成)
        addObject(new StickMan()).setPosition(700,300);
        //羽毛球(未完成)
        addObject(new Badminton()).setPosition(100, 100);

        //ui按钮，需css改样式
        Button button1 = new Button("下一房间");
        button1.setOnAction(e -> {Room2 room2 = new Room2(); this.leave(); room2.enter();});
        addUiNode(button1, 300, 400);
        //测试黄色按钮
        setupPlayButton(button1);
    }
    private void setupPlayButton(Button button) {
        //常态图片样式
        Image imgNormal = new Image(Objects.requireNonNull(getClass().getResource("/button_play.png")).toExternalForm());
        //鼠标悬停时图片样式
        Image imgHover = new Image(Objects.requireNonNull(getClass().getResource("/button_play_hover.png")).toExternalForm());
        //默认常态
        ImageView iv = new ImageView(imgNormal);

        iv.setPreserveRatio(true);
        iv.setFitHeight(48);

        button.setText(null);
        button.setGraphic(iv);
        button.setBackground(Background.EMPTY);
        button.setPadding(Insets.EMPTY);

        // 添加悬停效果
        button.setOnMouseEntered(e -> iv.setImage(imgHover));
        button.setOnMouseExited(e -> iv.setImage(imgNormal));
    }
}

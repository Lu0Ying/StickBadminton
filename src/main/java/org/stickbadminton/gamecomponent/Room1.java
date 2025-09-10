package org.stickbadminton.gamecomponent;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
        addObject(new StickMan(1)).setPosition(200,300);
        //羽毛球(未完成)
        addObject(new Badminton()).setPosition(100, 300);
        //ui按钮，需css改样式
        Button button1 = new Button("下一房间");
        button1.setOnAction(e -> {Room2 room2 = new Room2(); this.leave(); room2.enter();});
        addUiNode(button1, 300, 400);
        //测试黄色按钮
        setupPlayButton(button1);

        //在 (300, 20) 处显示数字，电子显像管风格
        digitView=createDigitImageView(currentNumber);//初始化为0
        addUiNode(digitView, 300, 20);

        /*
        *（测试用）
         */
        //添加点击加一的按钮
        Button incrementButton = new Button("数字+1");
        incrementButton.setOnAction(e -> incrementNumber());
        addUiNode(incrementButton, 300, 80);
        //添加重置按钮
        Button reloadButton = new Button("重置");
        reloadButton.setOnAction(e -> reloadNumber());
        addUiNode(reloadButton, 300, 100);
        //添加数字选择下拉框
        setupNumberSelector();
    }
    private final ImageView digitView; // 用于更新显示的数字
    private int currentNumber = 0; // 当前显示的数字

    //数字加一
    private void incrementNumber() {
        currentNumber = (currentNumber + 1) % 10; // 0-9循环
        updateDigitImage(currentNumber);
    }
    //数字重置
    private void reloadNumber() {
        currentNumber = 0;
        updateDigitImage(currentNumber);
    }
    //数字选择
    private void setupNumberSelector() {
        // 创建0-9的数字选项
        ComboBox<Integer> numberSelector = new ComboBox<>();
        numberSelector.setItems(FXCollections.observableArrayList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        numberSelector.setValue(0); // 默认选择0
        numberSelector.setPromptText("选择数字");

        // 选择数字时更新显示
        numberSelector.setOnAction(e -> {
            Integer selectedNumber = numberSelector.getValue();
            if (selectedNumber != null) {
                currentNumber = selectedNumber;
                updateDigitImage(selectedNumber);
            }
        });
        addUiNode(numberSelector, 300, 120);
    }

    //创建数字图片视图
    private ImageView createDigitImageView(int number) {
        String imagePath = "/digital_" + number + ".png";
        try {
            Image digitImage = new Image(Objects.requireNonNull(getClass().getResource(imagePath)).toExternalForm());
            ImageView imageView = new ImageView(digitImage);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(30);
            return imageView;
        } catch (Exception e) {
            System.err.println("无法加载数字图片: " + imagePath);
            return new ImageView(); // 返回空ImageView
        }
    }

    //更新数字图片
    private void updateDigitImage(int number) {
        String imagePath = "/digital_" + number + ".png";
        try {
            Image digitImage = new Image(Objects.requireNonNull(getClass().getResource(imagePath)).toExternalForm());
            digitView.setImage(digitImage);
        } catch (Exception e) {
            System.err.println("无法更新数字图片: " + imagePath);
        }
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

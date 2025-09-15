package org.stickbadminton.gamecomponent;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.stickbadminton.UIObject;

import java.util.Objects;

public class  UIDigitView extends UIObject {
    private final ImageView digitView; // 用于更新显示的数字
    @Override
    public Node getUINode() {
        return digitView;
    }

    private int currentNumber; // 当前显示的数字
    public int getCurrentNumber() {
        return currentNumber;
    }
    public void setCurrentNumber(int currentNumber) {
        this.currentNumber = currentNumber;
        updateDigitImage(currentNumber);
    }

    public UIDigitView(){
        currentNumber = 0;
        digitView = createDigitImageView(0);
    }
    public UIDigitView(int currentNumber) {
        this.currentNumber = currentNumber;
        digitView = createDigitImageView(currentNumber);
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
}

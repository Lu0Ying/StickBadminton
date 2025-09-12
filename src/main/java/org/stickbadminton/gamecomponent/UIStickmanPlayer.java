package org.stickbadminton.gamecomponent;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.transform.Scale;
import org.stickbadminton.UIObject;

import java.util.Objects;

public class UIStickmanPlayer extends UIObject {
    private final ImageView PlayerView; // 用于更新显示的人物
    private boolean isFlipped = false; // 是否反转图片
    @Override
    public Node getUINode() {
        return PlayerView;
    }
    private int currentNumber; // 当前显示的数字
    public int getCurrentNumber() {
        return currentNumber;
    }
    public void setCurrentNumber(int currentNumber) {
        this.currentNumber = currentNumber;
        updatePlayerImage(currentNumber);
    }

    public UIStickmanPlayer(){
        currentNumber = 0;
        PlayerView = createPlayerImageView(0);
    }
    public UIStickmanPlayer(int currentNumber) {
        this.currentNumber = currentNumber;
        PlayerView = createPlayerImageView(currentNumber);
    }
    //创建数字图片视图
    private ImageView createPlayerImageView(int number) {
        if(number != 0) {
            String imagePath = "/stickman_player_" + number + ".png";
            try {
                Image playerImage = new Image(Objects.requireNonNull(getClass().getResource(imagePath)).toExternalForm());
                ImageView imageView = new ImageView(playerImage);
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(30);
                return imageView;
            } catch (Exception e) {
                System.err.println("无法加载人物图片: " + imagePath);
                return new ImageView(); // 返回空ImageView
            }
        }else return new ImageView();
    }

    //更新数字图片
    private void updatePlayerImage(int number) {
        if (number == 0) {
            // 数字为0时清空图片并隐藏
            PlayerView.setImage(null);
            PlayerView.setVisible(false);
        } else {
            // 数字不为0时显示图片并更新图片内容
            String imagePath = "/stickman_player_" + number + ".png";
            try {
                Image playerImage = new Image(Objects.requireNonNull(getClass().getResource(imagePath)).toExternalForm());
                PlayerView.setImage(playerImage);
                PlayerView.setVisible(true);
                updateFlipTransform(); // 更新反转状态
            } catch (Exception e) {
                System.err.println("无法更新人物图片: " + imagePath);
                PlayerView.setImage(null);
                PlayerView.setVisible(false);
            }
        }
    }
    Scale scale=new Scale(-1, 1);
    // 更新图片反转变换
    private void updateFlipTransform() {
//        System.out.println(isFlipped);//测试用
        // 移除现有的变换
        PlayerView.getTransforms().remove(scale);

        // 如果需要反转，添加反转变换
        if (isFlipped) {
            PlayerView.getTransforms().add(scale);
        }
    }

    public void setFlipped(boolean flipped) {
        isFlipped = flipped;
    }
}

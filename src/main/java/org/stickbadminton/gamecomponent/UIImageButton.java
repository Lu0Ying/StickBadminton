package org.stickbadminton.gamecomponent;

import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import org.stickbadminton.UIObject;

public class UIImageButton extends UIObject {
    private Button button;
    @Override
    public Node getUINode() {
        return button;
    }

    public UIImageButton(String sourceUrl) {
        button = new Button(sourceUrl);
        //常态图片样式
        Image imgNormal = new Image(sourceUrl);
        //鼠标悬停时图片样式
        StringBuffer sb = new StringBuffer(sourceUrl);
        sb.insert(sb.length() - 4, "_hover");
        Image imgHover = new Image(sb.toString());
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

    public void setOnAction(EventHandler<ActionEvent> eventHandler) {
        button.setOnAction(eventHandler);
    }
}

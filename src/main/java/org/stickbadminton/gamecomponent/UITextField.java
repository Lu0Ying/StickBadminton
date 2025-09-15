package org.stickbadminton.gamecomponent;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import org.stickbadminton.UIObject;

/**
 * 简单的文本输入框，适配 UIObject 体系。
 */
public class UITextField extends UIObject {

    private final TextField field;

    public UITextField() {
        this(null);
    }

    public UITextField(String promptText) {
        field = new TextField();
        field.setPromptText(promptText);
        field.setBackground(Background.EMPTY);
        field.setPadding(new Insets(6, 10, 6, 10));
        field.setPrefHeight(32);
        // 如需统一字号：field.setStyle("-fx-font-size: 16px;");
    }

    @Override
    public Node getUINode() {
        return field;
    }

    public void setPromptText(String text) {
        field.setPromptText(text);
    }

    public String getText() {
        return field.getText();
    }

    public void setText(String text) {
        field.setText(text);
    }

    public void setPrefWidth(double w) {
        field.setPrefWidth(w);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        field.setOnAction(handler); // 回车触发
    }

    public void setVisible(boolean visible) {
        field.setVisible(visible);
        field.setManaged(visible);
    }
}
package org.stickbadminton.gamecomponent;

import javafx.scene.Node;
import javafx.scene.text.Text;
import org.stickbadminton.UIObject;

public class UITextLabel extends UIObject {
    private final Text text;

    public UITextLabel(String content) {
        this.text = new Text(content);
    }

    @Override
    public Node getUINode() {
        return text;
    }

    // 可以加更多方法，如设置字体、颜色等
    public Text getTextNode() {
        return text;
    }
    public void setVisible(boolean visible) {
        text.setVisible(visible);
        text.setManaged(visible);
    }
}
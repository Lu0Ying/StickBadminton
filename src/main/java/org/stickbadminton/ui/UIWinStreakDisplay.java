package org.stickbadminton.ui;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.stickbadminton.UIObject;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.logic.WinStreakStore;

/**
 * 选人界面"连胜局数"模块：显示玩家当前连胜，数据经 WinStreakStore 接口读取。
 * 单人模式显示所选 AI 难度的连胜（难度没选或双人模式时显示历史最高连胜）。
 * 未来数据迁移到 MySQL/SQLite 只换存储实现，本类无需改动。
 */
public class UIWinStreakDisplay extends UIObject {

    private final Text labelText = new Text("连胜局数");
    private final Text valueText = new Text("0");
    private final HBox container = new HBox(8);

    public UIWinStreakDisplay() {
        labelText.setFont(Font.font("黑体", FontWeight.BOLD, 18));
        labelText.setFill(Color.WHITE);
        valueText.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        valueText.setFill(Color.GOLD);
        container.getChildren().addAll(labelText, valueText);
        refresh();
    }

    @Override
    public Node getUINode() {
        return container;
    }

    /** 按当前模式与难度刷新数值：仅在选人/撤销等事件点调用，不逐帧执行 */
    public void refresh() {
        valueText.setText(String.valueOf(readStreak()));
    }

    /** 单人且难度已选(1~5)读对应难度；否则读历史最高（跨难度取最大值） */
    private int readStreak() {
        WinStreakStore store = WinStreakStore.getInstance();
        int difficulty = GameProperties.difficultyLevel;
        if (GameProperties.matchMode == 1 && difficulty >= 1 && difficulty <= 5) {
            return store.getWinStreak(difficulty);
        }
        int max = 0;
        for (int d = 1; d <= 5; d++) {
            max = Math.max(max, store.getWinStreak(d));
        }
        return max;
    }
}

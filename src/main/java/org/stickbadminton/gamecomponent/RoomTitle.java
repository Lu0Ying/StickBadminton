package org.stickbadminton.gamecomponent;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import javafx.scene.image.ImageView;
import org.stickbadminton.SwitchRoomEffect;

public class RoomTitle extends Room{
    public RoomTitle() {
        addObject(new GameObject("title_background", new Image("title_background.png")));

        UIImageButton button_start = new UIImageButton("button_titlestart.png");
        button_start.setOnAction(e -> {
            GameProperties.matchMode = 1;
            RoomStickmanSelect rsr = new RoomStickmanSelect();
            new SwitchRoomEffect(this, rsr);
        });
        addUiObject(button_start, 650, 400);

        UIImageButton button_players = new UIImageButton("button_2player.png");
        button_players.setOnAction(e -> {
            GameProperties.matchMode = 2;
            RoomStickmanSelect rsr = new RoomStickmanSelect();
            new SwitchRoomEffect(this, rsr);
        });
        addUiObject(button_players, 650, 470);
    }
}

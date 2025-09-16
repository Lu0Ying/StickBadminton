package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;

public class RoomStickmanSelect extends Room{
    private int team1;
    private int team2;

    private UIStickmanPlayer view1;
    private UIStickmanPlayer view2;

    private HintAIDifficulty hintAIDifficulty;

    // 当前选择的队伍
    private boolean isTeam1Selecting = true;

    public RoomStickmanSelect(){
        // 选人界面



        addObject(new GameObject("background",new Image("stickmanselect_background.png")));
        UIImageButton startButton=new UIImageButton("button_start.png");
        addUiObject(startButton, 380, 480);
        startButton.setOnAction(e->{
            SoundPlay.playSound("button_select.mp3", 100);
            if(team1!=0&&team2!=0) {
                RoomGameplay roomGameplay = new RoomGameplay();
                new SwitchRoomEffect(this, roomGameplay);
            }
        });
        UIImageButton undoButton=new UIImageButton("button_undo.png");
        addUiObject(undoButton, 400, 350);
        undoButton.setOnAction(e-> {
            SoundPlay.playSound("button_select.mp3", 100);
            onUndoButtonClick();
        });

        UIImageButton infiniteEnergyButton=new UIImageButton("button_rectanglerounded.png");
        addUiObject(infiniteEnergyButton, 384, 410);
        infiniteEnergyButton.setOnAction(e->{
            SoundPlay.playSound("button_select.mp3", 100);
            GameProperties.infiniteEnergyMode = ! GameProperties.infiniteEnergyMode;
        });
        addObject(new InfiniteEnergySettingDisplay()).setPosition(375, 405);

        addCharacterButton(1,320,120);
        addCharacterButton(2,420,120);
        addCharacterButton(3,520,120);
        addCharacterButton(4,370,220);
        addCharacterButton(5,470,220);

        addObject(new GameObject("modeHint", new Image(
                GameProperties.matchMode == 1 ? "stickmanselect_mode1hint.png" : "stickmanselect_mode2hint.png"
        ))).setOpacity(0.2);

        if (GameProperties.matchMode == 1) {
            hintAIDifficulty = new HintAIDifficulty();
            addObject(hintAIDifficulty);
        }

        view1=new UIStickmanPlayer();
        view2=new UIStickmanPlayer();

        // 设置第二个角色图片反转（面向左边）
        view2.setFlipped(true);
        addUiObject(view1,65,320);
        addUiObject(view2,840,320);


    }
    private void addCharacterButton(int characterId, int x, int y) {
        UIImageButton button = new UIImageButton("selectbutton_" + characterId + ".png");
        button.setOnAction(e-> {
            SoundPlay.playSound("button_select.mp3", 100);
            onCharacterSelect(characterId);
        });
        addUiObject(button, x, y);
    }
    private void onCharacterSelect(int characterId){
        // 根据当前队伍添加到对应列表
        if (isTeam1Selecting) {
            if (GameProperties.matchMode == 1)
                hintAIDifficulty.isOpened = true;
            team1=characterId;
            GameProperties.characterType1 = characterId;
            isTeam1Selecting=false;
            updatePlayerViews();
        } else if (team2 == 0){
            if (GameProperties.matchMode == 1)
                GameProperties.difficultyLevel = characterId;
            team2=characterId;
            GameProperties.characterType2 = characterId;
            updatePlayerViews();
        }
    }
    //撤销上一次
    private  void onUndoButtonClick() {
        if (!isTeam1Selecting && team2 != 0) {
            team2 = 0;
            updatePlayerViews();
        } else if (!isTeam1Selecting && team1 != 0) {
            if (GameProperties.matchMode == 1)
                hintAIDifficulty.isOpened = false;
            team1 = 0;
            isTeam1Selecting=true;
            updatePlayerViews();
        }
    }
    private void updatePlayerViews(){
        view1.setCurrentNumber(team1);
        view2.setFlipped(true);
        view2.setCurrentNumber(team2);
    }
    public int getTeam1(){
        return team1;
    }
    public int getTeam2(){
        return team2;
    }

}

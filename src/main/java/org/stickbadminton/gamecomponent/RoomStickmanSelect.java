package org.stickbadminton.gamecomponent;

import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;

public class RoomStickmanSelect extends Room{
    private int team1;
    private int team2;

    private UIStickmanPlayer view1;
    private UIStickmanPlayer view2;

    // 当前选择的队伍
    private boolean isTeam1Selecting = true;

    public RoomStickmanSelect(){
        // 选人界面
        // 背景、按钮等素材已存在resources文件夹中
        // 由背景和5个选人按钮和start按钮和undo按钮构成
        // 背景: stickmanselect_background.png
        // 按钮: selectbutton_1.png 等
        // start按钮(放屏幕中下位置): button_start.png
        // undo按钮(放start按钮上面，和start按钮居中对齐):button_undo.png
        // 按钮物件用写好的 UIImageButton 类，具体用法见 Room1

        addObject(new GameObject("background",new Image("stickmanselect_background.png")));
        UIImageButton startButton=new UIImageButton("button_start.png");
        addUiObject(startButton, 380, 480);
        startButton.setOnAction(e->{
            if(team1!=0&&team2!=0) {
                Room1 room1 = new Room1();
                this.leave();
                room1.enter();
            }
        });
        UIImageButton undoButton=new UIImageButton("button_undo.png");
        addUiObject(undoButton, 400, 350);
        undoButton.setOnAction(e->onUndoButtonClick());
        addCharacterButton(1,420,120);
        addCharacterButton(2,320,120);
        addCharacterButton(3,520,120);
        addCharacterButton(4,370,220);
        addCharacterButton(5,470,220);


        view1=new UIStickmanPlayer();
        view2=new UIStickmanPlayer();

        // 设置第二个角色图片反转（面向左边）
        view2.setFlipped(true);
        addUiObject(view1,65,320);
        addUiObject(view2,840,320);


    }
    private void addCharacterButton(int characterId, int x, int y) {
        UIImageButton button = new UIImageButton("selectbutton_" + characterId + ".png");
        button.setOnAction(e-> onCharacterSelect(characterId));
        addUiObject(button, x, y);
    }
    private void onCharacterSelect(int characterId){
        // 根据当前队伍添加到对应列表
        if (isTeam1Selecting) {
            team1=characterId;
            GameProperties.characterType1 = characterId;
            isTeam1Selecting=false;
            updatePlayerViews();
        } else if (team2 == 0){
            team2=characterId;
            GameProperties.characterType2 = characterId;
            updatePlayerViews();
        }
    }
    //撤销上一次
    private  void onUndoButtonClick() {
        if (!isTeam1Selecting && team2 != 0) {
            team2 = 0;
            isTeam1Selecting=false;
            updatePlayerViews();
        } else if (!isTeam1Selecting && team1 != 0) {
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

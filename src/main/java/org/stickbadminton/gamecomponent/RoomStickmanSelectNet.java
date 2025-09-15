package org.stickbadminton.gamecomponent;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;
import org.stickbadminton.gamecomponent.network.NetworkClient;

public class RoomStickmanSelectNet extends Room{
    private int team1;
    private int team2;

    private UIStickmanPlayer view1;
    private UIStickmanPlayer view2;

    private HintAIDifficulty hintAIDifficulty;

    // 当前选择的队伍
    private boolean isTeam1Selecting = true;

    // ready/waiting 显示
    private UIImageButton buttonReady1;
    private UIImageButton buttonReady2;
    private UIImageButton buttonWaiting1;
    private UIImageButton buttonWaiting2;

    private volatile boolean p1Ready = false;
    private volatile boolean p2Ready = false;

    private NetworkClient netClient;

    // 保存服务器地址，便于由“加入房间界面”传入
    private final String serverHost;
    private final int serverPort;

    // 默认构造：兼容旧代码路径，可通过 -D/环境变量配置
    public RoomStickmanSelectNet(){
        this(
                System.getProperty("stb.server.host",
                        System.getenv().getOrDefault("STB_SERVER_HOST", "127.0.0.1")),
                Integer.parseInt(System.getProperty("stb.server.port",
                        System.getenv().getOrDefault("STB_SERVER_PORT", "8888")))
        );
    }

    // 新增：支持指定 host/port（与 RoomNetJoin 联动）
    public RoomStickmanSelectNet(String host, int port) {
        this.serverHost = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
        this.serverPort = (port >= 1 && port <= 65535) ? port : 8888;

        // 选人界面 UI
        buttonReady1 = new UIImageButton("button_ready.png");
        addUiObject(buttonReady1, 250, 200);
        buttonReady2 = new UIImageButton("button_ready.png");
        addUiObject(buttonReady2, 650, 200);
        buttonWaiting1 = new UIImageButton("button_waiting.png");
        addUiObject(buttonWaiting1, 250, 250);
        buttonWaiting2 = new UIImageButton("button_waiting.png");
        addUiObject(buttonWaiting2, 650, 250);

        // 初始 waiting
        p1Ready = false; p2Ready = false;
        updateNetReadyUi();

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
        view2.setFlipped(true);
        addUiObject(view1,65,320);
        addUiObject(view2,840,320);

        initNetStatusListener();
    }

    private void initNetStatusListener() {
        try {
            netClient = new NetworkClient(serverHost, serverPort, null);
            netClient.addConnectionListener(new NetworkClient.ConnectionListener() {
                @Override
                public void onAssigned(String playerId) {
                    if ("p1".equalsIgnoreCase(playerId)) setP1Ready(true);
                    else if ("p2".equalsIgnoreCase(playerId)) setP2Ready(true);
                }
                @Override
                public void onPlayerState(String playerId, boolean ready) {
                    if ("p1".equalsIgnoreCase(playerId)) setP1Ready(ready);
                    else if ("p2".equalsIgnoreCase(playerId)) setP2Ready(ready);
                }
                @Override
                public void onPlayerLeft(String playerId) {
                    if ("p1".equalsIgnoreCase(playerId)) setP1Ready(false);
                    else if ("p2".equalsIgnoreCase(playerId)) setP2Ready(false);
                }
            });
            netClient.start();

            // 把本地场景键盘事件转发给服务器（如果当前有 FXGL Scene）
            Platform.runLater(() -> netClient.attachToPrimaryScene());

        } catch (Exception e) {
            System.out.println("[RoomStickmanSelectNet] net init error: " + e.getMessage());
        }
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

    private void setP1Ready(boolean ready) {
        p1Ready = ready;
        updateNetReadyUi();
    }

    private void setP2Ready(boolean ready) {
        p2Ready = ready;
        updateNetReadyUi();
    }

    private void updateNetReadyUi(){
        Runnable r = () -> {
            if (buttonReady1 != null) buttonReady1.setVisible(p1Ready);
            if (buttonWaiting1 != null) buttonWaiting1.setVisible(!p1Ready);
            if (buttonReady2 != null) buttonReady2.setVisible(p2Ready);
            if (buttonWaiting2 != null) buttonWaiting2.setVisible(!p2Ready);
        };
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }

    public int getTeam1(){ return team1; }
    public int getTeam2(){ return team2; }
}
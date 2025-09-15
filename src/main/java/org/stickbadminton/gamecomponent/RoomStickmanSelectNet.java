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

    // ready/waiting 显示
    private UIImageButton buttonReady1;
    private UIImageButton buttonReady2;
    private UIImageButton buttonWaiting1;
    private UIImageButton buttonWaiting2;

    private volatile boolean p1Ready = false;
    private volatile boolean p2Ready = false;

    private NetworkClient netClient;
    private volatile String myId = null; // "p1" / "p2" / watcher*

    // 保存服务器地址与期望席位
    private final String serverHost;
    private final int serverPort;
    private final String desiredId; // 新增：HELLO 期望席位
    public RoomStickmanSelectNet(){
        this(
                System.getProperty("stb.server.host",
                        System.getenv().getOrDefault("STB_SERVER_HOST", "127.0.0.1")),
                Integer.parseInt(System.getProperty("stb.server.port",
                        System.getenv().getOrDefault("STB_SERVER_PORT", "8888"))),
                null
        );
    }

    // 新增构造：支持传入 desiredId（"p1" / "p2"）
    public RoomStickmanSelectNet(String host, int port, String desiredId) {
        this.serverHost = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
        this.serverPort = (port >= 1 && port <= 65535) ? port : 8888;
        this.desiredId = desiredId;

        addObject(new GameObject("background",new Image("stickmanselect_background.png")));

        // Ready / Waiting 指示
        buttonWaiting1 = new UIImageButton("button_waiting.png");
        addUiObject(buttonWaiting1, 720, 170);
        buttonWaiting2 = new UIImageButton("button_waiting.png");
        addUiObject(buttonWaiting2, 720, 170);
        buttonReady1 = new UIImageButton("button_ready.png");
        addUiObject(buttonReady1, 130, 170);
        buttonReady2 = new UIImageButton("button_ready.png");
        addUiObject(buttonReady2, 130, 170);

        // 点击自己一侧 Ready/Waiting 区域切换就绪
        buttonReady1.setOnAction(e -> tryToggleReady("p1"));
        buttonWaiting1.setOnAction(e -> tryToggleReady("p1"));
        buttonReady2.setOnAction(e -> tryToggleReady("p2"));
        buttonWaiting2.setOnAction(e -> tryToggleReady("p2"));

        updateNetReadyUi();

        // 开始按钮：发送 START 请求，由服务器校验并广播
        UIImageButton startButton=new UIImageButton("button_start.png");
        addUiObject(startButton, 380, 480);
        startButton.setOnAction(e->{
            SoundPlay.playSound("button_select.mp3", 100);
            if (netClient != null) {
                // 若我方已选人但未就绪，先自动就绪一次，减少误操作
                if ("p1".equalsIgnoreCase(myId) && !p1Ready && team1 > 0) {
                    netClient.sendReady(true);
                } else if ("p2".equalsIgnoreCase(myId) && !p2Ready && team2 > 0) {
                    netClient.sendReady(true);
                }
                // 只有都已就绪且都已选非0人物时才发送 START
                if (p1Ready && p2Ready && team1 > 0 && team2 > 0) {
                    netClient.sendStartRequest();
                } else {
                    System.out.println("[SelectNet] Start blocked: p1Ready=" + p1Ready + ", p2Ready=" + p2Ready
                            + ", team1=" + team1 + ", team2=" + team2 + " (请双方都选人并点就绪)");
                }
            }
        });

        // 撤销按钮（仅影响自己侧选择：清空为 0）
        UIImageButton undoButton=new UIImageButton("button_undo.png");
        addUiObject(undoButton, 400, 350);
        undoButton.setOnAction(e-> {
            SoundPlay.playSound("button_select.mp3", 100);
            onUndoButtonClick();
        });

        // 角色选择按钮：只能更改自己一侧
        addCharacterButton(1,320,120);
        addCharacterButton(2,420,120);
        addCharacterButton(3,520,120);
        addCharacterButton(4,370,220);
        addCharacterButton(5,470,220);

        // 模式提示与 AI 提示
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
            // 把 desiredId 传入，让服务器尽量分配对应席位
            netClient = new NetworkClient(serverHost, serverPort, desiredId);
            netClient.addConnectionListener(new NetworkClient.ConnectionListener() {
                @Override
                public void onAssigned(String playerId) {
                    myId = playerId;
                    System.out.println("[SelectNet] I am assigned as " + myId);
                }
                @Override
                public void onPlayerState(String playerId, boolean present) {
                    if ("p1".equalsIgnoreCase(playerId)) setP1Present(present);
                    else if ("p2".equalsIgnoreCase(playerId)) setP2Present(present);
                }
                @Override
                public void onPlayerLeft(String playerId) {
                    if ("p1".equalsIgnoreCase(playerId)) {
                        setP1Present(false);
                        applySelect("p1", 0);
                    } else if ("p2".equalsIgnoreCase(playerId)) {
                        setP2Present(false);
                        applySelect("p2", 0);
                    }
                }
                @Override
                public void onReadyState(String playerId, boolean ready) {
                    if ("p1".equalsIgnoreCase(playerId)) setP1Ready(ready);
                    else if ("p2".equalsIgnoreCase(playerId)) setP2Ready(ready);
                }
                @Override
                public void onSelected(String playerId, int characterId) {
                    applySelect(playerId, characterId);
                }
                @Override
                public void onStartGame(int ct1, int ct2) {
                    GameProperties.characterType1 = ct1;
                    GameProperties.characterType2 = ct2;
                    RoomGameplay roomGameplay = new RoomGameplay();
                    new SwitchRoomEffect(RoomStickmanSelectNet.this, roomGameplay);
                }
            });
            netClient.start();
            Platform.runLater(() -> netClient.attachToPrimaryStageAuto());
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
        // 只能选择自己一侧
        if ("p1".equalsIgnoreCase(myId)) {
            applySelect("p1", characterId);
            if (netClient != null) netClient.sendSelect(characterId);
            // 选中有效角色且尚未就绪时，自动就绪，减少误操作
            if (characterId > 0 && !p1Ready && netClient != null) netClient.sendReady(true);
        } else if ("p2".equalsIgnoreCase(myId)) {
            applySelect("p2", characterId);
            if (netClient != null) netClient.sendSelect(characterId);
            if (characterId > 0 && !p2Ready && netClient != null) netClient.sendReady(true);
        } else {
            // 观战者不允许改
            System.out.println("[SelectNet] watcher cannot select");
        }
    }

    private void onUndoButtonClick() {
        if ("p1".equalsIgnoreCase(myId) && team1 != 0) {
            applySelect("p1", 0);
            if (netClient != null) netClient.sendSelect(0);
            // 清空选择后自动取消就绪
            if (p1Ready && netClient != null) netClient.sendReady(false);
        } else if ("p2".equalsIgnoreCase(myId) && team2 != 0) {
            applySelect("p2", 0);
            if (netClient != null) netClient.sendSelect(0);
            if (p2Ready && netClient != null) netClient.sendReady(false);
        }
    }

    private void applySelect(String playerId, int characterId) {
        if ("p1".equalsIgnoreCase(playerId)) {
            team1 = characterId;
            GameProperties.characterType1 = characterId;
        } else if ("p2".equalsIgnoreCase(playerId)) {
            team2 = characterId;
            GameProperties.characterType2 = characterId;
        }
        updatePlayerViews();
    }

    private void tryToggleReady(String side) {
        if (myId == null) return;
        if (!myId.equalsIgnoreCase(side)) return; // 只能切换自己一侧
        boolean target = "p1".equalsIgnoreCase(side) ? !p1Ready : !p2Ready;
        if (netClient != null) netClient.sendReady(target);
    }

    private void updatePlayerViews(){
        view1.setCurrentNumber(team1);
        view2.setFlipped(true);
        view2.setCurrentNumber(team2);
    }

    private void setP1Present(boolean present) {
        if (!present) setP1Ready(false);
        updateNetReadyUi();
    }

    private void setP2Present(boolean present) {
        if (!present) setP2Ready(false);
        updateNetReadyUi();
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
            if (buttonWaiting2 != null) buttonWaiting2.setVisible(!p2Ready);
        };
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }

    public int getTeam1(){ return team1; }
    public int getTeam2(){ return team2; }
}
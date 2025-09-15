// RoomStickmanSelectNet.java
package org.stickbadminton.gamecomponent;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;
import org.stickbadminton.UIObject;
import org.stickbadminton.gamecomponent.network.NetworkClient;

import java.io.IOException;

public class RoomStickmanSelectNet extends Room {

    private final RoomStickmanSelectNet self = this;

    private int team1;
    private int team2;

    private UIStickmanPlayer view1;
    private UIStickmanPlayer view2;

    private HintAIDifficulty hintAIDifficulty;

    private UIImageButton buttonReady1;
    private UIImageButton buttonReady2;
    private UIImageButton buttonWaiting1;
    private UIImageButton buttonWaiting2;

    private volatile boolean p1Ready = false;
    private volatile boolean p2Ready = false;

    private NetworkClient netClient;
    private volatile String myId = null;

    private final String serverHost;
    private final int serverPort;
    private final String desiredId;

    public RoomStickmanSelectNet() {
        this(
                System.getProperty("stb.server.host", System.getenv().getOrDefault("STB_SERVER_HOST", "127.0.0.1")),
                Integer.parseInt(System.getProperty("stb.server.port", System.getenv().getOrDefault("STB_SERVER_PORT", "8888"))),
                null
        );
    }

    public RoomStickmanSelectNet(String host, int port, String desiredId) {
        this.serverHost = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
        this.serverPort = (port >= 1 && port <= 65535) ? port : 8888;
        this.desiredId = desiredId;

        addObject(new GameObject("background", new Image("stickmanselect_background.png")));

        buttonWaiting1 = new UIImageButton("button_waiting.png");
        addUiObject(buttonWaiting1, 720, 170);
        buttonWaiting2 = new UIImageButton("button_waiting.png");
        addUiObject(buttonWaiting2, 720, 170);
        buttonReady1 = new UIImageButton("button_ready.png");
        addUiObject(buttonReady1, 130, 170);
        buttonReady2 = new UIImageButton("button_ready.png");
        addUiObject(buttonReady2, 130, 170);

        buttonReady1.setVisible(false);
        buttonWaiting1.setVisible(true);
        buttonReady2.setVisible(false);
        buttonWaiting2.setVisible(true);

        buttonReady1.setOnAction(e -> tryToggleReady("p1"));
        buttonWaiting1.setOnAction(e -> tryToggleReady("p1"));
        buttonReady2.setOnAction(e -> tryToggleReady("p2"));
        buttonWaiting2.setOnAction(e -> tryToggleReady("p2"));

        updateNetReadyUi();

        UIImageButton startButton = new UIImageButton("button_start.png");
        addUiObject(startButton, 380, 480);
        startButton.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            if (netClient != null) {
                if ("p1".equalsIgnoreCase(myId) && !p1Ready && team1 > 0) netClient.setReady(true);
                if ("p2".equalsIgnoreCase(myId) && !p2Ready && team2 > 0) netClient.setReady(true);
            }
        });

        UIImageButton undoButton = new UIImageButton("button_undo.png");
        addUiObject(undoButton, 400, 350);
        undoButton.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            onUndoButtonClick();
        });

        addCharacterButton(1, 320, 120);
        addCharacterButton(2, 420, 120);
        addCharacterButton(3, 520, 120);
        addCharacterButton(4, 370, 220);
        addCharacterButton(5, 470, 220);

        addObject(new GameObject("modeHint", new Image(
                GameProperties.matchMode == 1 ? "stickmanselect_mode1hint.png" : "stickmanselect_mode2hint.png"
        ))).setOpacity(0.2);

        if (GameProperties.matchMode == 1) {
            hintAIDifficulty = new HintAIDifficulty();
            addObject(hintAIDifficulty);
        }

        view1 = new UIStickmanPlayer();
        view2 = new UIStickmanPlayer();
        view2.setFlipped(true);
        addUiObject(view1, 65, 320);
        addUiObject(view2, 840, 320);

        initNetStatusListener();
    }

    private void initNetStatusListener() {
        try {
            netClient = new NetworkClient(serverHost, serverPort, desiredId);
            netClient.addConnectionListener(new NetworkClient.ConnectionListener() {
                @Override
                public void onAssigned(String playerId) {
                    myId = playerId;
                }

                @Override
                public void onPlayerState(String playerId, boolean present) {
                    if ("p1".equalsIgnoreCase(playerId)) setP1Present(present);
                    else if ("p2".equalsIgnoreCase(playerId)) setP2Present(present);
                }

                @Override
                public void onPlayerLeft(String playerId) {
                    if ("p1".equalsIgnoreCase(playerId)) setP1Present(false);
                    else if ("p2".equalsIgnoreCase(playerId)) setP2Present(false);
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
                    RoomGameplay roomGameplay = new RoomGameplay(netClient);
                    new SwitchRoomEffect(self, roomGameplay);
                }
            });
            netClient.connect();
        } catch (IOException e) {
            System.out.println("[SelectNet] connect failed: " + e.getMessage());
        }
    }

    private void addCharacterButton(int characterId, int x, int y) {
        UIImageButton button = new UIImageButton("selectbutton_" + characterId + ".png");
        button.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            onCharacterSelect(characterId);
        });
        addUiObject(button, x, y);
    }

    private void onCharacterSelect(int characterId) {
        if (myId == null) return;
        if ("p1".equalsIgnoreCase(myId)) {
            applySelect("p1", characterId);
            netClient.selectCharacter(characterId);
            if (characterId > 0 && !p1Ready) netClient.setReady(true);
        } else if ("p2".equalsIgnoreCase(myId)) {
            applySelect("p2", characterId);
            netClient.selectCharacter(characterId);
            if (characterId > 0 && !p2Ready) netClient.setReady(true);
        }
    }

    private void onUndoButtonClick() {
        if (myId == null) return;
        if ("p1".equalsIgnoreCase(myId) && team1 != 0) {
            applySelect("p1", 0);
            netClient.selectCharacter(0);
            if (p1Ready) netClient.setReady(false);
        } else if ("p2".equalsIgnoreCase(myId) && team2 != 0) {
            applySelect("p2", 0);
            netClient.selectCharacter(0);
            if (p2Ready) netClient.setReady(false);
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
        if (myId == null || !myId.equalsIgnoreCase(side)) return;
        boolean target = "p1".equalsIgnoreCase(side) ? !p1Ready : !p2Ready;
        netClient.setReady(target);
    }

    private void updatePlayerViews() {
        view1.setCurrentNumber(team1);
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

    private void updateNetReadyUi() {
        Platform.runLater(() -> {
            buttonReady1.setVisible(p1Ready);
            buttonWaiting1.setVisible(!p1Ready);
            buttonReady2.setVisible(p2Ready);
            buttonWaiting2.setVisible(!p2Ready);
        });
    }

    public int getTeam1() { return team1; }
    public int getTeam2() { return team2; }
}
package org.stickbadminton.gamecomponent.network;

import java.io.Serializable;
import java.util.Map;

public class Message implements Serializable {
    private MessageType type;
    private Map<String, Object> data;

    // 构造函数、getter和setter

    public enum MessageType {
        CONNECT, DISCONNECT, GAME_START, GAME_UPDATE, PLAYER_INPUT, GAME_END
    }
}

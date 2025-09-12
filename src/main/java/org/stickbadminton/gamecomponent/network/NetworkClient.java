package org.stickbadminton.gamecomponent.network;

import java.io.*;
import java.net.*;

public class NetworkClient {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private boolean connected = false;
    private GameController gameController; // 游戏控制器引用

    public NetworkClient(GameController gameController) {
        this.gameController = gameController;
    }

    public boolean connect(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;

            // 启动接收服务器消息的线程
            startListening();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void startListening() {
        new Thread(() -> {
            try {
                String message;
                while ((message = reader.readLine()) != null) {
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                System.out.println("与服务器断开连接");
                connected = false;
            }
        }).start();
    }

    private void handleServerMessage(String message) {
        // 根据服务器发送的消息类型处理
        if (message.equals("GAME_START")) {
            gameController.startNetworkGame();
        } else if (message.equals("POSITION_LEFT")) {
            gameController.setPlayerPosition("LEFT");
        } else if (message.equals("POSITION_RIGHT")) {
            gameController.setPlayerPosition("RIGHT");
        } else if (message.startsWith("PLAYER_MOVE:")) {
            // 处理玩家移动
            String[] parts = message.split(":");
            if (parts.length == 3) {
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                gameController.updateOpponentPosition(x, y);
            }
        } else if (message.startsWith("PLAYER_HIT:")) {
            // 处理对方击球
            String[] parts = message.split(":");
            if (parts.length >= 3) {
                float forceX = Float.parseFloat(parts[1]);
                float forceY = Float.parseFloat(parts[2]);
                gameController.opponentHitShuttle(forceX, forceY);
            }
        } else if (message.startsWith("SCORE_UPDATE:")) {
            // 更新分数
            String[] parts = message.split(":");
            if (parts.length == 3) {
                int playerScore = Integer.parseInt(parts[1]);
                int opponentScore = Integer.parseInt(parts[2]);
                gameController.updateScore(playerScore, opponentScore);
            }
        }
    }

    public void sendPlayerMove(float x, float y) {
        if (connected) {
            writer.println("PLAYER_MOVE:" + x + ":" + y);
        }
    }

    public void sendPlayerHit(float forceX, float forceY) {
        if (connected) {
            writer.println("PLAYER_HIT:" + forceX + ":" + forceY);
        }
    }

    public void sendScoreUpdate(int myScore, int opponentScore) {
        if (connected) {
            writer.println("SCORE_UPDATE:" + myScore + ":" + opponentScore);
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
            if (reader != null) reader.close();
            if (writer != null) writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
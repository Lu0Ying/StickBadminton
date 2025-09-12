package org.stickbadminton.gamecomponent.network;

import java.io.*;
import java.net.*;
import java.util.*;

public class GameServer {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private int port = 8888;
    private boolean isRunning = false;

    public GameServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("服务器启动，监听端口: " + port);
            isRunning = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    System.out.println("等待玩家连接...");
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("玩家已连接: " + clientSocket.getInetAddress());

                    // 为新客户端创建处理线程
                    ClientHandler clientHandler = new ClientHandler(clientSocket, clients.size());
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();

                    // 如果有两个玩家，可以开始游戏
                    if (clients.size() == 2) {
                        startGame();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void startGame() {
        // 通知所有客户端游戏开始
        for (ClientHandler client : clients) {
            client.sendMessage("GAME_START");
        }

        // 分配玩家位置
        clients.get(0).sendMessage("POSITION_LEFT");
        clients.get(1).sendMessage("POSITION_RIGHT");
    }

    // 处理客户端的线程类
    private class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;
        private int playerId;

        public ClientHandler(Socket socket, int playerId) {
            this.socket = socket;
            this.playerId = playerId;
            try {
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = reader.readLine()) != null) {
                    System.out.println("从玩家 " + playerId + " 收到: " + message);

                    // 转发玩家行为给其他玩家
                    for (ClientHandler client : clients) {
                        if (client.playerId != this.playerId) {
                            client.sendMessage(message);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("玩家 " + playerId + " 断开连接");
                clients.remove(this);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void sendMessage(String message) {
            writer.println(message);
        }
    }

    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}

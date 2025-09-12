package org.stickbadminton.gamecomponent.network;

// 仅展示需要修改的部分
public class GameController {
    private NetworkClient networkClient;
    private boolean isNetworkGame = false;
    private String playerPosition; // "LEFT" 或 "RIGHT"

    // 其他已有的游戏属性...

    public void initNetworkGame() {
        networkClient = new NetworkClient(this);
        // 显示连接界面，让用户输入服务器地址
    }

    public void connectToServer(String address, int port) {
        boolean success = networkClient.connect(address, port);
        if (success) {
            // 等待服务器分配位置和开始游戏...
            isNetworkGame = true;
        } else {
            // 显示连接失败消息
        }
    }

    public void startNetworkGame() {
        // 初始化网络游戏状态
    }

    public void setPlayerPosition(String position) {
        this.playerPosition = position;
        // 根据位置初始化玩家和对手
    }

    @Override
    public void update() {
        // 原有的更新逻辑

        // 如果是网络游戏，需要发送自己的位置信息
        if (isNetworkGame && player != null) {
            networkClient.sendPlayerMove(player.getX(), player.getY());
        }
    }

    // 当本地玩家击球时
    public void onPlayerHitShuttle(float forceX, float forceY) {
        if (isNetworkGame) {
            networkClient.sendPlayerHit(forceX, forceY);
        }
    }

    // 当收到对手击球信息时
    public void opponentHitShuttle(float forceX, float forceY) {
        // 更新羽毛球物理状态
        shuttle.applyForce(forceX, forceY);
    }

    // 更新对手位置
    public void updateOpponentPosition(float x, float y) {
        if (opponent != null) {
            opponent.setPosition(x, y);
        }
    }

    // 更新分数
    public void updateScore(int playerScore, int opponentScore) {
        // 根据玩家位置判断哪个是自己的分数
        if (playerPosition.equals("LEFT")) {
            this.playerScore = playerScore;
            this.opponentScore = opponentScore;
        } else {
            this.playerScore = opponentScore;
            this.opponentScore = playerScore;
        }
    }

    // 游戏结束时断开网络连接
    public void endGame() {
        if (isNetworkGame && networkClient != null) {
            networkClient.disconnect();
        }
        // 其他清理工作...
    }
}

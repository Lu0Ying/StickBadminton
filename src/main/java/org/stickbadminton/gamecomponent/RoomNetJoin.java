package org.stickbadminton.gamecomponent;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import org.stickbadminton.GameObject;
import org.stickbadminton.Room;
import org.stickbadminton.SoundPlay;
import org.stickbadminton.SwitchRoomEffect;
import org.stickbadminton.gamecomponent.network.GameServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 联机入口：
 * - 创建房间（做主机）：本机启动服务器并进入选人界面（默认请求 p1）
 * - 加入别人房间：输入对方地址进入选人界面（默认请求 p2）
 * - 返回主菜单
 */
public class RoomNetJoin extends Room {

    // 主按钮
    private UIImageButton btnCreateHost;
    private UIImageButton btnJoin;
    private UIImageButton btnBackToMenu;

    // 加入模式 UI
    private UITextField ipField;
    private UITextField portField;
    private UIImageButton btnJoinConfirm;
    private UIImageButton btnBack;

    private static final int DEFAULT_PORT = 8889;

    // 本机服务器（仅当“创建房间”时启动一次）
    private static volatile GameServer localServer = null;
    private static volatile Thread localServerThread = null;
    private static volatile int localServerPort = -1;

    // 调试监听，保留强引用防GC
    private final ChangeListener<String> ipChangeLogger = (obs, o, n) ->
            System.out.println("[RoomNetJoin] ip now='" + n + "' len=" + (n == null ? 0 : n.length()));
    private final ChangeListener<String> portChangeLogger = (obs, o, n) ->
            System.out.println("[RoomNetJoin] port now='" + n + "' len=" + (n == null ? 0 : n.length()));

    public RoomNetJoin() {
        // 背景
        addObject(new GameObject("background", new Image("stickmanselect_background_clear.png"))).setOpacity(0.85);

        // 创建房间（默认请求 p1）
        btnCreateHost = new UIImageButton("button_create.png");
        addUiObject(btnCreateHost, 395, 180);

        btnCreateHost.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            onCreateHost();
        });

        // 加入房间（进入加入模式）
        btnJoin = new UIImageButton("button_join.png");
        addUiObject(btnJoin, 415, 250);
        btnJoin.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            enterJoinMode();
        });

        // 返回主菜单
        btnBackToMenu = new UIImageButton("button_undo.png");
        addUiObject(btnBackToMenu, 400, 320);
        btnBackToMenu.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            RoomTitle title = new RoomTitle();
            new SwitchRoomEffect(this, title);
        });

        // 加入模式控件：默认隐藏
        ipField = new UITextField("服务器地址");
        ipField.setPrefWidth(360);
        addUiObject(ipField, 320, 180);
        ipField.setVisible(false);

        portField = new UITextField("端口，默认 8889（仅数字）");
        portField.setPrefWidth(180);
        portField.setText(String.valueOf(DEFAULT_PORT)); // 有效默认值
        addUiObject(portField, 360, 230);
        portField.setVisible(false);

        // 端口仅允许数字
        TextField tfPort = (TextField) portField.getUINode();
        tfPort.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,5}") ? change : null;
        }));

        btnJoinConfirm = new UIImageButton("button_start.png"); // “加入/开始”
        addUiObject(btnJoinConfirm, 380, 290);
        btnJoinConfirm.setVisible(false);
        btnJoinConfirm.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            onJoinConfirm();
        });
        // 在输入框回车直接加入
        ((TextField) ipField.getUINode()).setOnAction(ev -> onJoinConfirm());
        tfPort.setOnAction(ev -> onJoinConfirm());

        btnBack = new UIImageButton("button_undo.png"); // “返回（退出加入模式）”
        addUiObject(btnBack, 400, 350);
        btnBack.setVisible(false);
        btnBack.setOnAction(e -> {
            SoundPlay.playSound("button_select.mp3", 100);
            leaveJoinMode();
        });
    }

    // ——— 创建房间：默认请求 p1 ———
    private void onCreateHost() {
        int port = ensureLocalServerStartedAndGetPort(DEFAULT_PORT);
        if (port <= 0) {
            System.out.println("[RoomNetJoin] 无法启动本地服务器，所有候选端口都被占用");
            return;
        }
        // 自己作为主机，客户端连到本机实际端口，默认 HELLO:p1
        goToSelect("127.0.0.1", port, "p1");
    }

    private void enterJoinMode() {
        ipField.setText("");
        portField.setText(String.valueOf(DEFAULT_PORT));

        ipField.setVisible(true);
        portField.setVisible(true);
        btnJoinConfirm.setVisible(true);
        btnBack.setVisible(true);

        btnCreateHost.setVisible(false);
        btnJoin.setVisible(false);
        btnBackToMenu.setVisible(false);

        Platform.runLater(() -> {
            try {
                ipField.getUINode().requestFocus();
                System.out.println("[RoomNetJoin] 已请求将焦点放到 IP 输入框");
            } catch (Exception ignored) {}
        });

        try {
            TextField tfIP = (TextField) ipField.getUINode();
            TextField tfPort = (TextField) portField.getUINode();
            tfIP.textProperty().addListener(ipChangeLogger);
            tfPort.textProperty().addListener(portChangeLogger);
        } catch (Exception ignored) {}
    }

    private void leaveJoinMode() {
        ipField.setVisible(false);
        portField.setVisible(false);
        btnJoinConfirm.setVisible(false);
        btnBack.setVisible(false);

        btnCreateHost.setVisible(true);
        btnJoin.setVisible(true);
        btnBackToMenu.setVisible(true);

        try {
            TextField tfIP = (TextField) ipField.getUINode();
            TextField tfPort = (TextField) portField.getUINode();
            tfIP.textProperty().removeListener(ipChangeLogger);
            tfPort.textProperty().removeListener(portChangeLogger);
        } catch (Exception ignored) {}
    }

    // ——— 加入别人：默认请求 p2 ———
    private void onJoinConfirm() {
        String hostInput = safeTrim(ipField);
        String portInput = safeTrim(portField);

        System.out.println("[RoomNetJoin] onJoinConfirm read ipField='" + hostInput + "' len=" + hostInput.length()
                + ", portField='" + portInput + "' len=" + portInput.length());

        HostPort hp = parseHostPort(hostInput);
        String host = hp.host;
        Integer port = hp.port;

        if ((host == null || host.isEmpty()) && looksLikeHost(portInput)) {
            host = portInput;
            System.out.println("[RoomNetJoin] 检测到端口框误填了 host，按 host='" + host + "' 处理");
            port = null;
        }

        int finalPort = (port != null) ? port : parsePortOrDefault(portInput, DEFAULT_PORT);

        if (host == null || host.isEmpty()) {
            System.out.println("[RoomNetJoin] IP 不能为空，请先输入 IP");
            return;
        }

        System.out.println("[RoomNetJoin] 即将加入 host='" + host + "', port=" + finalPort);
        // 加入别人 → 默认 HELLO:p2
        goToSelect(host, finalPort, "p2");
    }

    // 跳转到选人，携带期望席位
    private void goToSelect(String host, int port, String desiredId) {
        RoomStickmanSelectNet room = new RoomStickmanSelectNet(host, port, desiredId);
        new SwitchRoomEffect(this, room);
        Platform.runLater(() -> System.out.println("[RoomNetJoin] Join " + host + ":" + port + " (desired=" + desiredId + ")"));
    }

    // —— 帮助方法 ——
    private static class HostPort {
        final String host; final Integer port;
        HostPort(String h, Integer p) { host = h; port = p; }
    }

    private static HostPort parseHostPort(String input) {
        if (input == null) return new HostPort("", null);
        String s = input.trim();
        if (s.isEmpty()) return new HostPort("", null);
        int idx = s.lastIndexOf(':');
        if (idx > 0 && idx < s.length() - 1) {
            String h = s.substring(0, idx).trim();
            String p = s.substring(idx + 1).trim();
            if (p.matches("\\d{1,5}")) {
                int pv = Integer.parseInt(p);
                if (pv >= 1 && pv <= 65535) return new HostPort(h, pv);
            }
        }
        return new HostPort(s, null);
    }

    private static boolean looksLikeHost(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        if (t.matches("\\d+")) return false; // 纯数字像端口
        return t.contains(".") || t.matches(".*[a-zA-Z].*");
    }

    private static String safeTrim(UITextField f) {
        try {
            String s = f.getText();
            return s == null ? "" : s.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static int parsePortOrDefault(String text, int def) {
        if (text == null) return def;
        String s = text.trim();
        if (s.isEmpty()) return def;
        try {
            int p = Integer.parseInt(s);
            if (p >= 1 && p <= 65535) return p;
        } catch (NumberFormatException ignored) {}
        return def;
    }

    // 尝试从 preferredPort 开始寻找可用端口（最多尝试 21 个）
    private static int ensureLocalServerStartedAndGetPort(int preferredPort) {
        if (localServer != null && localServerPort > 0) return localServerPort;
        synchronized (RoomNetJoin.class) {
            if (localServer != null && localServerPort > 0) return localServerPort;

            int maxTry = 21;
            for (int i = 0; i < maxTry; i++) {
                int p = preferredPort + i;
                if (!isPortFree(p)) {
                    System.out.println("[RoomNetJoin] 端口 " + p + " 已占用，尝试下一个...");
                    continue;
                }
                try {
                    GameServer server = new GameServer(p);
                    Thread t = new Thread(() -> {
                        try {
                            server.start();
                        } catch (Exception e) {
                            System.out.println("[RoomNetJoin] start server failed on " + p + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }, "Local-GameServer");
                    t.setDaemon(true);
                    t.start();
                    localServer = server;
                    localServerThread = t;
                    localServerPort = p;
                    System.out.println("[RoomNetJoin] Local server launching on port " + p);
                    return p;
                } catch (Exception e) {
                    System.out.println("[RoomNetJoin] 创建服务器失败（端口 " + p + "）: " + e.getMessage());
                    localServer = null;
                    localServerPort = -1;
                }
            }
            return -1;
        }
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
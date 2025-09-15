package org.stickbadminton;

import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.HashSet;
import java.util.Set;

public class KeyInput {
    private static final Set<KeyCode> keys = new HashSet<>();

    // 联机模式开关：true 时，屏蔽本地物理输入对 KeyInput 的影响，只接收“服务器注入”的事件
    private static volatile boolean networkMode = false;

    // 服务器注入标记：NetworkClient 注入事件时置 true，使得 KeyInput 识别并放行
    private static final ThreadLocal<Boolean> SERVER_INJECTION = ThreadLocal.withInitial(() -> false);

    public static void initInput() {
        FXGL.getPrimaryStage().getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (networkMode && !Boolean.TRUE.equals(SERVER_INJECTION.get())) {
                // 联机模式下，本地物理按键不写入 keys，但不消费事件，保证 NetworkClient 仍能采集并上报给服务器
                return;
            }
            keys.add(e.getCode());
        });

        FXGL.getPrimaryStage().getScene().addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (networkMode && !Boolean.TRUE.equals(SERVER_INJECTION.get())) {
                // 同上：不写入、不消费
                return;
            }
            keys.remove(e.getCode());
        });
    }

    public static boolean isKeyHolding(KeyCode keyCode) {
        return keys.contains(keyCode);
    }

    // —— 联机模式控制 —— //
    public static void enableNetworkMode() {
        networkMode = true;
        // 清一次，避免联机切换时保留旧的本地按下状态
        keys.clear();
    }

    public static void disableNetworkMode() {
        networkMode = false;
        keys.clear();
    }

    // —— 仅供 NetworkClient 在注入事件时调用 —— //
    public static void beginServerInjection() {
        SERVER_INJECTION.set(true);
    }

    public static void endServerInjection() {
        SERVER_INJECTION.set(false);
    }
}
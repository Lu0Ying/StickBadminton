package org.stickbadminton;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.util.HashMap;

public class SoundPlay {
    private static MediaPlayer backgroundPlayer;
    private static double backgroundVolume = 1;
    private static HashMap<String, Media> soundCache=new HashMap<>();
    // 每个音效复用一个播放器实例：逐拍 new MediaPlayer 在长时间对局后会耗尽原生播放器
    // 资源，创建失败抛 MediaException——该异常若逸出会中断 heavyHit/lightHit 的出球逻辑
    // （球速未设置 = "AI 跳起挥拍却接不到球"），故必须限资源 + 吞异常双保险
    private static final HashMap<String, MediaPlayer> oneShotPlayers = new HashMap<>();
    // 初始化音频池
    public static void initSoundPool() {
        // 加载所有资源
        loadSound("add_score.mp3");
        loadSound("button_select.mp3");
        loadSound("cheer.mp3");
        loadSound("fire.mp3");
        loadSound("ingame_bgm.mp3");
        loadSound("net_crash.mp3");
        loadSound("shot_light.mp3");
        loadSound("shot_heavy.mp3");
        loadSound("sigh.mp3");
        loadSound("title_bgm.mp3");
        warmUpSounds();
    }

    // 对局音效预热：原生播放器创建从"对局中首次触发"提前到启动时——
    // 扣杀首帧原本要叠加 fire.mp3/shot_heavy 的原生创建，与 40 粒 burst 同帧就是
    // 肉眼可见的卡顿；预热后对局中恒为"复用已有播放器"零创建
    private static final String[] WARM_UP_SOUNDS = {
            "shot_heavy.mp3", "shot_light.mp3", "fire.mp3",
            "add_score.mp3", "cheer.mp3", "net_crash.mp3", "sigh.mp3"
    };

    private static void warmUpSounds() {
        for (String s : WARM_UP_SOUNDS) obtainPlayer(s); // 仅创建，不发声
    }

    private static void loadSound(String sourceUrl) {
        // 加载某个资源到音频池，从 classpath 读取，打包后同样有效
        Media media = loadMedia(sourceUrl);
        if (media != null) {
            soundCache.put(sourceUrl, media);
        }
    }

    // 统一从 classpath 读取音频资源，缺失/解析失败时打印错误并返回 null，避免游戏崩溃
    private static Media loadMedia(String sourceUrl) {
        var url = SoundPlay.class.getResource("/sounds/" + sourceUrl);
        if (url == null) {
            System.err.println("音频资源缺失:" + sourceUrl);
            return null;
        }
        try {
            return new Media(url.toExternalForm());
        } catch (RuntimeException e) {
            System.err.println("音频解析失败:" + sourceUrl + " - " + e);
            return null;
        }
    }

    // 设置背景音乐（创建失败仅降级为无 BGM，不中断比赛计分/发球等主流程）
    public static void setBackgroundMusic(String sourceUrl) {
        if (backgroundPlayer != null) {
            backgroundPlayer.stop();
            backgroundPlayer.dispose(); // 释放资源
            backgroundPlayer = null;
        }
        Media media = loadMedia(sourceUrl);
        if (media == null) {
            return;
        }
        try {
            backgroundPlayer=new MediaPlayer(media);

            //设置循环播放和音量
            backgroundPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            backgroundPlayer.setVolume(backgroundVolume);
        } catch (RuntimeException e) {
            backgroundPlayer = null;
            System.err.println("背景音乐创建失败(不影响游戏): " + sourceUrl + " - " + e);
        }
    }
    public static void playBackgroundMusic() {
        // 播放背景音乐（异常不外逸：调用点在计分/发球主流程）
        try {
            if(backgroundPlayer!=null) {
                backgroundPlayer.play();
            }else{
                System.err.println("请先设置音乐");
            }
        } catch (RuntimeException e) {
            System.err.println("背景音乐播放失败(不影响游戏): " + e);
        }
    }
    public static void stopBackgroundMusic() {
        // 暂停播放背景音乐（异常不外逸：调用点在得分流程）
        try {
            if(backgroundPlayer!=null){
                backgroundPlayer.pause();
            }
        } catch (RuntimeException e) {
            System.err.println("背景音乐暂停失败(不影响游戏): " + e);
        }
    }
    public static void setBackgroundMusicVolume(double volume) {
        // 设置背景音乐音量
        // volume 范围：0为静音，1为原音量
        backgroundVolume=Math.max(0,Math.min(1,volume));
        if(backgroundPlayer!=null){
            backgroundPlayer.setVolume(backgroundVolume);
        }

    }
    public static void playSound(String sourceUrl, double volume) {
        // 播放指定的音效，以给定的音量；volume 范围：0为静音，1为原音量。
        // 每个音效复用单个播放器（重叠触发时 seek(0) 重播），原生播放器资源恒定占用不增长。
        // 音效位于击球/计分等关键逻辑中，任何播放失败必须就地吞掉并丢弃坏实例，
        // 绝不允许异常逸出中断出球（曾导致 heavyHit 中断、球速未设置的"挥拍接不到球"）
        try {
            MediaPlayer soundPlayer = obtainPlayer(sourceUrl);
            if (soundPlayer == null) return;
            soundPlayer.setVolume(Math.max(0,Math.min(1,volume)));
            soundPlayer.seek(Duration.ZERO); // 从头重播，覆盖前一次未播完的同音效
            soundPlayer.play();
        } catch (RuntimeException e) {
            // 原生播放器创建/播放失败（如资源耗尽）：丢弃坏实例，下一拍自动重建
            oneShotPlayers.remove(sourceUrl);
            System.err.println("音效播放失败(不影响游戏): " + sourceUrl + " - " + e);
        }
    }

    // 取得（必要时创建）复用播放器；仅创建不发声，失败返回 null（不抛异常）
    private static MediaPlayer obtainPlayer(String sourceUrl) {
        MediaPlayer soundPlayer = oneShotPlayers.get(sourceUrl);
        if (soundPlayer != null) return soundPlayer;
        Media media = soundCache.get(sourceUrl);
        if (media == null) {
            // 如果音效没有预加载，则动态加载
            media = loadMedia(sourceUrl);
            if (media == null) return null;
            soundCache.put(sourceUrl, media);
        }
        soundPlayer = new MediaPlayer(media);
        oneShotPlayers.put(sourceUrl, soundPlayer);
        return soundPlayer;
    }

    //手动移除不需要的音效（缓存 Media 与复用播放器一并清理）
    public static void removeSoundFromCache(String sourceUrl) {
        soundCache.remove(sourceUrl);
        MediaPlayer player = oneShotPlayers.remove(sourceUrl);
        if (player != null) {
            try { player.dispose(); } catch (RuntimeException ignored) { }
        }
    }
}

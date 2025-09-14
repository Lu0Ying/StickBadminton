package org.stickbadminton;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.HashMap;

public class SoundPlay {
    private static MediaPlayer backgroundPlayer;
    private static double backgroundVolume = 1;
    private static HashMap<String, Media> soundCache=new HashMap<>();
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
    }

    private static void loadSound(String sourceUrl) {
        // 加载某个资源到音频池
        try{
            String path=getAbsolutePath(sourceUrl);
            Media media=new Media(new File(path).toURI().toURL().toString());
            soundCache.put(sourceUrl, media);
        }catch(MalformedURLException e) {
            System.err.println("无效音频路径:"+e.getMessage());
        }
    }

    // 播放音频相关的函数
    public static String getAbsolutePath(String relativePath) {
       //如果为绝对路径，返回
        if(Paths.get(relativePath).isAbsolute()) {
            return relativePath;
        }
        String basePath=Paths.get("src","main","resources","sounds").toString();
        return Paths.get(basePath,relativePath).toString();
    }
    // 设置背景音乐
    public static void setBackgroundMusic(String sourceUrl) {
       try{
           if (backgroundPlayer != null) {
               backgroundPlayer.stop();
               backgroundPlayer.dispose(); // 释放资源
           }
           Media media=new Media(new File(getAbsolutePath(sourceUrl)).toURI().toURL().toString());
           backgroundPlayer=new MediaPlayer(media);

           //设置循环播放和音量
           backgroundPlayer.setCycleCount(MediaPlayer.INDEFINITE);
           backgroundPlayer.setVolume(backgroundVolume);
       }catch (MalformedURLException e) {
            System.err.println("无效的音频文件路径:"+e.getMessage());
       }

    }
    public static void playBackgroundMusic() {
        // 播放背景音乐
        if(backgroundPlayer!=null) {
            backgroundPlayer.play();
        }else{
            System.err.println("请先设置音乐");
        }

    }
    public static void stopBackgroundMusic() {
        // 暂停播放背景音乐
        if(backgroundPlayer!=null){
            backgroundPlayer.pause();
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
        // 播放指定的音效，以给定的音量
        // volume 范围：0为静音，1为原音量
        try {
            Media media = soundCache.get(sourceUrl);
            if(media==null){
                // 如果音效没有预加载，则动态加载
                media = new Media(new File(getAbsolutePath(sourceUrl)).toURI().toURL().toString());
                soundCache.put(sourceUrl, media);
            }
            MediaPlayer soundPlayer = new MediaPlayer(media);
            soundPlayer.setVolume(Math.max(0,Math.min(1,volume)));
            soundPlayer.play();
            soundPlayer.setOnEndOfMedia(() -> soundPlayer.dispose());
        }catch (MalformedURLException e){
            System.err.println("无效的音频文件路径:"+e.getMessage());
        }
    }

    //手动移除不需要的音效
    public static void removeSoundFromCache(String sourceUrl) {
        soundCache.remove(sourceUrl);
    }
}

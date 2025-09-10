package org.stickbadminton;

import com.almasb.fxgl.audio.Music;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Paths;

public class SoundPlay {
    private static MediaPlayer backgroundPlayer;
    private static double backgroundVolume = 1;
    // 播放音频相关的函数
    public static String getAbsolutePath(String relativePath) {
       //如果为绝对路径，返回
        if(Paths.get(relativePath).isAbsolute()) {
            return relativePath;
        }
        String basePath=Paths.get("src","main","resources","sound").toString();
        return Paths.get(basePath,relativePath).toString();
    }
    public static void setBackgroundMusic(String sourceUrl) {
       try{

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
    public static void playSound(String sourceUrl, double volume) throws MalformedURLException {
        // 播放指定的音效，以给定的音量
        // volume 范围：0为静音，1为原音量
        try {
            Media media = new Media(new File(getAbsolutePath(sourceUrl)).toURI().toURL().toString());
            MediaPlayer soundPlayer = new MediaPlayer(media);

            soundPlayer.setVolume(Math.max(0,Math.min(1,volume)));
            soundPlayer.play();

            //播放完释放
            soundPlayer.setOnEndOfMedia(()->{
                soundPlayer.stop();
                soundPlayer.dispose();
            });
        }catch (MalformedURLException e){
            System.err.println("无效的音频文件路径:"+e.getMessage());
        }
    }
}

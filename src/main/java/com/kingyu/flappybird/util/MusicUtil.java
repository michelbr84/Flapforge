package com.kingyu.flappybird.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * 音乐工具类
 *
 * @author Kingyu
 * wav音频：使用JDK自带的javax.sound.sampled解码播放，JDK 8及以上版本均可用
 * （原先使用的sun.audio包在JDK 9中已被移除，会导致NoClassDefFoundError）
 * mp3音频：JDK没有提供支持，需要使用第三方的工具包
 */
public class MusicUtil {

    private static final String FLY_WAV_PATH = "resources/wav/fly.wav";
    private static final String CRASH_WAV_PATH = "resources/wav/crash.wav";
    private static final String SCORE_WAV_PATH = "resources/wav/score.wav";

    // 已解码到内存的音效，首次播放时装载，之后直接复用，避免每次播放都读文件
    private static Sound fly;
    private static Sound crash;
    private static Sound score;

    // 播放在单独的后台线程进行，不阻塞事件分发线程（绘制与按键响应），连续触发短音频不会使游戏卡顿
    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MusicUtil-player");
        t.setDaemon(true); // 守护线程，不阻止程序退出
        return t;
    });

    private MusicUtil() {
    } // 私有化，防止其他类实例化此类

    // wav播放
    public static void playFly() {
        PLAYER.execute(() -> {
            if (fly == null) {
                fly = Sound.load(FLY_WAV_PATH);
            }
            fly.play();
        });
    }

    public static void playCrash() {
        PLAYER.execute(() -> {
            if (crash == null) {
                crash = Sound.load(CRASH_WAV_PATH);
            }
            crash.play();
        });
    }

    public static void playScore() {
        PLAYER.execute(() -> {
            if (score == null) {
                score = Sound.load(SCORE_WAV_PATH);
            }
            score.play();
        });
    }

    /**
     * 一段已解码为PCM数据的音效。每次播放都新建一个Clip，因此快速连续触发的音效可以叠加播放
     */
    private static class Sound {
        private static boolean lineErrorReported; // 播放失败只提示一次，避免刷屏

        private final AudioFormat format;
        private final byte[] data; // 为null表示装载失败，播放时静默跳过

        private Sound(AudioFormat format, byte[] data) {
            this.format = format;
            this.data = data;
        }

        // 装载并解码wav文件
        static Sound load(String path) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(path))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return new Sound(in.getFormat(), out.toByteArray());
            } catch (UnsupportedAudioFileException | IOException e) {
                e.printStackTrace();
                return new Sound(null, null);
            }
        }

        // 播放一次
        void play() {
            if (data == null) {
                return;
            }
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(format, data, 0, data.length);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close(); // 播放完毕后释放音频线路
                    }
                });
                clip.start();
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                if (!lineErrorReported) { // 例如机器没有可用的音频设备
                    lineErrorReported = true;
                    e.printStackTrace();
                }
            }
        }
    }
}

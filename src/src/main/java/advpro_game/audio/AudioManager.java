package advpro_game.audio;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import java.net.URL;

public class AudioManager {
    private static MediaPlayer bgmPlayer;
    private static AudioClip bgmClip;


    // Play Background Music

    public static void playBGM(String resourcePath) {
        stopBGM();
        try {
            URL url = AudioManager.class.getResource(resourcePath);
            if (url == null) {
                System.err.println("[AudioManager] Missing BGM: " + resourcePath);
                return;
            }

            if (resourcePath.endsWith(".wav")) {
                // Use AudioClip for WAV (lightweight, easy looping)
                bgmClip = new AudioClip(url.toExternalForm());
                bgmClip.setCycleCount(AudioClip.INDEFINITE);
                bgmClip.setVolume(0.7);
                bgmClip.play();
                System.out.println("[AudioManager] WAV BGM playing: " + resourcePath);
            } else {
                // Use MediaPlayer for MP3
                Media media = new Media(url.toExternalForm());
                bgmPlayer = new MediaPlayer(media);
                bgmPlayer.setVolume(0.7);
                bgmPlayer.setOnEndOfMedia(() -> bgmPlayer.seek(Duration.ZERO)); // loop
                bgmPlayer.play();
                System.out.println("[AudioManager] MP3 BGM playing: " + resourcePath);
            }
        } catch (Exception e) {
            System.err.println("[AudioManager] Failed to play BGM: " + e.getMessage());
        }
    }


    //  Stop BGM

    public static void stopBGM() {
        if (bgmClip != null) {
            bgmClip.stop();
            bgmClip = null;
        }
        if (bgmPlayer != null) {
            bgmPlayer.stop();
            bgmPlayer.dispose();
            bgmPlayer = null;
        }
    }


    //  Play One-shot SFX

    public static void playSFX(String resourcePath) {
        try {
            URL url = AudioManager.class.getResource(resourcePath);
            if (url == null) {
                System.err.println("[AudioManager] Missing SFX: " + resourcePath);
                return;
            }

            // Use AudioClip for short effects (fast, no delay)
            AudioClip clip = new AudioClip(url.toExternalForm());
            clip.setVolume(0.75);
            clip.play();

            System.out.println("[AudioManager] SFX played: " + resourcePath);
        } catch (Exception e) {
            System.err.println("[AudioManager] Failed to play SFX: " + e.getMessage());
        }
    }
}

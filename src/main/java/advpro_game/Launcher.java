package advpro_game;

import advpro_game.audio.AudioManager;
import advpro_game.controller.DrawingLoop;
import advpro_game.controller.GameLoop;
import advpro_game.controller.StageManager;
import advpro_game.view.GameStage;
import advpro_game.view.MenuView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Entry point. Handles menu -> game session lifecycle, input wiring, and clean thread shutdown.
 */
public class Launcher extends Application {

    private static final Logger LOG = LogManager.getLogger(Launcher.class);

    private Stage primaryStage;
    private Thread gameThread, drawThread;
    private GameLoop gameLoop;
    private DrawingLoop drawingLoop;
    private StageManager stageManager; // new per session

    public static void main(String[] args) { launch(args); }

    // ---------- Logging ----------
    private static void setupLogging() {
        Configurator.setRootLevel(Level.INFO);
        Configurator.setLevel("advpro_game.view.GameStage", Level.INFO);
        Configurator.setLevel("advpro_game.model.GameCharacter", Level.TRACE);
        Configurator.setLevel("advpro_game.controller.StageManager", Level.INFO);
        LOG.debug("Logging configured");
    }

    @Override
    public void start(Stage stage) {
        setupLogging();
        this.primaryStage = stage;

        // Friendly uncaught handler
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            // Avoid pattern matching features; keep it Java 21-friendly
            if (cause != null && "advpro_game.GameException".equals(cause.getClass().getName())) {
                System.err.println("[Game Error] " + cause.getMessage());
            } else {
                System.err.println("[Uncaught] in " + t.getName());
                cause.printStackTrace();
            }
        });

        showMenu();
    }

    // ---------- Menu ----------
    /** Show main menu (Start / Exit). */
    private void showMenu() {
        // Optional: AudioManager.playBGM("/advpro_game/assets/bgm_menu.mp3");

        // Ensure previous session is fully stopped
        stopLoops();
        AudioManager.stopBGM();

        MenuView menu = new MenuView(this::startGame, Platform::exit);
        Scene menuScene = new Scene(menu, 800, 400);
        primaryStage.setTitle("Contre two one");
        primaryStage.setScene(menuScene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Clean close from menu as well
        primaryStage.setOnCloseRequest(e -> {
            stopLoops();
            AudioManager.stopBGM();
            Platform.exit();
        });
    }

    // ---------- Game session ----------
    /** Build and start a fresh game session. */
    private void startGame() {
        try {
            // Stop any menu BGM when entering gameplay
            AudioManager.stopBGM();

            // Safety: don’t double-run threads if coming from Retry quickly
            stopLoops();

            GameStage gameStage = new GameStage();
            Scene scene = new Scene(gameStage, GameStage.WIDTH, GameStage.HEIGHT);
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);

            // Scene-level input (keeps working even when a node steals focus)
            scene.addEventFilter(KeyEvent.KEY_PRESSED,  e -> gameStage.getKeys().add(e.getCode()));
            scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> gameStage.getKeys().remove(e.getCode()));
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED,  e -> gameStage.getKeys().add(e.getButton()));
            scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> gameStage.getKeys().remove(e.getButton()));
            // Click anywhere to restore keyboard focus to the game
            scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> gameStage.requestFocus());
            Platform.runLater(gameStage::requestFocus);

            // Create/attach StageManager AFTER scene is attached (FX timing safe)
            stageManager = new StageManager(gameStage);
            Platform.runLater(stageManager::start);

            // Wire Retry / Exit-to-Menu actions called by GameOverOverlay buttons
            gameStage.setOnRetry(() -> {
                // Rebuild a clean session
                stopLoops();
                Platform.runLater(this::startGame);
            });
            gameStage.setOnExitToMenu(() -> {
                stopLoops();
                Platform.runLater(this::showMenu);
            });

            // Start loops
            gameLoop = new GameLoop(gameStage);
            gameLoop.attachStageManager(stageManager);
            drawingLoop = new DrawingLoop(gameStage);

            gameThread = new Thread(gameLoop, "GameLoopThread");
            drawThread = new Thread(drawingLoop, "DrawingLoopThread");
            gameThread.setDaemon(true);
            drawThread.setDaemon(true);
            gameThread.start();
            drawThread.start();

            // Window close → clean shutdown
            primaryStage.setOnCloseRequest(e -> {
                stopLoops();
                AudioManager.stopBGM();
                Platform.exit();
            });

        } catch (Throwable ex) {
            ex.printStackTrace();
            // If you have advpro_game.GameException defined elsewhere, this rethrow helps the handler above
            throw new GameException("Failed to start the game.", ex);
        }
    }

    /** Stop threads and clear references safely. Idempotent. */
    private void stopLoops() {
        try { if (gameLoop != null) gameLoop.stop(); } catch (Throwable ignore) {}
        try { if (drawingLoop != null) drawingLoop.stop(); } catch (Throwable ignore) {}

        try { if (gameThread != null && gameThread.isAlive()) gameThread.join(200); } catch (InterruptedException ignore) {}
        try { if (drawThread != null && drawThread.isAlive()) drawThread.join(200); } catch (InterruptedException ignore) {}

        gameLoop = null;
        drawingLoop = null;
        gameThread = null;
        drawThread = null;

        // Stop any playing music
        AudioManager.stopBGM();

        // Let GC reclaim previous StageManager (only lightweight timers inside)
        stageManager = null;
    }
}

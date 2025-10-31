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

public class Launcher extends Application {

    private Stage primaryStage;
    private Thread gameThread, drawThread;
    private GameLoop gameLoop;
    private DrawingLoop drawingLoop;
    private StageManager stageManager; // created per game session

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Friendly uncaught error handler (special-case GameException)
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            if (cause instanceof GameException) {
                System.err.println("[Game Error] " + cause.getMessage());
            } else {
                System.err.println("[Uncaught] in " + t.getName());
                cause.printStackTrace();
            }
        });

        showMenu();
    }

    /** Show main menu (Start / Exit). */
    private void showMenu() {
        // Optional menu BGM
        // AudioManager.playBGM("/advpro_game/assets/bgm_menu.mp3");

        MenuView menu = new MenuView(this::startGame, Platform::exit);
        Scene menuScene = new Scene(menu, 800, 400);
        primaryStage.setTitle("Contra Clone");
        primaryStage.setScene(menuScene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Stop any leftover game audio just in case
        AudioManager.stopBGM();

        // Ensure no dangling threads from a previous session
        stopLoops();
    }

    /** Build and start a fresh game session. */
    private void startGame() {
        try {
            // Stop any menu BGM when entering gameplay
            AudioManager.stopBGM();

            GameStage gameStage = new GameStage();
            Scene scene = new Scene(gameStage, GameStage.WIDTH, GameStage.HEIGHT);
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);

            // ---- SCENE-LEVEL INPUT (reliable focus) ----
            scene.addEventFilter(KeyEvent.KEY_PRESSED,  e -> gameStage.getKeys().add(e.getCode()));
            scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> gameStage.getKeys().remove(e.getCode()));
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED,  e -> gameStage.getKeys().add(e.getButton()));
            scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> gameStage.getKeys().remove(e.getButton()));

            // Keep keyboard focus on the game whenever you click inside the window
            scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> gameStage.requestFocus());

            // Ensure focus lands on the game at start
            Platform.runLater(gameStage::requestFocus);

            // Create StageManager AFTER scene is attached (FX timing safe)
            stageManager = new StageManager(gameStage);
            Platform.runLater(stageManager::start);

            // Wire Retry / Exit-to-Menu actions from in-game UI
            gameStage.setOnRetry(() -> {
                stopLoops();
                Platform.runLater(this::startGame);  // rebuild a fresh session
            });
            gameStage.setOnExitToMenu(() -> {
                stopLoops();
                Platform.runLater(this::showMenu);
            });

            // --- Start loops ---
            gameLoop = new GameLoop(gameStage);            // single-arg ctor
            gameLoop.attachStageManager(stageManager);     // call once
            drawingLoop = new DrawingLoop(gameStage);

            gameThread = new Thread(gameLoop, "GameLoopThread");
            drawThread = new Thread(drawingLoop, "DrawingLoopThread");
            gameThread.setDaemon(true);
            drawThread.setDaemon(true);
            gameThread.start();
            drawThread.start();

            // On window close: clean shutdown
            primaryStage.setOnCloseRequest(e -> {
                stopLoops();
                AudioManager.stopBGM();
                Platform.exit();
            });

        } catch (Throwable ex) {
            // Print stack and rethrow as GameException for the handler above
            ex.printStackTrace();
            throw new GameException("Failed to start the game.", ex);
        }
    }

    /** Stop threads and clear references safely. */
    private void stopLoops() {
        try { if (gameLoop != null) gameLoop.stop(); } catch (Throwable ignore) {}
        try { if (drawingLoop != null) drawingLoop.stop(); } catch (Throwable ignore) {}
        try { if (gameThread != null) gameThread.join(150); } catch (InterruptedException ignore) {}
        try { if (drawThread != null) drawThread.join(150); } catch (InterruptedException ignore) {}

        gameLoop = null;
        drawingLoop = null;
        gameThread = null;
        drawThread = null;

        // Stop any playing music
        AudioManager.stopBGM();

        // Let GC reclaim previous StageManager (only short PauseTransitions inside)
        stageManager = null;
    }
}

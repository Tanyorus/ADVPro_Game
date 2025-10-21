package advpro_game;

import advpro_game.controller.DrawingLoop;
import advpro_game.controller.GameLoop;
import advpro_game.view.GameStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class Launcher extends Application {

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("[Uncaught] in " + t.getName());
            e.printStackTrace();
        });

        GameStage root = new GameStage();
        Scene scene = new Scene(root, GameStage.WIDTH, GameStage.HEIGHT);

        // ---- SCENE-LEVEL INPUT (reliable focus) ----
        scene.addEventFilter(KeyEvent.KEY_PRESSED,  e -> root.getKeys().add(e.getCode()));
        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> root.getKeys().remove(e.getCode()));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED,  e -> root.getKeys().add(e.getButton()));
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> root.getKeys().remove(e.getButton()));

        // Keep keyboard focus on the game whenever you click inside the window
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> root.requestFocus());

        primaryStage.setTitle("Contre tro to!");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // make sure focus starts on the game
        root.requestFocus();

        // Loops
        GameLoop gameLoop = new GameLoop(root);
        DrawingLoop drawingLoop = new DrawingLoop(root);
        Thread gameThread = new Thread(gameLoop, "GameLoopThread");
        Thread drawThread = new Thread(drawingLoop, "DrawingLoopThread");
        gameThread.setDaemon(true);
        drawThread.setDaemon(true);
        gameThread.start();
        drawThread.start();

        primaryStage.setOnCloseRequest(evt -> {
            try { gameLoop.stop(); } catch (Throwable ignore) {}
            try { drawingLoop.stop(); } catch (Throwable ignore) {}
            try { gameThread.join(150); } catch (InterruptedException ignore) {}
            try { drawThread.join(150); } catch (InterruptedException ignore) {}
            Platform.exit();
        });
    }
}

package advpro_game;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import advpro_game.controller.DrawingLoop;
import advpro_game.controller.GameLoop;
import advpro_game.view.GameStage;

public class Launcher extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Initialize stage and loops
        GameStage gameStage = new GameStage();
        GameLoop gameLoop = new GameLoop(gameStage);
        DrawingLoop drawingLoop = new DrawingLoop(gameStage);

        // Scene setup
        Scene scene = new Scene(gameStage, GameStage.WIDTH, GameStage.HEIGHT);
        scene.setOnKeyPressed(e -> gameStage.getKeys().add(e.getCode()));
        scene.setOnKeyReleased(e -> gameStage.getKeys().remove(e.getCode()));

        // Stage setup
        primaryStage.setTitle("Contre tro to!");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Graceful exit
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            Platform.exit();
        });

        // Start loops
        Thread gameThread = new Thread(gameLoop, "GameLoopThread");
        Thread drawThread = new Thread(drawingLoop, "DrawingLoopThread");
        gameThread.setDaemon(true);
        drawThread.setDaemon(true);
        gameThread.start();
        drawThread.start();
    }
}

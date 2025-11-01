package advpro_game.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Retro-styled translucent “Game Over” overlay with Retry / Exit buttons. */
public class GameOverOverlay extends StackPane {

    public GameOverOverlay(Runnable onRetry, Runnable onExitToMenu, double width, double height) {
        setPickOnBounds(true);
        setPrefSize(width, height);

        // Dim veil
        Rectangle veil = new Rectangle();
        veil.widthProperty().bind(widthProperty());
        veil.heightProperty().bind(heightProperty());
        veil.setFill(Color.color(0, 0, 0, 0.55));

        // Panel content
        Label title = new Label("GAME OVER");
        title.setTextFill(Color.web("#f5f5f7"));
        title.setFont(Font.font("Consolas", FontWeight.EXTRA_BOLD, 40));
        title.setStyle("-fx-effect: dropshadow(gaussian, rgba(255,0,80,0.65), 24, 0.35, 0, 0);");

        Button retry = bigButton("RETRY", "#00f0b5", "#06281f");
        retry.setOnAction(e -> { if (onRetry != null) onRetry.run(); });

        Button exit = bigButton("EXIT TO MENU", "#ff6b6b", "#2a0f10");
        exit.setOnAction(e -> { if (onExitToMenu != null) onExitToMenu.run(); });

        VBox box = new VBox(14, title, retry, exit);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: rgba(17,17,17,0.82); -fx-background-radius: 16;");
        StackPane.setAlignment(box, Pos.CENTER);

        getChildren().setAll(veil, box);
    }

    private Button bigButton(String text, String glowHex, String hoverBgHex) {
        Button b = new Button(text);
        b.setPrefWidth(240);
        b.setPrefHeight(46);
        b.setTextFill(Color.WHITE);
        b.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        b.setStyle(baseButtonStyle(glowHex));
        b.hoverProperty().addListener((ob, was, is) -> {
            if (is) b.setStyle(baseButtonStyle(glowHex) +
                    "-fx-background-color: linear-gradient(to bottom, " + hoverBgHex + ", #101010);");
            else    b.setStyle(baseButtonStyle(glowHex));
        });
        b.setOnMousePressed(e -> b.setOpacity(0.85));
        b.setOnMouseReleased(e -> b.setOpacity(1.0));
        return b;
    }

    private String baseButtonStyle(String glowHex) {
        return String.join("",
                "-fx-background-color: linear-gradient(to bottom, #141414, #0c0c0c);",
                "-fx-background-radius: 12;",
                "-fx-border-color: rgba(255,255,255,0.12);",
                "-fx-border-radius: 12;",
                "-fx-border-width: 1;",
                "-fx-effect: dropshadow(gaussian, ", glowHex, ", 18, 0.25, 0, 0);",
                "-fx-padding: 6 14 6 14;"
        );
    }
}
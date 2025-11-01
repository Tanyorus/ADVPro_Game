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

/** Retro-styled mission complete / ending screen with “Back to Menu”. */
public class EndingView extends StackPane {

    public EndingView(Runnable onBackToMenu, double width, double height) {
        setPickOnBounds(true);
        setPrefSize(width, height);

        // Deep background gradient
        Rectangle bg = new Rectangle();
        bg.widthProperty().bind(widthProperty());
        bg.heightProperty().bind(heightProperty());
        bg.setFill(javafx.scene.paint.LinearGradient.valueOf(
                "to bottom, #0f0f1a 0%, #0a0a14 45%, #000000 100%"
        ));

        Label title = new Label("MISSION COMPLETE");
        title.setTextFill(Color.web("#e7ffe8"));
        title.setFont(Font.font("Consolas", FontWeight.EXTRA_BOLD, 36));
        title.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,255,140,0.6), 22, 0.35, 0, 0);");

        Label sub = new Label("Thanks for playing!");
        sub.setTextFill(Color.web("#9af5ff"));
        sub.setStyle("-fx-font-size: 16px; -fx-opacity: 0.9;");

        Button toMenu = bigButton("BACK TO MENU", "#00f0b5", "#06281f");
        toMenu.setOnAction(e -> { if (onBackToMenu != null) onBackToMenu.run(); });

        VBox box = new VBox(14, title, sub, toMenu);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: rgba(17,17,17,0.82); -fx-background-radius: 16;");

        getChildren().setAll(bg, box);
    }

    private Button bigButton(String text, String glowHex, String hoverBgHex) {
        Button b = new Button(text);
        b.setPrefWidth(260);
        b.setPrefHeight(50);
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

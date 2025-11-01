package advpro_game.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.BlendMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Retro-style start menu with big buttons: "Start Game" and "Exit".
 * Constructor accepts two Runnables for actions.
 *
 * Usage:
 *   new MenuView(() -> startGame(), () -> Platform.exit());
 */
public class MenuView extends StackPane {

    private final Runnable onStart;
    private final Runnable onExit;

    public MenuView(Runnable onStart, Runnable onExit) {
        this.onStart = (onStart != null ? onStart : () -> {});
        this.onExit  = (onExit  != null ? onExit  : () -> {});
        setPrefSize(800, 400);
        buildUI();
    }

    private void buildUI() {
        // --- Background gradient (retro deep purple → black) ---
        Rectangle bg = new Rectangle();
        bg.widthProperty().bind(widthProperty());
        bg.heightProperty().bind(heightProperty());
        bg.setFill(javafx.scene.paint.LinearGradient.valueOf(
                "to bottom, #0f0f1a 0%, #141325 30%, #0b0a16 60%, #000000 100%"
        ));

        // --- Subtle scanlines overlay ---
        Rectangle scan = new Rectangle();
        scan.widthProperty().bind(widthProperty());
        scan.heightProperty().bind(heightProperty());
        scan.setFill(javafx.scene.paint.LinearGradient.valueOf(
                "to bottom, rgba(255,255,255,0.06) 0%, rgba(255,255,255,0.00) 2%, rgba(255,255,255,0.06) 4%, rgba(255,255,255,0.00) 6%"
        ));
        scan.setBlendMode(BlendMode.OVERLAY);
        scan.setOpacity(0.25);

        // --- Vignette ---
        Rectangle vignette = new Rectangle();
        vignette.widthProperty().bind(widthProperty());
        vignette.heightProperty().bind(heightProperty());
        vignette.setFill(Color.TRANSPARENT);
        vignette.setMouseTransparent(true);
        vignette.setStyle(
                "-fx-background-color: transparent;" // placeholder to allow effect below
        );
        vignette.setEffect(new javafx.scene.effect.InnerShadow(80, Color.color(0,0,0,0.85)));

        // --- Title ---
        Label title = new Label("CONTRE • TWO • ONE");
        title.setTextFill(Color.web("#f5f5f7"));
        title.setFont(Font.font("Consolas", FontWeight.EXTRA_BOLD, 40));
        title.setStyle(
                "-fx-effect: dropshadow(gaussian, rgba(0,255,180,0.6), 20, 0.3, 0, 0);" +
                        "-fx-letter-spacing: 1px;"
        );

        // --- Subtitle / flavor text ---
        Label subtitle = new Label("PRESS ENTER TO START");
        subtitle.setTextFill(Color.web("#87f7ff"));
        subtitle.setFont(Font.font("Consolas", FontWeight.SEMI_BOLD, 16));
        subtitle.setOpacity(0.85);

        // Blink animation for subtitle
        javafx.animation.FadeTransition blink = new javafx.animation.FadeTransition(javafx.util.Duration.millis(900), subtitle);
        blink.setFromValue(1.0);
        blink.setToValue(0.25);
        blink.setAutoReverse(true);
        blink.setCycleCount(javafx.animation.Animation.INDEFINITE);
        blink.play();

        // --- Buttons ---
        Button startBtn = bigButton("START GAME", "#00f0b5", "#06281f");
        startBtn.setOnAction(e -> onStart.run());

        Button exitBtn = bigButton("EXIT", "#ff6b6b", "#2a0f10");
        exitBtn.setOnAction(e -> onExit.run());

        // Keyboard shortcuts
        setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER, SPACE -> onStart.run();
                case ESCAPE -> onExit.run();
            }
        });

        // Layout stack
        VBox centerBox = new VBox(14, title, subtitle, spacer(10), startBtn, exitBtn);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(24));
        centerBox.setFillWidth(false);

        // Subtle breathing scale on the title for retro vibe
        javafx.animation.ScaleTransition breathe = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(2400), title);
        breathe.setFromX(1.0); breathe.setFromY(1.0);
        breathe.setToX(1.03); breathe.setToY(1.03);
        breathe.setCycleCount(javafx.animation.Animation.INDEFINITE);
        breathe.setAutoReverse(true);
        breathe.play();

        getChildren().setAll(bg, scan, vignette, centerBox);

        // Request focus so Enter/Escape work immediately
        sceneProperty().addListener((obs, o, n) -> { if (n != null) requestFocus(); });
        requestFocus();
    }

    private Button bigButton(String text, String glowHex, String hoverBgHex) {
        Button b = new Button(text);
        b.setPrefWidth(240);
        b.setPrefHeight(46);
        b.setTextFill(Color.WHITE);
        b.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        b.setStyle(baseButtonStyle(glowHex));

        // Hover + press effects
        b.hoverProperty().addListener((ob, was, is) -> {
            if (is) {
                b.setStyle(baseButtonStyle(glowHex) +
                        "-fx-background-color: linear-gradient(to bottom, " + hoverBgHex + ", #101010);");
            } else {
                b.setStyle(baseButtonStyle(glowHex));
            }
        });
        b.setOnMousePressed(e -> b.setOpacity(0.8));
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
                "-fx-effect: dropshadow(gaussian, ", glowHex, " , 18, 0.25, 0, 0);",
                "-fx-padding: 6 14 6 14;"
        );
    }

    private Node spacer(double h) {
        Rectangle r = new Rectangle(1, h);
        r.setOpacity(0);
        return r;
    }
}

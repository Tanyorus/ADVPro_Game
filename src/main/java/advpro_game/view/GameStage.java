package advpro_game.view;

import advpro_game.Launcher;
import advpro_game.model.Bullet;
import advpro_game.model.Enemy;
import advpro_game.model.GameCharacter;
import advpro_game.model.Keys;
import advpro_game.model.Platform;
import javafx.geometry.Pos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GameStage extends Pane {
    public static final int WIDTH = 800;
    public static final int HEIGHT = 400;
    public static final int GROUND = 350;

    // ---- Layers ----
    private final Pane backgroundLayer = new Pane();
    private final Pane worldLayer      = new Pane();
    private final Pane enemyLayer      = new Pane();
    private final Pane bulletLayer     = new Pane();
    private final Pane hudLayer        = new Pane();
    private final Pane overlayLayer    = new Pane();
    private final javafx.scene.canvas.Canvas debugCanvas = new javafx.scene.canvas.Canvas(WIDTH, HEIGHT);

    // Scene content
    private Image backgroundImg;
    private final List<GameCharacter> gameCharacterList = new ArrayList<>();
    private List<Score> scoreList = new ArrayList<>();

    // World data
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();

    // Input
    private final Keys keys = new Keys();
    private MouseButton mouseButton;

    // HUD: lives + slow-mo bar
    private Image lifeIconImg;
    private final HBox livesBox = new HBox(6);
    private final Rectangle slowBg = new Rectangle(120, 10, Color.color(0,0,0,0.35));
    private final Rectangle slowFill = new Rectangle(0, 10, Color.CORNFLOWERBLUE);

    // Slow-time state
    private double slowMoEnergy = 1.0;        // 0..1
    private final double slowMoDrainRate = 0.35;    // per second
    private final double slowMoRechargeRate = 0.20; // per second
    private final double slowMoScale = 0.55;        // time scale when active
    private boolean slowMoActive = false;

    public GameStage() {
        // ---- Background ----
        try (InputStream s = Launcher.class.getResourceAsStream("/advpro_game/assets/Stage1.png")) {
            if (s != null) backgroundImg = new Image(s);
        } catch (Exception ignored) {}
        ImageView bgIV = new ImageView(backgroundImg);
        bgIV.setFitHeight(HEIGHT); bgIV.setFitWidth(WIDTH);
        bgIV.setPreserveRatio(false);
        bgIV.setMouseTransparent(true);
        bgIV.setCache(true);
        backgroundLayer.getChildren().add(bgIV);

        // Clips
        worldLayer.setClip(new Rectangle(WIDTH, HEIGHT));
        enemyLayer.setClip(new Rectangle(WIDTH, HEIGHT));
        bulletLayer.setClip(new Rectangle(WIDTH, HEIGHT));
        backgroundLayer.setMouseTransparent(true);
        overlayLayer.setMouseTransparent(true);
        debugCanvas.setMouseTransparent(true);

        // ---- Platforms ----
        platforms.add(Platform.solid(0, GROUND, WIDTH, 100)); // ground
        platforms.add(Platform.oneWay(10, 270, 400, 30));     // ledge 1
        platforms.add(Platform.oneWay(420, 200, 200, 10));    // ledge 2

        // ---- Character ----
        GameCharacter p1 = new GameCharacter(
                0, 30, 30,
                "/advpro_game/assets/Character.png",
                32, 16, 2,
                65, 65,
                KeyCode.A, KeyCode.D,
                KeyCode.W, KeyCode.S,
                MouseButton.PRIMARY
        );
        gameCharacterList.add(p1);
        worldLayer.getChildren().add(p1);

        // ---- Enemy sample ----
        // (keep your own spawns as needed)

        // ---- Scores ----
        Score s1 = new Score(16, 16);
        scoreList.add(s1);

        // ---- Lives HUD ----
        livesBox.setAlignment(Pos.TOP_LEFT);
        livesBox.setLayoutX(16); livesBox.setLayoutY(32);
        livesBox.setMouseTransparent(true);
        try (InputStream in = Launcher.class.getResourceAsStream("/advpro_game/assets/life.png")) {
            if (in != null) lifeIconImg = new Image(in);
        } catch (Exception ignored) {}
        updateLivesHUD(getPlayer().getLives()); // draw 3 icons by default

        // ---- Slow-mo bar ----
        slowBg.setArcWidth(6); slowBg.setArcHeight(6);
        slowFill.setArcWidth(6); slowFill.setArcHeight(6);
        slowBg.setLayoutX(16); slowBg.setLayoutY(56);
        slowFill.setLayoutX(16); slowFill.setLayoutY(56);
        slowFill.setWidth(slowBg.getWidth() * slowMoEnergy);

        // ---- Z-order ----
        overlayLayer.getChildren().clear();
        hudLayer.getChildren().addAll(s1, livesBox, slowBg, slowFill);
        getChildren().setAll(backgroundLayer, worldLayer, enemyLayer, bulletLayer, hudLayer, overlayLayer, debugCanvas);

        // ---- Input wiring (Pane-level; Launcher also wires Scene-level) ----
        setFocusTraversable(true);
        sceneProperty().addListener((obs, o, n) -> { if (n != null) requestFocus(); });
        setOnKeyPressed(e -> keys.add(e.getCode()));
        setOnKeyReleased(e -> keys.remove(e.getCode()));
        setOnMousePressed(e -> keys.add(e.getButton()));
        setOnMouseReleased(e -> keys.remove(e.getButton()));
        requestFocus();
    }

    // ---------- Slow-time ----------
    public void tickSlowMo(boolean wantSlow, double dtSeconds) {
        if (wantSlow && slowMoEnergy > 0.0) {
            slowMoActive = true;
            slowMoEnergy -= slowMoDrainRate * dtSeconds;
        } else {
            slowMoActive = false;
            slowMoEnergy += slowMoRechargeRate * dtSeconds;
        }
        slowMoEnergy = Math.max(0.0, Math.min(1.0, slowMoEnergy));

        // Update bar on FX thread
        javafx.application.Platform.runLater(() -> slowFill.setWidth(slowBg.getWidth() * slowMoEnergy));
    }
    public double getTimeScale() { return slowMoActive ? slowMoScale : 1.0; }

    // ---------- HUD helpers ----------
    public void updateLivesHUD(int lives) {
        javafx.application.Platform.runLater(() -> {
            livesBox.getChildren().clear();
            for (int i = 0; i < lives; i++) {
                if (lifeIconImg != null) {
                    ImageView iv = new ImageView(lifeIconImg);
                    iv.setFitWidth(18); iv.setFitHeight(18);
                    iv.setPreserveRatio(true);
                    livesBox.getChildren().add(iv);
                } else {
                    Rectangle r = new Rectangle(18, 18, Color.GOLD);
                    r.setArcWidth(6); r.setArcHeight(6);
                    livesBox.getChildren().add(r);
                }
            }
        });
    }

    // ---------- Background switching ----------
    public void setStageBackground(int index) {
        String path = switch (index) {
            case 2 -> "/advpro_game/assets/Stage2.png";
            case 3 -> "/advpro_game/assets/Stage3.png";
            default -> "/advpro_game/assets/Stage1.png";
        };
        try (InputStream s = Launcher.class.getResourceAsStream(path)) {
            if (s != null) {
                Image newBg = new Image(s);
                backgroundImg = newBg;
                ((ImageView)backgroundLayer.getChildren().get(0)).setImage(newBg);
            }
        } catch (Exception ignored) {}
    }

    // ---------- API for loops ----------
    public List<Platform> getPlatforms() { return platforms; }
    public List<Bullet> getBullets() { return bullets; }
    public List<Enemy> getEnemies() { return enemies; }
    public GraphicsContext getDebugGC() { return debugCanvas.getGraphicsContext2D(); }
    public List<Score> getScoreList() { return scoreList; }
    public void setScoreList(List<Score> list) { scoreList = list; hudLayer.getChildren().setAll(list); }
    public Pane getDBoverlay() { return overlayLayer; }
    public List<GameCharacter> getGameCharacterList() { return gameCharacterList; }
    public GameCharacter getPlayer() { return gameCharacterList.get(0); }
    public Keys getKeys() { return keys; }
    public MouseButton getMouseButton() { return mouseButton; }

    // Layered node management (FX-thread safe)
    public void addBullet(Bullet b) {
        bullets.add(b);
        javafx.application.Platform.runLater(() -> bulletLayer.getChildren().add(b.getNode()));
    }
    public void removeBullet(Bullet b) {
        javafx.application.Platform.runLater(() -> bulletLayer.getChildren().remove(b.getNode()));
        bullets.remove(b);
    }
    public void addEnemy(Enemy e) {
        enemies.add(e);
        javafx.application.Platform.runLater(() -> enemyLayer.getChildren().add(e.getNode()));
    }
    public void removeEnemy(Enemy e) {
        javafx.application.Platform.runLater(() -> enemyLayer.getChildren().remove(e.getNode()));
        enemies.remove(e);
    }
    // FX-thread safe small impact flash on the overlay layer
    public void showHitFlash(double x, double y) {
        javafx.application.Platform.runLater(() -> {
            var dot = new javafx.scene.shape.Circle(8, javafx.scene.paint.Color.ORANGE);
            dot.setTranslateX(x);
            dot.setTranslateY(y);
            getDBoverlay().getChildren().add(dot);

            var fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), dot);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(ev -> {
                // remove on the *next* pulse to avoid bounds-update race
                javafx.application.Platform.runLater(() ->
                        getDBoverlay().getChildren().remove(dot)
                );
            });
            fade.play();
        });
    }

}

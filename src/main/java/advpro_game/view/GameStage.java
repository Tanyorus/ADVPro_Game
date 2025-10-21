package advpro_game.view;

import advpro_game.Launcher;
import advpro_game.model.Bullet;
import advpro_game.model.Enemy;
import advpro_game.model.GameCharacter;
import advpro_game.model.Keys;
import advpro_game.model.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GameStage extends Pane {
    public static final int WIDTH = 800;
    public static final int HEIGHT = 400;
    public static final int GROUND = 350;

    // ---- Layers (fixed Z-order) ----
    private final Pane backgroundLayer = new Pane();
    private final Pane worldLayer      = new Pane(); // players / props
    private final Pane enemyLayer      = new Pane();
    private final Pane bulletLayer     = new Pane();
    private final Pane hudLayer        = new Pane(); // scores, UI
    private final Pane overlayLayer    = new Pane(); // debug boxes, guides
    private final javafx.scene.canvas.Canvas debugCanvas = new javafx.scene.canvas.Canvas(WIDTH, HEIGHT);

    // Scene content
    private Image gameStageImg;
    private final List<GameCharacter> gameCharacterList;
    private List<Score> scoreList;

    // World objects (data lists)
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();

    // Input
    private final Keys keys = new Keys();
    private MouseButton mouseButton; // kept for compatibility

    public GameStage() {
        // ---- Lists ----
        gameCharacterList = new ArrayList<>();
        scoreList = new ArrayList<>();

        // ---- Background ----
        try (InputStream stream = Launcher.class.getResourceAsStream("/advpro_game/assets/Stage1.png")) {
            if (stream != null) gameStageImg = new Image(stream);
        } catch (Exception ignored) {}
        ImageView backgroundImg = new ImageView(gameStageImg);
        backgroundImg.setFitHeight(HEIGHT);
        backgroundImg.setFitWidth(WIDTH);
        backgroundImg.setPreserveRatio(false);
        backgroundImg.setMouseTransparent(true);
        backgroundImg.setCache(true);
        backgroundLayer.getChildren().add(backgroundImg);

        // ---- (Optional) Clip per layer (must be distinct Rectangle per layer) ----
        worldLayer.setClip(new Rectangle(WIDTH, HEIGHT));
        enemyLayer.setClip(new Rectangle(WIDTH, HEIGHT));
        bulletLayer.setClip(new Rectangle(WIDTH, HEIGHT));
        // No clip for HUD/overlay/background

        // Non-interactive layers ignore mouse
        backgroundLayer.setMouseTransparent(true);
        overlayLayer.setMouseTransparent(true);
        debugCanvas.setMouseTransparent(true);

        // ---- Platforms ----
        platforms.add(Platform.solid(0, GROUND, WIDTH, 100)); // ground
        platforms.add(Platform.oneWay(10, 270, 400, 30));     // ledge 1
        platforms.add(Platform.oneWay(420, 200, 200, 10));    // ledge 2

        // ---- Characters ----
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

        // ---- Enemies (example) ----
        Enemy e = new Enemy(600, GROUND - 50, 30, 50);
        enemies.add(e);
        enemyLayer.getChildren().add(e.getNode());

        // ---- Scores (HUD) ----
        Score s1 = new Score(16, 16); // top-left; avoid overlapping player
        scoreList.add(s1);
        hudLayer.getChildren().add(s1);

        // ---- Final Z-order ----
        overlayLayer.getChildren().clear();
        getChildren().setAll(
                backgroundLayer,  // 0 back
                worldLayer,       // 1 player
                enemyLayer,       // 2 enemies
                bulletLayer,      // 3 bullets
                hudLayer,         // 4 HUD
                overlayLayer,     // 5 overlay
                debugCanvas       // 6 top-most for debug drawing
        );

        // ---- Input wiring ----
        setFocusTraversable(true);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) requestFocus();
        });
        setOnKeyPressed(e2 -> keys.add(e2.getCode()));
        setOnKeyReleased(e2 -> keys.remove(e2.getCode()));
        setOnMousePressed(e2 -> keys.add(e2.getButton()));
        setOnMouseReleased(e2 -> keys.remove(e2.getButton()));
        requestFocus();
    }

    // ---------- API (used by loops) ----------
    public List<Platform> getPlatforms() { return platforms; }
    public List<Bullet> getBullets() { return bullets; }
    public List<Enemy> getEnemies() { return enemies; }

    public GraphicsContext getDebugGC() { return debugCanvas.getGraphicsContext2D(); }

    public List<Score> getScoreList() { return scoreList; }
    public void setScoreList(List<Score> scoreList) {
        this.scoreList = scoreList;
        hudLayer.getChildren().setAll(scoreList); // keep scores on HUD layer
    }

    public Pane getDBoverlay() { return overlayLayer; } // compatibility name
    public List<GameCharacter> getGameCharacterList() { return gameCharacterList; }

    public Keys getKeys() { return keys; }
    public MouseButton getMouseButton() { return mouseButton; }

    // ---- Layered helpers (use these; do NOT add nodes directly to root) ----
    public void addBullet(Bullet b) {
        bullets.add(b);
        bulletLayer.getChildren().add(b.getNode());
    }
    public void removeBullet(Bullet b) {
        bulletLayer.getChildren().remove(b.getNode());
        bullets.remove(b);
    }
    public void addEnemy(Enemy e) {
        enemies.add(e);
        enemyLayer.getChildren().add(e.getNode());
    }
    public void removeEnemy(Enemy e) {
        enemyLayer.getChildren().remove(e.getNode());
        enemies.remove(e);
    }
}

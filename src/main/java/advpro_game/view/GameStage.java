package advpro_game.view;

import advpro_game.Launcher;
import advpro_game.audio.AudioManager;
import advpro_game.model.*;
import advpro_game.model.Platform;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
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
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

public class GameStage extends Pane {
    // ---- Dimensions / ground ----
    public static final int WIDTH  = 800;
    public static final int HEIGHT = 400;
    public static final int GROUND = 350; // unified ground level

    // Mouse tracking for snapped aim
    private volatile double mouseX = WIDTH * 0.5, mouseY = HEIGHT * 0.5;
    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }


    private javafx.scene.Node activeUltNode = null;
    // If StageManager fails to spawn, fallback to default minions
    private boolean autoFallbackToSafetyMinions = true;

    // -------- Deferred scene-graph mutations while worldReady == false --------
    private final java.util.List<Runnable> deferredOps = new java.util.ArrayList<>();
    private void runOrDefer(Runnable r) {
        if (r == null) return;
        if (worldReady) {
            Ui.later(r); // ALWAYS next pulse to avoid layout-time mutations
        } else {
            synchronized (deferredOps) { deferredOps.add(r); }
        }
    }
    private void flushDeferredOps() {
        List<Runnable> toRun;
        synchronized (deferredOps) {
            toRun = new ArrayList<>(deferredOps);
            deferredOps.clear();
        }
        for (Runnable op : toRun) {
            try { Ui.later(op); } catch (Throwable t) { t.printStackTrace(); }
        }
    }
    /** Re-enable world mutations now and flush any deferred ops safely. */
    private void rearmWorldReadySoon() {
        worldReady = true; // mark unlocked
        flushDeferredOps();
    }

    // ---- Layers (use Group to avoid Region layout churn) ----
    private final Group backgroundLayer = new Group();
    private final Group worldLayer      = new Group();   // player / static world
    private final Group enemyLayer      = new Group();   // enemies (volatile)
    private final Group bulletLayer     = new Group();   // bullets (volatile)
    private final Pane  hudLayer        = new Pane();    // HUD (needs layout)
    private final Group overlayLayer    = new Group();   // overlays (modal)
    private final javafx.scene.canvas.Canvas debugCanvas =
            new javafx.scene.canvas.Canvas(WIDTH, HEIGHT);

    // --- detach/attach guards for volatile layers (index-free) ---
    private boolean volatileLayersAttached = true;
    private void detachVolatileLayers() {
        Ui.later(() -> {
            if (!volatileLayersAttached) return;
            enemyLayer.setVisible(false);
            bulletLayer.setVisible(false);
            volatileLayersAttached = false;
        });
    }
    private void attachVolatileLayers() {
        Ui.later(() -> {
            if (volatileLayersAttached) return;
            enemyLayer.setVisible(true);
            bulletLayer.setVisible(true);
            volatileLayersAttached = true;
        });
    }

    // ----- generation & safety spawn controls -----
    private int generation = 0;

    // OFF by default so StageManager fully controls spawns:
    private boolean allowSafetyMinions = false;
    public void setAllowSafetyMinions(boolean allow) { this.allowSafetyMinions = allow; }

    // -------- world lock to avoid scene-graph mutations mid-reset --------
    private volatile boolean worldReady = true;
    public boolean isWorldReady() { return worldReady; }

    // Static helper for Ui.safe* (works with any node)
    public static boolean isWorldReady(javafx.scene.Node anyNodeInGameStage) {
        if (anyNodeInGameStage == null) return true;
        javafx.scene.Parent p = anyNodeInGameStage.getParent();
        while (p != null && !(p instanceof advpro_game.view.GameStage)) p = p.getParent();
        if (p instanceof advpro_game.view.GameStage gs) return gs.worldReady;
        return true; // if we can't find the GameStage, don't block
    }

    // ---- Scene content ----
    private Image backgroundImg;
    private ImageView bgIV; // keep handle (don’t rely on getChildren().get(0))
    private final List<GameCharacter> gameCharacterList = new ArrayList<>();
    private List<Score> scoreList = new ArrayList<>();

    // ---- World data (logical) ----
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Bullet> bullets     = new ArrayList<>();
    private final List<Enemy> enemies      = new ArrayList<>();

    // ---- Input state ----
    private final Keys keys = new Keys();
    private MouseButton mouseButton; // kept for API compatibility (not used directly)

    // ---- Stage tracking ----
    private int currentStage = 1; // default
    public int getCurrentStage() { return currentStage; }

    // ---- Base input handlers (restorable after overlays) ----
    // (PATCHED) E key triggers ultimate laser here
    private final javafx.event.EventHandler<javafx.scene.input.KeyEvent> baseKeyPressed =
            e -> {
                keys.add(e.getCode());
                if (e.getCode() == KeyCode.E) {
                    GameCharacter hero = getPlayer();
                    if (hero != null) triggerUltimateFrom(hero);
                    e.consume();
                }
            };
    private final javafx.event.EventHandler<javafx.scene.input.KeyEvent> baseKeyReleased =
            e -> { keys.remove(e.getCode()); };
    private final javafx.event.EventHandler<javafx.scene.input.MouseEvent> baseMousePressed =
            e -> { keys.add(e.getButton()); };
    private final javafx.event.EventHandler<javafx.scene.input.MouseEvent> baseMouseReleased =
            e -> { keys.remove(e.getButton()); };

    // ---- HUD: lives + slow-mo ----
    private Image lifeIconImg;
    private final HBox livesBox = new HBox(6);
    private final Rectangle slowBg   = new Rectangle(120, 10, Color.color(0,0,0,0.35));
    private final Rectangle slowFill = new Rectangle(0,   10, Color.CORNFLOWERBLUE);

    // ---- Slow-time ----
    private double slowMoEnergy            = 1.0;  // 0..1
    private final double slowMoDrainRate   = 0.55; // per second
    private final double slowMoRechargeRate= 0.20; // per second
    private final double slowMoScale       = 0.25; // active time scale
    private boolean slowMoActive           = false;

    // ---- Win/overlay guards ----
    private Group gameClearOverlay = null;
    private boolean gameClearShown    = false; // prevent dup overlay
    private boolean winCheckEnabled   = true;  // disarm while resetting
    private boolean victoryShown      = false; // for shouldShowVictory()
    private boolean hadEnemiesThisStage = false;

    // ---- Callbacks for launcher wiring ----
    private Runnable onRetry = () -> {};
    private Runnable onExitToMenu = () -> {};
    public void setOnRetry(Runnable r)      { this.onRetry = (r != null ? r : () -> {}); }
    public void setOnExitToMenu(Runnable r) { this.onExitToMenu = (r != null ? r : () -> {}); }

    // Optional link to controller (used for respawns on retry)
    private advpro_game.controller.StageManager stageManager;
    public void setStageManager(advpro_game.controller.StageManager m) {
        this.stageManager = m;
        if (m == null) return;
        // Try StageManager#setGameStage(GameStage) *if it exists*.
        try {
            java.lang.reflect.Method attach =
                    m.getClass().getMethod("setGameStage", advpro_game.view.GameStage.class);
            attach.setAccessible(true);
            attach.invoke(m, this);
            LOG.info("StageManager accepted GameStage via reflection.");
        } catch (NoSuchMethodException nsme) {
            LOG.fine("StageManager has no setGameStage(GameStage); skipping reflective wiring.");
        } catch (Throwable t) {
            LOG.warning("Failed to reflectively set GameStage on StageManager: " + t);
        }
    }

    // ---- Logger ----
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(GameStage.class.getName());

    public GameStage() {
        // Make noninteractive layers not participate in picking; avoids deep pick walks.
        backgroundLayer.setPickOnBounds(false);
        worldLayer.setPickOnBounds(false);
        enemyLayer.setPickOnBounds(false);
        bulletLayer.setPickOnBounds(false);
        overlayLayer.setPickOnBounds(false);

        // ---- Background (safe init) ----
        try (InputStream s = Launcher.class.getResourceAsStream("/advpro_game/assets/Stage1.png")) {
            if (s != null) backgroundImg = new Image(s);
        } catch (Exception ignored) {}
        bgIV = new ImageView();
        bgIV.setFitHeight(HEIGHT);
        bgIV.setFitWidth(WIDTH);
        bgIV.setPreserveRatio(false);
        bgIV.setMouseTransparent(true);
        bgIV.setCache(true);
        if (backgroundImg != null) bgIV.setImage(backgroundImg);
        backgroundLayer.getChildren().add(bgIV);

        // ---- Mouse transparency on layers ----
        backgroundLayer.setMouseTransparent(true);
        worldLayer.setMouseTransparent(false);
        enemyLayer.setMouseTransparent(true);
        bulletLayer.setMouseTransparent(true);
        overlayLayer.setMouseTransparent(true); // will be disabled when showing overlay
        debugCanvas.setMouseTransparent(true);

        // ---- Add layers (once) ----
        getChildren().addAll(backgroundLayer, worldLayer, enemyLayer,
                bulletLayer, hudLayer, overlayLayer, debugCanvas);

        // ---- Z-ORDER via viewOrder (smaller draws on top) ----
        backgroundLayer.setViewOrder(100);
        worldLayer.setViewOrder(80);
        enemyLayer.setViewOrder(60);
        bulletLayer.setViewOrder(50);
        hudLayer.setViewOrder(30);
        overlayLayer.setViewOrder(10);
        debugCanvas.setViewOrder(0); // top-most (raise to -10 if you want above overlay)

        // ---- Build HUD ----
        buildHUD();

        // ---- Player ----
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
        Ui.safeAdd(worldLayer, p1);
        try { p1.setBulletSink(this::addBullet); }
        catch (Throwable t) { LOG.fine("setBulletSink unavailable; direct adds will be used."); }

        // ---- Score (center-top) ----
        Score s1 = new Score(0, 0);
        s1.setLayoutX((WIDTH / 2.0) - 40);
        s1.setLayoutY(8);
        scoreList.add(s1);
        hudLayer.getChildren().addAll(s1, livesBox, slowBg, slowFill);

        // ---- First stage ----
        // Defer one pulse to ensure the node is in a Scene before heavy work (prevents Scene sync NPEs)
        javafx.application.Platform.runLater(() -> setStage(1));

        // ---- Input (base handlers) ----
        setFocusTraversable(true);
        sceneProperty().addListener((obs, o, n) -> { if (n != null) requestFocus(); });
        setOnKeyPressed(baseKeyPressed);
        setOnKeyReleased(baseKeyReleased);
        setOnMousePressed(baseMousePressed);
        setOnMouseReleased(baseMouseReleased);
        requestFocus();

        // Track mouse
        setOnMouseMoved(e -> { mouseX = e.getX(); mouseY = e.getY(); });
        setOnMouseDragged(e -> { mouseX = e.getX(); mouseY = e.getY(); });
    }

    private void buildHUD() {
        livesBox.setAlignment(Pos.TOP_LEFT);
        livesBox.setLayoutX(16); livesBox.setLayoutY(32);
        livesBox.setMouseTransparent(true);
        try (InputStream in = Launcher.class.getResourceAsStream("/advpro_game/assets/life.png")) {
            if (in != null) lifeIconImg = new Image(in);
        } catch (Exception ignored) {}
        updateLivesHUD(3);

        slowBg.setArcWidth(6);  slowBg.setArcHeight(6);
        slowFill.setArcWidth(6); slowFill.setArcHeight(6);
        slowBg.setLayoutX(16);  slowBg.setLayoutY(56);
        slowFill.setLayoutX(16); slowFill.setLayoutY(56);
        slowFill.setWidth(slowBg.getWidth() * slowMoEnergy);
    }

    // =================== Stage switching (platforms + background + BGM) ===================
    public void setStage(int stageNumber) {
        // lock the world to prevent mid-reset mutations
        worldReady = false;

        detachVolatileLayers();
        currentStage = stageNumber;

        // Inform StageManager about stage if it supports it
        if (stageManager != null) {
            try {
                var m = stageManager.getClass().getMethod("setStage", int.class);
                m.setAccessible(true);
                m.invoke(stageManager, stageNumber);
                LOG.info("StageManager.setStage(" + stageNumber + ") invoked via reflection.");
            } catch (NoSuchMethodException ignored) {
                LOG.fine("StageManager has no setStage(int); continuing.");
            } catch (Throwable t) {
                LOG.warning("Could not call StageManager.setStage(int): " + t);
            }
        }

        generation++; // invalidate any stale timers from prior stage
        final int token = generation; // capture for timers to guard late-firing

        // >>> CRITICAL: let one pulse pass so clears happen before rebuild
        javafx.animation.PauseTransition drain =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(16));

        drain.setOnFinished(ev -> {
            if (token != generation) return; // stage changed again, ignore

            // reset flags
            hadEnemiesThisStage = false;
            victoryShown = false;
            gameClearShown = false;
            winCheckEnabled = true;

            // clear visuals on detached layers
            Ui.safeClear(enemyLayer);
            Ui.safeClear(bulletLayer);

            // clear logical lists
            platforms.clear();
            enemies.clear();
            bullets.clear();

            // ground
            platforms.add(Platform.solid(0, GROUND, WIDTH, 100));

            try {
                switch (stageNumber) {
                    case 1 -> {
                        setStageBackground(1);
                        platforms.add(Platform.oneWay(220, 270, 190, 30));
                        platforms.add(Platform.oneWay(476, 300, 60, 30));
                        safePlayBGM("/advpro_game/assets/bgm_stage1.mp3");
                    }
                    case 2 -> {
                        setStageBackground(2);
                        platforms.add(Platform.oneWay(50, 280, 150, 20));
                        platforms.add(Platform.oneWay(250, 240, 150, 20));
                        platforms.add(Platform.oneWay(450, 200, 150, 20));
                        platforms.add(Platform.oneWay(650, 160, 130, 20));
                        safePlayBGM("/advpro_game/assets/bgm_stage2.mp3");
                    }
                    case 3 -> {
                        setStageBackground(3);
                        platforms.add(Platform.oneWay(100, 250, 200, 25));
                        platforms.add(Platform.oneWay(350, 180, 100, 20));
                        platforms.add(Platform.oneWay(500, 250, 200, 25));
                        platforms.add(Platform.oneWay(200, 120, 150, 18));
                        safePlayBGM("/advpro_game/assets/bgm_stage3.mp3");
                    }
                    default -> {
                        setStageBackground(1);
                        safePlayBGM("/advpro_game/assets/bgm_stage1.mp3");
                    }
                }
            } catch (Throwable t) {
                LOG.warning("Stage setup failed: " + t);
            }

            // ---- ARM + SAFETIES (robust) ----
            javafx.animation.PauseTransition arm =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(80));
            arm.setOnFinished(_ev -> {
                if (token != generation) return; // stage changed, ignore

                // 1) Mark unlocked
                worldReady = true;

                // 2) Ensure layers are attached back
                attachVolatileLayers();

                // 3) Try spawning immediately (now that world is really ready)
                boolean spawnedNow = false;
                try {
                    spawnedNow = trySpawnForStageOnce(currentStage);
                } catch (Throwable t) {
                    LOG.warning("Spawn attempt failed (arm): " + t);
                }

                if (!spawnedNow && (allowSafetyMinions || autoFallbackToSafetyMinions) && lacksMinions()) {
                    try {
                        spawnDefaultMinionsFor(currentStage);
                        LOG.info("Arm: fall back to safety minions for stage " + currentStage);
                    } catch (Throwable t) {
                        LOG.warning("Fallback spawn failed (arm): " + t);
                    }
                } else if (spawnedNow) {
                    LOG.info("Arm: enemies spawned for stage " + currentStage);
                } else {
                    LOG.info("Arm: spawn requested, waiting for safety checks...");
                }

                // 4) Flush deferred ops queue
                rearmWorldReadySoon();
            });
            arm.play();

            // Safety #1 at ~600ms
            javafx.animation.PauseTransition safety600 =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
            safety600.setOnFinished(__ -> {
                if (token != generation) return;
                try {
                    if (noEnemiesVisible()) {
                        boolean spawned = trySpawnForStageOnce(currentStage);
                        if (!spawned && (allowSafetyMinions || autoFallbackToSafetyMinions)) {
                            spawnDefaultMinionsFor(currentStage);
                            LOG.info("Safety600: default minions spawned for stage " + currentStage);
                        } else if (spawned) {
                            LOG.info("Safety600: enemies appeared for stage " + currentStage);
                        } else {
                            LOG.info("Safety600: spawn requested but still no visible enemies.");
                        }
                    } else {
                        LOG.info("Safety600: enemies already visible, skipping.");
                    }
                } catch (Throwable t) {
                    LOG.warning("Safety600 failed: " + t);
                }
            });
            safety600.play();

            // Safety #2 at ~1500ms (final guarantee)
            javafx.animation.PauseTransition safety1500 =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(1500));
            safety1500.setOnFinished(__ -> {
                if (token != generation) return;
                try {
                    if (noEnemiesVisible()) {
                        boolean spawned = trySpawnForStageOnce(currentStage);
                        if (!spawned && (allowSafetyMinions || autoFallbackToSafetyMinions)) {
                            spawnDefaultMinionsFor(currentStage);
                            LOG.info("Safety1500: default minions spawned for stage " + currentStage);
                        } else if (spawned) {
                            LOG.info("Safety1500: enemies appeared for stage " + currentStage);
                        } else {
                            LOG.info("Safety1500: spawn requested but none visible. Check StageManager wiring.");
                        }
                    } else {
                        LOG.info("Safety1500: enemies already visible, skipping.");
                    }
                } catch (Throwable t) {
                    LOG.warning("Safety1500 failed: " + t);
                }
            });
            safety1500.play();

            LOG.info("Stage " + stageNumber + " initialized (arm +80ms).");
        });

        drain.play();
    }

    private boolean lacksMinions() {
        for (var e : enemies) {
            if (e instanceof Minion || e instanceof EliteMinion) return false;
        }
        return true;
    }

    private void safePlayBGM(String path) {
        try { AudioManager.playBGM(path); } catch (Throwable ignored) { /* media module missing: noop */ }
    }

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
                Ui.later(() -> bgIV.setImage(newBg)); // FX thread, next pulse
            }
        } catch (Exception ignored) {}
    }

    public void spawnDefaultMinionsFor(int stageIdx) {
        switch (stageIdx) {
            case 1 -> {
                addEnemy(new Minion(150, GROUND - 50));
                addEnemy(new Minion(250, GROUND - 50));
                addEnemy(new Minion(350, GROUND - 50));
                addEnemy(new Minion(450, GROUND - 50));
                addEnemy(new EliteMinion(550, GROUND - 60));
            }
            case 2 -> {
                addEnemy(new Minion(120, GROUND - 50));
                addEnemy(new Minion(220, GROUND - 50));
                addEnemy(new Minion(320, GROUND - 50));
                addEnemy(new Minion(420, GROUND - 50));
                addEnemy(new Minion(520, GROUND - 50));
                addEnemy(new EliteMinion(650, GROUND - 60));
            }
            case 3 -> {
                addEnemy(new Minion(100, GROUND - 50));
                addEnemy(new Minion(180, GROUND - 50));
                addEnemy(new Minion(260, GROUND - 50));
                addEnemy(new Minion(340, GROUND - 50));
                addEnemy(new Minion(420, GROUND - 50));
                addEnemy(new Minion(500, GROUND - 50));
                addEnemy(new EliteMinion(650, GROUND - 60));
            }
            default -> {
                addEnemy(new Minion(200, GROUND - 50));
                addEnemy(new Minion(300, GROUND - 50));
            }
        }
    }

    // =================== Slow-mo ===================
    public void tickSlowMo(boolean wantSlow, double dtSeconds) {
        if (wantSlow && slowMoEnergy > 0.0) {
            slowMoActive = true;
            slowMoEnergy -= slowMoDrainRate * dtSeconds;
        } else {
            slowMoActive = false;
            slowMoEnergy += slowMoRechargeRate * dtSeconds;
        }
        if (slowMoEnergy < 0.0) { slowMoEnergy = 0.0; slowMoActive = false; }
        if (slowMoEnergy > 1.0)  slowMoEnergy = 1.0;

        Ui.later(() -> slowFill.setWidth(slowBg.getWidth() * slowMoEnergy));
    }

    /** Time scale for enemies & enemy bullets only. Player logic ignores this. */
    public double getTimeScale() { return slowMoActive ? slowMoScale : 1.0; }


    // =================== HUD ===================
    public void updateLivesHUD(int lives) {
        if (lives < 0) lives = 0;
        final int liveCount = lives;
        Ui.later(() -> {
            livesBox.getChildren().clear();
            for (int i = 0; i < liveCount; i++) {
                if (lifeIconImg != null) {
                    ImageView iv = new ImageView(lifeIconImg);
                    iv.setFitWidth(22); iv.setFitHeight(22);
                    iv.setPreserveRatio(true);
                    iv.setEffect(new DropShadow(8, Color.color(0,0,0,0.75)));
                    livesBox.getChildren().add(iv);
                } else {
                    Rectangle r = new Rectangle(22, 22, Color.GOLD);
                    r.setArcWidth(6); r.setArcHeight(6);
                    r.setEffect(new DropShadow(8, Color.color(0,0,0,0.75)));
                    livesBox.getChildren().add(r);
                }
            }
        });
    }

    // =================== Public API (used by loops/managers) ===================
    public List<Platform> getPlatforms() { return platforms; }
    public List<Bullet>   getBullets()   { return bullets; }
    public List<Enemy>    getEnemies()   { return enemies; }
    public GraphicsContext getDebugGC()  { return debugCanvas.getGraphicsContext2D(); }
    public List<Score> getScoreList()    { return scoreList; }
    public void setScoreList(List<Score> list) {
        scoreList = (list != null) ? list : new ArrayList<>();
        Ui.safeReplaceChildren(hudLayer,
                scoreList.stream().filter(java.util.Objects::nonNull).toList());
    }

    public List<GameCharacter> getGameCharacterList() { return gameCharacterList; }
    public GameCharacter getPlayer() {
        return gameCharacterList.isEmpty() ? null : gameCharacterList.get(0);
    }
    public Keys getKeys() { return keys; }
    public MouseButton getMouseButton() { return mouseButton; } // compat
    public Pane getDBoverlay() { return hudLayer; } // kept for compat if used

    // For GameLoop victory gating
    public boolean shouldShowVictory() { return !victoryShown && hadEnemiesThisStage && enemies.isEmpty(); }
    public void markVictoryShown() { victoryShown = true; }

    // =================== Node management (FX-safe) ===================
    public void addBullet(Bullet b) {
        if (b == null) return;
        if (!bullets.contains(b)) bullets.add(b);
        Runnable addVisual = () -> {
            try {
                javafx.scene.Node node = (b == null) ? null : b.getNode();
                if (node != null) Ui.safeAdd(bulletLayer, node);
                else System.err.println("WARN addBullet: Bullet.getNode() is null; logic-only.");
            } catch (Throwable t) {
                System.err.println("WARN addBullet: " + t);
            }
        };
        if (LOG.isLoggable(Level.FINE)) {
            try {
                GameCharacter p = getPlayer();
                LOG.fine("[Stage] bullet +1 from " + (p != null ? p.getClass().getSimpleName() : "unknown")
                        + " angle=" + String.format("%.1f", (p != null ? getSnappedAimAngleDeg(p) : 0.0)));
            } catch (Throwable ignored) {}
        }
        runOrDefer(addVisual);
    }

    public void removeBullet(Bullet b) {
        if (b == null) return;
        Runnable removeVisual = () -> {
            try {
                javafx.scene.Node node = b.getNode();
                if (node != null) Ui.safeRemove(bulletLayer, node);
            } catch (Throwable ignored) {}
        };
        // defer one pulse to avoid interleaving with clears; also safe if locked
        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1));
        pt.setOnFinished(ev -> runOrDefer(removeVisual));
        pt.play();
        bullets.remove(b);
    }

    public void addEnemy(Enemy e) {
        if (e == null) return;

        // --- Gate Java-bullet usage to Stage-2 Java boss only ---
        // If this enemy is a Boss and bossType != 2, force-clear any custom bullet
        try {
            if (e instanceof Boss b) {
                try {
                    int t = b.getBossType();   // requires Boss#getBossType()
                    if (t != 2) {
                        try { b.setBulletConfig(null); } catch (Throwable ignored) {}
                    }
                } catch (Throwable noGetter) {
                    // If getBossType() isn’t available, best-effort: clear config anyway
                    try { b.setBulletConfig(null); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
            // Never let visuals break if reflection/typing fails
        }
        // ---------------------------------------------------------

        if (!enemies.contains(e)) enemies.add(e);
        hadEnemiesThisStage = true;

        Runnable addVisual = () -> {
            try {
                javafx.scene.Node node = null;
                try { node = e.getNode(); } catch (Throwable ignored) {}
                if (node == null && e instanceof javafx.scene.Node n2) node = n2;
                if (node != null) Ui.safeAdd(enemyLayer, node);
                else System.err.println("WARN addEnemy: enemy has no Node; logic only (no visual).");
            } catch (Throwable t) {
                System.err.println("WARN addEnemy: " + t);
            }
        };

        runOrDefer(addVisual);
    }


    public void removeEnemy(Enemy e) {
        if (e == null) return;
        try {
            javafx.scene.Node node = null;
            try { node = e.getNode(); } catch (Throwable ignored) {}
            if (node == null && e instanceof javafx.scene.Node n2) node = n2;

            final javafx.scene.Node nodeRef = node; // <- make it effectively final
            if (nodeRef != null) {
                runOrDefer(() -> Ui.safeRemove(enemyLayer, nodeRef));
            }
        } catch (Throwable ignored) {}
        enemies.remove(e);
    }

    // Optional local update (useful for debug tools)
    private void update(double dtSec) {
        // bullets
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            try {
                b.update(dtSec);
                for (Enemy e : new ArrayList<>(enemies)) { // copy to be safe if enemy.update removes itself
                    try {
                        if (b.getHitbox().intersects(e.getHitbox())) {
                            e.hit(1);
                            GameCharacter p = getPlayer();
                            if (p != null) {
                                int pts = 10; // tweak or make e.scoreOnHit()
                                p.addScoreLogged(pts, "hit " + e.getClass().getSimpleName());
                            }
                            it.remove();
                            Ui.safeRemove(bulletLayer, b.getNode());
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                LOG.warning("Bullet update failed: " + t);
            }
        }
        // enemies
        for (Enemy e : new ArrayList<>(enemies)) {
            try {
                GameCharacter p = getPlayer();
                e.update(dtSec, p);
            } catch (Throwable t) {
                LOG.warning("Enemy update failed: " + t);
            }
        }
        // players
        for (GameCharacter c : new ArrayList<>(gameCharacterList)) {
            try {
                // propagate slow-mo to sprite framerate (if AnimatedSprite supports it)
                if (c.getImageView() != null) {
                    try { c.getImageView().setTimeScale(getTimeScale()); } catch (Throwable ignored) {}
                }
                c.repaint(dtSec);
                c.checkPlatformCollision(platforms);
                c.checkReachHighest();
                c.checkReachFloor();
            } catch (Throwable t) {
                LOG.warning("Player update failed: " + t);
            }
        }
    }

    // =================== Effects / Overlays ===================
    public void showHitFlash(double x, double y) {
        Ui.later(() -> {
            var dot = new javafx.scene.shape.Circle(8, javafx.scene.paint.Color.ORANGE);
            dot.setTranslateX(x);
            dot.setTranslateY(y);
            overlayLayer.getChildren().add(dot);

            var fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), dot);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(ev -> Ui.later(() -> overlayLayer.getChildren().remove(dot)));
            fade.play();
        });
    }

    // --------- Game Over ----------
    public void showGameOverOverlay() {
        Ui.later(() -> {
            Ui.safeClear(overlayLayer);
            overlayLayer.setMouseTransparent(false);
            overlayLayer.setPickOnBounds(true);
            var overlay = new GameOverOverlay(
                    () -> {
                        Ui.safeClear(overlayLayer);
                        overlayLayer.setMouseTransparent(true);
                        if (onRetry != null) onRetry.run();
                    },
                    () -> {
                        Ui.safeClear(overlayLayer);
                        overlayLayer.setMouseTransparent(true);
                        if (onExitToMenu != null) onExitToMenu.run();
                    },
                    WIDTH, HEIGHT
            );
            overlayLayer.getChildren().add(overlay);
        });
    }

    // --------- Victory flow ----------
    public void requestGameClear() {
        if (!winCheckEnabled || gameClearShown) return;
        gameClearShown = true;
        showGameClearOverlay();
    }

    private void showGameClearOverlay() {
        Ui.later(() -> {
            overlayLayer.getChildren().remove(gameClearOverlay);
            gameClearOverlay = new Group();

            var dim = new javafx.scene.shape.Rectangle(WIDTH, HEIGHT);
            dim.setFill(javafx.scene.paint.Color.color(0, 0, 0, 0.6));

            var msg = new javafx.scene.text.Text("ALL STAGES CLEARED!");
            msg.setFill(javafx.scene.paint.Color.LIMEGREEN);
            msg.setFont(javafx.scene.text.Font.font("Consolas", javafx.scene.text.FontWeight.BOLD, 42));
            msg.setX(WIDTH / 2.0 - 260);
            msg.setY(HEIGHT / 2.0 - 10);

            var sub = new javafx.scene.text.Text("Press ESC to Exit or R to Restart");
            sub.setFill(javafx.scene.paint.Color.WHITE);
            sub.setFont(javafx.scene.text.Font.font("Consolas", 20));
            sub.setX(WIDTH / 2.0 - 190);
            sub.setY(HEIGHT / 2.0 + 40);

            gameClearOverlay.getChildren().addAll(dim, msg, sub);
            overlayLayer.getChildren().add(gameClearOverlay);
            overlayLayer.setMouseTransparent(false);
            overlayLayer.setPickOnBounds(true);

            // Freeze players & capture keys
            getGameCharacterList().forEach(c -> c.setDisable(true));

            // Capture keys for exit/restart; consume so gameplay doesn’t see them
            setOnKeyPressed(e -> {
                e.consume();
                switch (e.getCode()) {
                    case ESCAPE -> javafx.application.Platform.exit();
                    case R -> {
                        clearPressedInputs();
                        restartFromStage1();
                    }
                }
            });

            try { AudioManager.playSFX("/advpro_game/assets/sfx_stageclear.mp3"); } catch (Exception ignored) {}
            System.out.println("INFO: All stages cleared!");
        });
    }

    // --------- Restart / Retry helpers ----------
    private void restartFromStage1() {
        Ui.later(() -> {
            worldReady = false;
            System.out.println("INFO: Restarting from Stage 1...");

            detachVolatileLayers();

            // disarm win checks while resetting
            winCheckEnabled = false;
            gameClearShown  = false;
            victoryShown    = false;

            // remove overlay & restore base inputs
            overlayLayer.getChildren().remove(gameClearOverlay);
            gameClearOverlay = null;
            overlayLayer.setMouseTransparent(true);
            overlayLayer.setPickOnBounds(false);
            setOnKeyPressed(baseKeyPressed);
            setOnKeyReleased(baseKeyReleased);
            setOnMousePressed(baseMousePressed);
            setOnMouseReleased(baseMouseReleased);

            // clear world
            bullets.clear();
            enemies.clear();
            hadEnemiesThisStage = false;
            Ui.safeClear(bulletLayer);
            Ui.safeClear(enemyLayer);

            // reset HUD/slow-mo
            slowMoActive = false;
            slowMoEnergy = 1.0;
            slowFill.setWidth(slowBg.getWidth() * slowMoEnergy);
            scoreList.forEach(Score::reset);

            // respawn player(s)
            for (var c : gameCharacterList) {
                c.setDisable(false);
                c.respawn();
            }
            GameCharacter p = getPlayer();
            if (p != null) updateLivesHUD(p.getLives());

            // go to Stage 1
            currentStage = 1;
            setStage(currentStage);

            // re-arm victory after a small delay; attach & unlock handled in setStage
            var arm = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
            arm.setOnFinished(ev -> winCheckEnabled = true);
            arm.play();

            requestFocus();
            System.out.println("INFO: Game restarted at Stage 1!");
        });
    }

    private void retryStage() {
        Ui.later(() -> {
            worldReady = false;
            System.out.println("INFO: Retrying stage...");

            detachVolatileLayers();

            // disarm win checks while resetting
            winCheckEnabled = false;
            gameClearShown  = false;
            victoryShown    = false;

            // remove overlay & restore inputs
            overlayLayer.getChildren().remove(gameClearOverlay);
            gameClearOverlay = null;
            overlayLayer.setMouseTransparent(true);
            overlayLayer.setPickOnBounds(false);
            setOnKeyPressed(baseKeyPressed);
            setOnKeyReleased(baseKeyReleased);
            setOnMousePressed(baseMousePressed);
            setOnMouseReleased(baseMouseReleased);

            // clear world lists & visuals
            bullets.clear();
            enemies.clear();
            hadEnemiesThisStage = false;
            Ui.safeClear(bulletLayer);
            Ui.safeClear(enemyLayer);

            // reset HUD/slow-mo
            slowMoActive = false;
            slowMoEnergy = 1.0;
            slowFill.setWidth(slowBg.getWidth() * slowMoEnergy);
            scoreList.forEach(Score::reset);

            // respawn player(s)
            for (var c : gameCharacterList) {
                c.setDisable(false);
                c.respawn();
            }
            GameCharacter p = getPlayer();
            if (p != null) updateLivesHUD(p.getLives());

            // rebuild same stage
            setStage(currentStage);

            // ask StageManager to re-spawn (if available)
            if (stageManager != null) {
                try {
                    stageManager.spawnEnemiesForStage(currentStage);
                    System.out.println("INFO: Enemies respawned for Stage " + currentStage);
                } catch (Throwable t) {
                    System.err.println("WARN: stageManager spawn failed: " + t);
                }
            } else {
                System.err.println("WARN: stageManager is null — cannot spawn enemies!");
            }

            // re-arm victory after a tiny delay
            var arm = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
            arm.setOnFinished(ev -> winCheckEnabled = true);
            arm.play();

            requestFocus();
            System.out.println("INFO: Stage restarted!");
        });
    }

    // =================== Small utilities ===================
    public static final class Ui {
        private static final java.util.logging.Logger LOG =
                java.util.logging.Logger.getLogger(Ui.class.getName());

        /** ALWAYS schedule on next pulse. */
        public static void later(Runnable r) {
            if (r != null) javafx.application.Platform.runLater(r);
        }
        public static void runFx(Runnable r) { later(r); }

        // ---- Overloads for Group and Pane (no index math anywhere) ----
        public static void safeAdd(Group parent, javafx.scene.Node n) {
            if (parent == null || n == null) { if (n == null) LOG.warning("safeAdd: null Node"); return; }
            later(() -> {
                try {
                    var kids = parent.getChildren();
                    if (!kids.contains(n)) kids.add(n);
                } catch (Throwable t) { LOG.warning("safeAdd(Group) failed: " + t); }
            });
        }
        public static void safeRemove(Group parent, javafx.scene.Node n) {
            if (parent == null || n == null) return;
            later(() -> {
                try { parent.getChildren().remove(n); }
                catch (Throwable t) { LOG.warning("safeRemove(Group) failed: " + t); }
            });
        }
        public static void safeClear(Group parent) {
            if (parent == null) return;
            later(() -> {
                try { parent.getChildren().clear(); }
                catch (Throwable t) { LOG.warning("safeClear(Group) failed: " + t); }
            });
        }

        public static void safeAdd(Pane parent, javafx.scene.Node n) {
            if (parent == null || n == null) { if (n == null) LOG.warning("safeAdd: null Node"); return; }
            later(() -> {
                try {
                    var kids = parent.getChildren();
                    if (!kids.contains(n)) kids.add(n);
                } catch (Throwable t) { LOG.warning("safeAdd(Pane) failed: " + t); }
            });
        }
        public static void safeRemove(Pane parent, javafx.scene.Node n) {
            if (parent == null || n == null) return;
            later(() -> {
                try { parent.getChildren().remove(n); }
                catch (Throwable t) { LOG.warning("safeRemove(Pane) failed: " + t); }
            });
        }
        public static void safeClear(Pane parent) {
            if (parent == null) return;
            later(() -> {
                try { parent.getChildren().clear(); }
                catch (Throwable t) { LOG.warning("safeClear(Pane) failed: " + t); }
            });
        }
        public static void safeReplaceChildren(Pane parent, java.util.Collection<? extends javafx.scene.Node> items) {
            if (parent == null) return;
            later(() -> {
                try {
                    var kids = parent.getChildren();
                    kids.clear();
                    if (items == null) return;
                    for (var node : items) if (node != null) kids.add(node);
                } catch (Throwable t) {
                    LOG.warning("safeReplaceChildren failed: " + t);
                }
            });
        }
    }
    private static boolean isEnemyBulletSafe(Bullet b) {
        if (b == null) return false;
        try {
            // Your Bullet has a ctor ... , ..., boolean isEnemy
            // and typically exposes a getter; be tolerant if signature differs.
            return b.isEnemyBullet();
        } catch (Throwable ignored) {
            // Fallbacks if older Bullet API:
            try { return (boolean) b.getClass().getMethod("isEnemyBullet").invoke(b); }
            catch (Throwable __) { /* last resort: assume player bullet */ }
        }
        return false;
    }

    private void clearPressedInputs() {
        try { keys.clear(); } catch (Throwable ignored) {}
        for (var c : gameCharacterList) c.setDisable(false);
    }

    /** Snap the aim to -45°, 0°, or +45° based on current mouse position. */
    public double getSnappedAimAngleDeg(GameCharacter c) {
        if (c == null) return 0.0;
        double cx = c.getTranslateX() + c.getCharacterWidth() * 0.5;
        double cy = c.getTranslateY() + c.getCharacterHeight() * 0.55;

        int facing = c.getFacingDir(); // +1 when facing right, -1 when left
        double dx = (mouseX - cx) * facing;
        double dy = (mouseY - cy);

        if (dx < 0) dx = Math.abs(dx);

        double angle = Math.toDegrees(Math.atan2(dy, dx)); // -180..+180 (0=straight)
        if (angle <= -15) return -45.0;  // aim up
        if (angle >=  15) return  45.0;  // aim down
        return 0.0;                      // straight
    }

    // --- Robust enemy visibility checks ---
    private boolean noEnemiesVisible() {
        boolean noneInList  = (enemies == null || enemies.isEmpty());
        boolean noneInLayer = (enemyLayer == null || enemyLayer.getChildren().isEmpty());
        return noneInList && noneInLayer;
    }

    private boolean trySpawnForStageOnce(int stage) {
        int beforeCount = (enemies == null) ? 0 : enemies.size();
        int beforeNodes = (enemyLayer == null) ? 0 : enemyLayer.getChildren().size();

        // Ask StageManager first (if present)
        if (stageManager != null) {
            try {
                stageManager.spawnEnemiesForStage(stage);
                LOG.info("Asked StageManager to spawn for stage " + stage);
            } catch (Exception ex) {
                LOG.warning("StageManager.spawnEnemiesForStage failed: " + ex.getMessage());
            }
        }

        // Give FX a pulse in case spawner adds nodes/list this tick
        javafx.application.Platform.runLater(() -> {});

        int afterCount = (enemies == null) ? 0 : enemies.size();
        int afterNodes = (enemyLayer == null) ? 0 : enemyLayer.getChildren().size();
        boolean changed = (afterCount > beforeCount) || (afterNodes > beforeNodes);

        LOG.info("Spawn delta -> list: " + beforeCount + "→" + afterCount +
                ", nodes: " + beforeNodes + "→" + afterNodes + " (changed=" + changed + ")");

        // Guaranteed fallback: if nothing appeared, inject default minions now.
        if (!changed && (autoFallbackToSafetyMinions || allowSafetyMinions) && noEnemiesVisible()) {
            try {
                spawnDefaultMinionsFor(stage);
                // Re-check immediately after fallback
                int fallCount = (enemies == null) ? 0 : enemies.size();
                int fallNodes = (enemyLayer == null) ? 0 : enemyLayer.getChildren().size();
                boolean fallbackChanged = (fallCount > beforeCount) || (fallNodes > beforeNodes);
                LOG.info("Fallback default minions -> list: " + beforeCount + "→" + fallCount +
                        ", nodes: " + beforeNodes + "→" + fallNodes + " (changed=" + fallbackChanged + ")");
                return fallbackChanged;
            } catch (Throwable t) {
                LOG.warning("Fallback spawn failed: " + t);
            }
        }

        return changed;
    }


    // === ULT LASER FX ===
    public void spawnUltimateLaser(advpro_game.model.LaserBeamUltimate fx) {
        // Remove any previous beam first
        if (activeUltNode != null) {
            Ui.safeRemove(bulletLayer, activeUltNode);
            activeUltNode = null;
        }

        // Host pane (because playOnce expects Pane, but bulletLayer is a Group)
        javafx.scene.layout.Pane host = new javafx.scene.layout.Pane();
        host.setPickOnBounds(false);
        host.setMouseTransparent(true);
        bulletLayer.getChildren().add(host);

        // Play once; remember the node so we can kill it next time
        fx.playOnce(host);
        activeUltNode = fx.getNode();

        // Cleanup host automatically after a short delay if it’s empty
        var cleanup = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.0));
        cleanup.setOnFinished(ev -> {
            if (host.getChildren().isEmpty()) bulletLayer.getChildren().remove(host);
            // If animation already removed the node, clear the pointer
            if (activeUltNode != null && !host.getChildren().contains(activeUltNode)) {
                activeUltNode = null;
            }
        });
        cleanup.play();
    }


    /** Best-effort removal of an enemy from scene + internal lists. */
    public void removeEnemyIfPresent(Enemy e) {
        try {
            if (e == null) return;

            // Remove from visual layers if present
            try {
                if (enemyLayer.getChildren().contains(e)) enemyLayer.getChildren().remove(e);
            } catch (Throwable ignore) { }
            try {
                getChildren().remove(e); // just in case it was added directly to this Pane
            } catch (Throwable ignore) { }

            // Remove from logical list
            try {
                enemies.remove(e);
            } catch (Throwable ignore) { }

            // Hide as a final safeguard
            try {
                e.setVisible(false);
            } catch (Throwable ignore) { }
        } catch (Throwable ignoreOuter) { }
    }


    // ======== PATCH: fire ultimate laser from player and wipe all enemies ========
    /** Fire the ultimate laser from the player's muzzle and wipe all enemies. */
    private void triggerUltimateFrom(GameCharacter hero) {
        try {
            int dir;
            try { dir = hero.getFacingDir(); }
            catch (Throwable ignored) { dir = (hero.getScaleX() >= 0) ? 1 : -1; }

            // Muzzle offsets — tweak if needed to line up with your sprite
            double muzzleX = hero.getTranslateX() + (dir > 0 ? 62 : 4);
            double muzzleY = hero.getTranslateY() + 34;

            // Visual FX (optional if LaserBeamUltimate exists)
            try {
                advpro_game.model.LaserBeamUltimate fx =
                        new advpro_game.model.LaserBeamUltimate(muzzleX, muzzleY, dir, 1.0);
                spawnUltimateLaser(fx);
            } catch (Throwable vizErr) {
                LOG.fine("Ultimate FX unavailable: " + vizErr);
            }

            // Wipe: remove all enemies immediately
            for (Enemy e : new java.util.ArrayList<>(enemies)) {
                try { removeEnemy(e); }
                catch (Throwable fall) { removeEnemyIfPresent(e); }
            }
        } catch (Throwable t) {
            LOG.warning("triggerUltimateFrom failed: " + t);
        }
    }
}

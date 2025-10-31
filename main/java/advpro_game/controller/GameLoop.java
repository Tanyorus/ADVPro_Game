package advpro_game.controller;

import advpro_game.model.Bullet;
import advpro_game.model.GameCharacter;
import advpro_game.view.GameStage;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class GameLoop implements Runnable {
    private final GameStage gameStage;
    private StageManager stageManager;     // may be injected later

    private final int frameRate  = 60;
    private final float interval = 1000.0f / frameRate;
    private volatile boolean running = true;

    // Invincibility after being hit (ms)
    private long invincibleUntil = 0;

    // Edge detection for jump
    private boolean prevW = false, prevUp = false, prevSpace = false;

    public GameLoop(GameStage gameStage) {
        this.gameStage = gameStage;
        // StageManager is lazy-inited in run(), or can be injected via attachStageManager()
    }

    /** Optional hook if you construct StageManager elsewhere (e.g., in Launcher). */
    public void attachStageManager(StageManager m) {
        this.stageManager = m;
    }

    public void stop() { running = false; }

    // ===================== PLAYER =====================
    private void updateCharacters(List<GameCharacter> list, double dtSec) {
        if (list.isEmpty()) return;

        final boolean worldReady = gameStage.isWorldReady();

        // slow-mo toggle (SHIFT)
        boolean wantSlow = gameStage.getKeys().isPressed(KeyCode.SHIFT);
        gameStage.tickSlowMo(wantSlow, dtSec);
        double scaled = dtSec * gameStage.getTimeScale();


        for (GameCharacter c : list) {
            try {
                c.beginFrame();

                boolean wPressed     = gameStage.getKeys().isPressed(KeyCode.W);
                boolean upPressed    = gameStage.getKeys().isPressed(KeyCode.UP);
                boolean spacePressed = gameStage.getKeys().isPressed(KeyCode.SPACE);
                boolean left         = gameStage.getKeys().isPressed(c.getLeftKey());
                boolean right        = gameStage.getKeys().isPressed(c.getRightKey());
                boolean down         = gameStage.getKeys().isPressed(c.getDownKey());

                // edge detection (tap to jump with W/UP/SPACE)
                boolean upEdge    = (!prevW && wPressed) || (!prevUp && upPressed);
                boolean spaceEdge = (!prevSpace && spacePressed);

                // movement / prone
                if (down && !(left || right)) c.prone();
                else if (left && !right)      c.moveLeft();
                else if (right && !left)      c.moveRight();
                else                          c.stop();

                // jump on edge
                if (upEdge || spaceEdge) c.jump();

                // double-tap down drop-through (implemented inside character)
                c.handleDownKey(down);

                // shooting — mouse-aim snapped to -45, 0, +45 degrees
                // IMPORTANT: do NOT spawn bullets while world is rebuilding
                if (worldReady && !c.isDisabled()) {
                    double aimDeg = getSnappedAimAngleDeg(c);
                    Bullet b = c.tryCreateBullet(gameStage.getKeys(), aimDeg);
                    if (b != null) gameStage.addBullet(b);
                }

                // physics + collisions
                c.repaint(scaled * 1000.0); // repaint expects ms
                c.checkPlatformCollision(gameStage.getPlatforms());
                c.checkReachHighest();
                c.checkReachFloor();
            } catch (Throwable t) {
                // keep the loop resilient
                t.printStackTrace();
            }
        }

        // update previous key states (when world not ready, treat as not-pressed to avoid buffered edges)
        prevW     = gameStage.getKeys().isPressed(KeyCode.W)     && worldReady;
        prevUp    = gameStage.getKeys().isPressed(KeyCode.UP)    && worldReady;
        prevSpace = gameStage.getKeys().isPressed(KeyCode.SPACE) && worldReady;
    }

    /**
     * Compute snapped aim angle in degrees relative to the player:
     * returns exactly -45 (up), 0 (straight), or +45 (down).
     * Uses GameStage mouse position; no dependency on a GameStage helper.
     * Convention: negative = up, positive = down (screen Y grows downward).
     */
    // Let GameStage compute the snapped aim based on its tracked mouse.
    private double getSnappedAimAngleDeg(GameCharacter c) {
        return gameStage.getSnappedAimAngleDeg(c);
    }


    // ===================== BULLETS =====================
    private void updateBullets(double dtSeconds) {
        double scaled = dtSeconds * gameStage.getTimeScale();
        var bullets = gameStage.getBullets();

        // snapshot to avoid CME
        var snapshot = new ArrayList<>(bullets);
        var toRemove = new ArrayList<Bullet>();

        for (Bullet b : snapshot) {
            if (b == null) continue;
            try {
                b.update(scaled);
            } catch (Throwable ignored) {}

            // Cull far-off bullets (logic only; visual removal is FX-safe inside GameStage)
            if (b.getX() < -100 || b.getX() > GameStage.WIDTH + 100 ||
                    b.getY() < -100 || b.getY() > GameStage.HEIGHT + 200) {
                toRemove.add(b);
            }
        }

        if (!toRemove.isEmpty()) {
            // remove via GameStage (which defers scene-graph ops while not worldReady)
            for (Bullet b : toRemove) gameStage.removeBullet(b);
        }
    }

    // ===================== ENEMIES =====================
    private void updateEnemies(double dtSeconds) {
        if (gameStage.getGameCharacterList().isEmpty()) return;

        final boolean worldReady = gameStage.isWorldReady();
        double scaled = dtSeconds * gameStage.getTimeScale();
        GameCharacter player = gameStage.getGameCharacterList().get(0);


        // defensive copy to avoid CME
        var snapshot = new ArrayList<>(gameStage.getEnemies());

        for (var enemy : snapshot) {
            try {
                enemy.update(scaled, player);
            } catch (NoSuchMethodError | Exception ignored) {}

            // Do not spawn enemy bullets while world is rebuilding
            if (worldReady) {
                try {
                    Bullet b = enemy.tryShoot(player);
                    if (b != null) gameStage.addBullet(b);
                } catch (Exception ignored) {}
            }
        }
    }

    // ===================== HUD + SCORE =====================
    private void updateScore(List<GameCharacter> chars) {
        if (!gameStage.getScoreList().isEmpty() && !chars.isEmpty()) {
            Platform.runLater(() -> {
                try {
                    gameStage.getScoreList().get(0).setPoint(chars.get(0).getScore());
                    gameStage.updateLivesHUD(chars.get(0).getLives());
                } catch (Throwable ignored) {}
            });
        }
    }

    // ===================== COLLISIONS =====================
    private void checkCharacterEnemyCollisions() {
        long now = System.currentTimeMillis();
        if (now < invincibleUntil) return;

        var bulletsSnap = new ArrayList<>(gameStage.getBullets());
        var enemiesSnap = new ArrayList<>(gameStage.getEnemies());

        for (GameCharacter c : gameStage.getGameCharacterList()) {
            // direct body contact
            for (var e : enemiesSnap) {
                if (c.getHitbox().intersects(e.getHitbox())) {
                    onPlayerHit(c);
                    return;
                }
            }
            // enemy bullet contact
            for (Bullet b : bulletsSnap) {
                try {
                    if (b.isEnemyBullet() && c.getHitbox().intersects(b.getHitbox())) {
                        onPlayerHit(c);
                        // remove bullet via GameStage (FX-safe / deferred)
                        PauseTransition delay = new PauseTransition(Duration.millis(10));
                        delay.setOnFinished(ev -> gameStage.removeBullet(b));
                        Platform.runLater(delay::play);
                        return;
                    }
                } catch (NoSuchMethodError err) {
                    // if isEnemyBullet() absent in some build, ignore gracefully
                }
            }
        }
    }

    private void onPlayerHit(GameCharacter c) {
        long now = System.currentTimeMillis();

        c.loseLife();
        Platform.runLater(() -> gameStage.updateLivesHUD(c.getLives()));

        // Always respawn when hit
        c.respawn();
        // 1.5s invincibility frames
        invincibleUntil = now + 1500;

        if (c.getLives() <= 0) {
            Platform.runLater(gameStage::showGameOverOverlay);
            stop();
        }
    }

    // ===================== MAIN LOOP =====================
    @Override
    public void run() {
        long last = System.nanoTime();

        if (stageManager == null) {
            stageManager = new StageManager(gameStage);
            stageManager.start();
        }

        while (running) {

            // >>> NEW: if the scene is being rebuilt, do nothing this frame
            if (!gameStage.isWorldReady()) {
                // Reset edge detectors so a held key doesn’t “edge-fire” after the pause
                prevW = prevUp = prevSpace = false;
                try { Thread.sleep(4); } catch (InterruptedException ignored) {}
                last = System.nanoTime();   // reset dt so physics won't jump
                continue;
            }
            // <<< NEW

            long now = System.nanoTime();
            double dtSec = (now - last) / 1_000_000_000.0;
            last = now;

            updateCharacters(gameStage.getGameCharacterList(), dtSec);
            updateBullets(dtSec);
            updateEnemies(dtSec);
            updateScore(gameStage.getGameCharacterList());
            checkCharacterEnemyCollisions();

            if (stageManager != null) stageManager.update();

            javafx.application.Platform.runLater(() -> {
                try {
                    var gc = gameStage.getDebugGC();
                    gc.clearRect(0, 0, GameStage.WIDTH, GameStage.HEIGHT);
                    for (var p : gameStage.getPlatforms()) p.drawDebug(gc);
                    gc.setStroke(javafx.scene.paint.Color.LIME);
                    for (var c : gameStage.getGameCharacterList()) {
                        var hb = c.getHitbox();
                        gc.strokeRect(hb.getMinX(), hb.getMinY(), hb.getWidth(), hb.getHeight());
                    }
                } catch (Exception ignored) {}
            });

            long frameTime = (System.nanoTime() - now) / 1_000_000L;
            long sleepMs = (long)(1000.0/60.0) - frameTime;
            if (sleepMs < 1) sleepMs = 1;
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
        }
    }

}

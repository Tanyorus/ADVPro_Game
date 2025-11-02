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
    private StageManager stageManager;     // optional

    private final int frameRate  = 60;
    private final float interval = 1000.0f / frameRate;
    private volatile boolean running = true;

    // Invincibility after being hit (ms)
    private long invincibleUntil = 0;

    private volatile boolean debugDrawEnabled = true;

    // Edge detection for jump
    private boolean prevW = false, prevUp = false, prevSpace = false, prevE = false;

    // 0° = right, 90° = up, 180° = left, 270° = down
// Uses WASD / Arrow keys to form 8-way aim; if no aim keys, defaults to facing.
    private double getSnappedAimAngleDeg(GameCharacter c) {
        var keys = gameStage.getKeys();

        boolean up    = keys.isPressed(KeyCode.W)     || keys.isPressed(KeyCode.UP);
        boolean down  = keys.isPressed(KeyCode.S)     || keys.isPressed(KeyCode.DOWN);
        boolean left  = keys.isPressed(c.getLeftKey());
        boolean right = keys.isPressed(c.getRightKey());

        int dx = (right ? 1 : 0) - (left ? 1 : 0);
        int dy = (down  ? 1 : 0) - (up   ? 1 : 0);

        // If no directional input, aim where the character faces
        if (dx == 0 && dy == 0) {
            // If your character uses scaleX sign for facing, this keeps it simple:
            double facing = 0.0;                       // 0° = right
            try {
                facing = (c.getScaleX() >= 0) ? 0.0 : 180.0;
            } catch (Throwable ignored) {}
            return facing;
        }

        // Screen Y grows downward; negate dy so "up" is +90°.
        double angle = Math.toDegrees(Math.atan2(-dy, dx)); // range (-180,180]
        if (angle < 0) angle += 360.0;

        // Snap to nearest 45°
        double snapped = Math.round(angle / 45.0) * 45.0;
        if (snapped >= 360.0) snapped -= 360.0;
        return snapped;
    }


    public GameLoop(GameStage gameStage) {
        this.gameStage = gameStage;
    }

    public void attachStageManager(StageManager m) {
        this.stageManager = m;
    }

    public void stop() { running = false; }



    public void setDebugDrawEnabled(boolean enabled) {
        debugDrawEnabled = enabled;
        if (!enabled) {
            Platform.runLater(() -> {
                try {
                    var gc = gameStage.getDebugGC();
                    gc.clearRect(0, 0, GameStage.WIDTH, GameStage.HEIGHT);
                } catch (Exception ignored) {}
            });
        }
    }

    public void disableDebugDraw() {
        setDebugDrawEnabled(false);
    }
    // ===================== PLAYER =====================
    private void updateCharacters(List<GameCharacter> list, double dtSec) {
        if (list.isEmpty()) return;

        final boolean worldReady = gameStage.isWorldReady();
        final double dtPlayer = dtSec; // player not slowed

        for (GameCharacter c : list) {
            try {
                c.beginFrame();

                boolean wPressed     = gameStage.getKeys().isPressed(KeyCode.W);
                boolean upPressed    = gameStage.getKeys().isPressed(KeyCode.UP);
                boolean spacePressed = gameStage.getKeys().isPressed(KeyCode.SPACE);
                boolean left         = gameStage.getKeys().isPressed(c.getLeftKey());
                boolean right        = gameStage.getKeys().isPressed(c.getRightKey());
                boolean down         = gameStage.getKeys().isPressed(c.getDownKey());
                boolean ePressed     = gameStage.getKeys().isPressed(KeyCode.E);

                boolean upEdge    = (!prevW && wPressed) || (!prevUp && upPressed);
                boolean spaceEdge = (!prevSpace && spacePressed);
                boolean eEdge     = (!prevE && ePressed);

                if (down && !(left || right)) c.prone();
                else if (left && !right)      c.moveLeft();
                else if (right && !left)      c.moveRight();
                else                          c.stop();

                if (upEdge || spaceEdge) c.jump();
                c.handleDownKey(down);

                if (worldReady && !c.isDisabled()) {
                    double aimDeg = gameStage.getSnappedAimAngleDeg(c);

                    if (eEdge) {
                        GameCharacter.Shot laser = c.tryCreateLaser(gameStage.getKeys(), aimDeg);
                        if (laser != null) gameStage.fireLaser(c, laser);
                    }

                    Bullet b = c.tryCreateBullet(gameStage.getKeys(), aimDeg);
                    if (b != null) gameStage.addBullet(b);
                }

                c.repaint(dtPlayer * 1000.0);
                c.checkPlatformCollision(gameStage.getPlatforms());
                c.checkReachHighest();
                c.checkReachFloor();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        prevW     = gameStage.getKeys().isPressed(KeyCode.W)     && worldReady;
        prevUp    = gameStage.getKeys().isPressed(KeyCode.UP)    && worldReady;
        prevSpace = gameStage.getKeys().isPressed(KeyCode.SPACE) && worldReady;
        prevE     = gameStage.getKeys().isPressed(KeyCode.E)     && worldReady;
    }

    // ===================== BULLETS =====================
    private void updateBullets(double dtSeconds) {
        final double s        = gameStage.getTimeScale();
        final double dtEnemy  = dtSeconds * s;
        final double dtPlayer = dtSeconds;

        var bullets = gameStage.getBullets();
        var snapshot = new ArrayList<>(bullets);
        var toRemove = new ArrayList<Bullet>();

        for (Bullet b : snapshot) {
            if (b == null) continue;
            try {
                boolean enemyShot = false;
                try { enemyShot = b.isEnemyBullet(); } catch (Throwable ignored) {}
                b.update(enemyShot ? dtEnemy : dtPlayer);
            } catch (Throwable ignored) {}

            if (b.getX() < -100 || b.getX() > GameStage.WIDTH + 100 ||
                    b.getY() < -100 || b.getY() > GameStage.HEIGHT + 200) {
                toRemove.add(b);
            }
        }
        for (Bullet b : toRemove) gameStage.removeBullet(b);
    }

    // ===================== ENEMIES =====================
    private void updateEnemies(double dtSeconds) {
        if (gameStage.getGameCharacterList().isEmpty()) return;

        final boolean worldReady = gameStage.isWorldReady();
        final double scaled = dtSeconds * gameStage.getTimeScale();
        GameCharacter player = gameStage.getGameCharacterList().get(0);

        var snapshot = new ArrayList<>(gameStage.getEnemies());
        for (var enemy : snapshot) {
            try { enemy.update(scaled, player); } catch (Throwable ignored) {}
            if (worldReady) {
                try {
                    Bullet b = enemy.tryShoot(player);
                    if (b != null) gameStage.addBullet(b);
                } catch (Throwable ignored) {}
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
            for (var e : enemiesSnap) {
                if (c.getHitbox().intersects(e.getHitbox())) {
                    onPlayerHit(c);
                    return;
                }
            }
            for (Bullet b : bulletsSnap) {
                try {
                    if (b.isEnemyBullet() && c.getHitbox().intersects(b.getHitbox())) {
                        onPlayerHit(c);
                        PauseTransition delay = new PauseTransition(Duration.millis(10));
                        delay.setOnFinished(ev -> gameStage.removeBullet(b));
                        Platform.runLater(delay::play);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private void onPlayerHit(GameCharacter c) {
        long now = System.currentTimeMillis();

        c.loseLife();
        Platform.runLater(() -> gameStage.updateLivesHUD(c.getLives()));

        // Only respawn if still alive
        if (c.getLives() > 0) {
            c.respawn();
            invincibleUntil = now + 1500; // 1.5s i-frames
            return;
        }

        // Lives <= 0 → freeze player & show overlay (FX first, then stop loop)
        for (GameCharacter gc : gameStage.getGameCharacterList()) {
            gc.setDisable(true);
        }

        Platform.runLater(gameStage::showGameOverOverlay);

        // Give the FX thread a pulse to render overlay before stopping the loop
        PauseTransition delayStop = new PauseTransition(Duration.millis(200));
        delayStop.setOnFinished(e -> stop());
        Platform.runLater(delayStop::play);
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
            if (!gameStage.isWorldReady()) {
                prevW = prevUp = prevSpace = false;
                try { Thread.sleep(4); } catch (InterruptedException ignored) {}
                last = System.nanoTime();
                continue;
            }

            long now = System.nanoTime();
            double dtSec = (now - last) / 1_000_000_000.0;
            last = now;

            boolean wantSlow = gameStage.getKeys().isPressed(KeyCode.SHIFT);
            gameStage.tickSlowMo(wantSlow, dtSec);

            updateCharacters(gameStage.getGameCharacterList(), dtSec);
            updateBullets(dtSec);
            updateEnemies(dtSec);
            updateScore(gameStage.getGameCharacterList());
            checkCharacterEnemyCollisions();

            if (stageManager != null) stageManager.update();

            if (debugDrawEnabled) {
                Platform.runLater(() -> {
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
            }

            long frameTime = (System.nanoTime() - now) / 1_000_000L;
            long sleepMs = (long)(1000.0/60.0) - frameTime;
            if (sleepMs < 1) sleepMs = 1;
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
        }
    }
}

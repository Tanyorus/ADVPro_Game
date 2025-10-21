package advpro_game.controller;

import advpro_game.model.Bullet;
import advpro_game.model.Enemy;
import advpro_game.model.GameCharacter;
import advpro_game.model.Platform;
import advpro_game.view.GameStage;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DrawingLoop implements Runnable {
    private final GameStage gameStage;
    private final int frameRate;
    private final double intervalMs;
    private volatile boolean running = true;

    // Overlay hitbox rectangles for each character
    private final Map<GameCharacter, Rectangle> boxes = new HashMap<>();

    public DrawingLoop(GameStage gameStage) {
        this.gameStage = gameStage;
        this.frameRate = 60;
        this.intervalMs = 1000.0 / frameRate;
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long start = System.nanoTime();

            // Everything in this loop that touches JavaFX scene must run on FX thread
            javafx.application.Platform.runLater(this::drawFrameOnFxThread);

            // Fixed timestep sleep
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            long sleepMs = (long)Math.max(1, Math.round(intervalMs - elapsedMs));
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
            last = start;
        }
    }

    public void stop() { running = false; }

    // ===== FX-thread work below =====
    private void drawFrameOnFxThread() {
        ensureOverlayBoxes();
        syncOverlayBoxes();
        resolveBulletEnemyHits();  // keep this here so hits are responsive
        drawDebugCanvas();
    }

    /** Create overlay rectangles for each character (once). */
    private void ensureOverlayBoxes() {
        var overlay = gameStage.getDBoverlay();
        var chars = gameStage.getGameCharacterList();
        if (boxes.size() == chars.size()) return;

        for (GameCharacter gc : chars) {
            if (boxes.containsKey(gc)) continue;
            Rectangle r = new Rectangle();
            r.setFill(Color.TRANSPARENT);
            r.setStroke(Color.RED);
            r.setStrokeWidth(1.5);
            r.setMouseTransparent(true);
            boxes.put(gc, r);
            overlay.getChildren().add(r);
        }
    }

    /** Keep overlay rectangles aligned to each character’s hitbox. */
    private void syncOverlayBoxes() {
        for (GameCharacter gc : gameStage.getGameCharacterList()) {
            Rectangle2D hb = gc.getHitbox();
            Rectangle r = boxes.get(gc);
            if (r == null) continue;
            r.setX(hb.getMinX());
            r.setY(hb.getMinY());
            r.setWidth(hb.getWidth());
            r.setHeight(hb.getHeight());
        }
    }

    /** Check bullet→enemy collisions, remove bullets on hit, call enemy.hit(). */
    private void resolveBulletEnemyHits() {
        var bullets = gameStage.getBullets();
        var enemies = gameStage.getEnemies();

        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            Rectangle2D bb = b.getHitbox();

            boolean removed = false;
            for (Enemy e : enemies) {
                if (bb.intersects(e.getHitbox())) {
                    e.hit();
                    gameStage.getChildren().remove(b.getNode());
                    it.remove();
                    removed = true;
                    break;
                }
            }
            if (removed) continue;
        }
    }

    /** Draw platforms + hitboxes to the debug canvas. */
    private void drawDebugCanvas() {
        var gc = gameStage.getDebugGC();
        gc.clearRect(0, 0, GameStage.WIDTH, GameStage.HEIGHT);

        // Platforms
        for (Platform p : gameStage.getPlatforms()) {
            Rectangle2D r = p.getHitbox();
            gc.setStroke(p.isSolid() ? Color.RED : Color.CYAN);
            gc.strokeRect(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
        }

        // Characters
        gc.setStroke(Color.LIME);
        for (GameCharacter c : gameStage.getGameCharacterList()) {
            Rectangle2D hb = c.getHitbox();
            gc.strokeRect(hb.getMinX(), hb.getMinY(), hb.getWidth(), hb.getHeight());
        }

        // Bullets
        gc.setStroke(Color.YELLOW);
        for (Bullet b : gameStage.getBullets()) {
            Rectangle2D hb = b.getHitbox();
            gc.strokeRect(hb.getMinX(), hb.getMinY(), hb.getWidth(), hb.getHeight());
        }

        // Enemies
        gc.setStroke(Color.ORANGE);
        for (Enemy e : gameStage.getEnemies()) {
            Rectangle2D hb = e.getHitbox();
            gc.strokeRect(hb.getMinX(), hb.getMinY(), hb.getWidth(), hb.getHeight());
        }
    }
}

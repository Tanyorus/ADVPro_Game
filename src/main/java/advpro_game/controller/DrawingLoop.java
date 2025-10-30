package advpro_game.controller;

import advpro_game.model.Bullet;
import advpro_game.model.Enemy;
import advpro_game.model.GameCharacter;
import advpro_game.view.GameStage;

public class DrawingLoop implements Runnable {
    private final GameStage gameStage;
    private final int frameRate = 60;
    private final float interval = 1000.0f / frameRate;
    private volatile boolean running = true;

    public DrawingLoop(GameStage gameStage) { this.gameStage = gameStage; }
    public void stop() { running = false; }

    private void stepCharacters(double dtMs) {
        for (GameCharacter c : gameStage.getGameCharacterList()) {
            c.repaint(dtMs);
            c.checkPlatformCollision(gameStage.getPlatforms());
            c.checkReachHighest();
            c.checkReachFloor();

            // hard clamp to world
            int x = c.getX();
            int w = c.getCharacterWidth();
            if (x < 0) c.setX(0);
            else if (x + w > GameStage.WIDTH) c.setX(GameStage.WIDTH - w);
        }
    }

    private void stepBullets(double dtSec) {
        for (Bullet b : new java.util.ArrayList<>(gameStage.getBullets())) {
            b.update(dtSec);

            // cull offscreen
            if (b.getX() < -120 || b.getX() > GameStage.WIDTH + 120 ||
                    b.getY() < -120 || b.getY() > GameStage.HEIGHT + 240) {
                gameStage.removeBullet(b);
                continue;
            }

            boolean hit = false;

            // Player bullets hit enemies
            if (!b.isEnemyBullet()) {
                for (Enemy e : gameStage.getEnemies()) {
                    if (b.getHitbox().intersects(e.getHitbox())) {
                        boolean dead = e.hit(b.getDamage());

                        // score (player 0)
                        if (!gameStage.getGameCharacterList().isEmpty()) {
                            var p = gameStage.getGameCharacterList().get(0);
                            p.addScore(dead ? 20 : 10);
                        }

                        gameStage.showHitFlash(b.getX(), b.getY());
                        gameStage.removeBullet(b);
                        hit = true;
                        break;
                    }
                }
            }
            // Enemy bullets hit player
            else {
                for (GameCharacter c : gameStage.getGameCharacterList()) {
                    if (b.getHitbox().intersects(c.getHitbox())) {
                        c.loseLife();
                        gameStage.updateLivesHUD(c.getLives());

                        if (c.getLives() > 0) {
                            c.respawn();
                        } else {
                            System.out.println("Game Over - Hit by enemy bullet!");
                        }

                        gameStage.showHitFlash(b.getX(), b.getY());
                        gameStage.removeBullet(b);
                        hit = true;
                        break;
                    }
                }
            }

            if (hit) continue;
        }
    }

    private void paintDebug() {
        javafx.application.Platform.runLater(() -> {
            var gc = gameStage.getDebugGC();
            gc.clearRect(0, 0, GameStage.WIDTH, GameStage.HEIGHT);

            // platforms
            for (var p : gameStage.getPlatforms()) {
                var r = p.getHitbox();
                gc.setStroke(p.isSolid() ? javafx.scene.paint.Color.RED
                        : javafx.scene.paint.Color.CYAN);
                gc.strokeRect(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
            }
            // character hitboxes
            gc.setStroke(javafx.scene.paint.Color.LIME);
            for (var c : gameStage.getGameCharacterList()) {
                var hb = c.getHitbox();
                gc.strokeRect(hb.getMinX(), hb.getMinY(), hb.getWidth(), hb.getHeight());
            }
        });
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dtMs  = (now - last) / 1_000_000.0;
            double dtSec = dtMs / 1000.0;
            last = now;

            stepCharacters(dtMs);
            stepBullets(dtSec);
            paintDebug();

            long frameTime = (System.nanoTime() - now)/1_000_000L;
            long sleepMs = Math.max(1, (long)interval - frameTime);
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
        }
    }
}
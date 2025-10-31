package advpro_game.controller;

import advpro_game.model.Bullet;
import advpro_game.model.GameCharacter;
import advpro_game.view.GameStage;
import javafx.scene.input.KeyCode;

import java.util.Iterator;
import java.util.List;

public class GameLoop implements Runnable {
    private final GameStage gameStage;
    private final StageManager stageManager;
    private final int frameRate = 60;
    private final float interval = 1000.0f / frameRate;
    private volatile boolean running = true;
    private long invincibleUntil = 0;  // Invincibility timer

    public GameLoop(GameStage gameStage) {
        this.gameStage = gameStage;
        this.stageManager = new StageManager(gameStage);
        this.stageManager.start();
    }

    public void stop() { running = false; }

    // === per-frame ===
    private void updateCharacters(List<GameCharacter> list) {
        for (GameCharacter c : list) {
            c.beginFrame();

            boolean left  = gameStage.getKeys().isPressed(c.getLeftKey());
            boolean right = gameStage.getKeys().isPressed(c.getRightKey());
            boolean up    = gameStage.getKeys().isPressed(c.getUpKey());
            boolean down  = gameStage.getKeys().isPressed(c.getDownKey());

            if (up && right)       c.jumpForward(1);
            else if (up && left)   c.jumpForward(-1);
            else if (left && right) c.stop();
            else if (left)         c.moveLeft();
            else if (right)        c.moveRight();
            else                   c.stop();

            if (up) c.jump();
            if ((down && !left && !right) || (down && !(up))) c.prone();

            c.handleDownKey(down);

            Bullet b = c.tryCreateBullet(gameStage.getKeys());
            if (b != null) gameStage.addBullet(b);
        }
    }

    private void updateBullets(double dtSeconds) {
        dtSeconds *= gameStage.getTimeScale();
        var bullets = gameStage.getBullets();
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            b.update(dtSeconds);
            if (b.getX() < -100 || b.getX() > GameStage.WIDTH + 100 ||
                    b.getY() < -100 || b.getY() > GameStage.HEIGHT + 200) {
                it.remove();
                gameStage.removeBullet(b);
            }
        }
    }

    private void updateScore(List<GameCharacter> chars) {
        if (!gameStage.getScoreList().isEmpty() && !chars.isEmpty()) {
            gameStage.getScoreList().get(0).setPoint(chars.get(0).getScore());
            gameStage.updateLivesHUD(chars.get(0).getLives());
        }
    }

    private void updateEnemies(double dtSeconds) {
        if (gameStage.getGameCharacterList().isEmpty()) return;

        GameCharacter player = gameStage.getGameCharacterList().get(0);

        for (var enemy : gameStage.getEnemies()) {
            // Update enemy movement
            enemy.update(dtSeconds, player);

            // Try to shoot at player
            Bullet b = enemy.tryShoot(player);
            if (b != null) {
                gameStage.addBullet(b);
                System.out.println("Enemy shot a bullet!");
            }
        }
    }

    private void checkCharacterEnemyCollisions() {
        long now = System.currentTimeMillis();

        // Skip collision check if player is invincible
        if (now < invincibleUntil) {
            return;
        }

        for (GameCharacter c : gameStage.getGameCharacterList()) {
            for (var e : new java.util.ArrayList<>(gameStage.getEnemies())) {
                if (c.getHitbox().intersects(e.getHitbox())) {
                    c.loseLife();
                    gameStage.updateLivesHUD(c.getLives());

                    // Always respawn when hit, even if lives reach 0
                    c.respawn();
                    // Give 1.5 seconds of invincibility after respawn
                    invincibleUntil = now + 1500;

                    if (c.getLives() <= 0) {
                        System.out.println("Game Over!");
                    }

                    // Enemy does NOT die - just the player takes damage
                    break;
                }
            }
        }
    }

    @Override
    public void run() {
        long last = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            double dt = (now - last) / 1_000_000_000.0;
            last = now;

            boolean wantSlow = gameStage.getKeys().isPressed(KeyCode.SHIFT);
            gameStage.tickSlowMo(wantSlow, dt);

            updateCharacters(gameStage.getGameCharacterList());

            for (GameCharacter c : gameStage.getGameCharacterList()) {
                c.checkPlatformCollision(gameStage.getPlatforms());
                c.checkReachHighest();
                c.checkReachFloor();
                c.repaint(16.7 * gameStage.getTimeScale());
            }

            updateBullets(dt);
            updateScore(gameStage.getGameCharacterList());
            updateEnemies(dt);
            checkCharacterEnemyCollisions();
            stageManager.update();

            long frameTime = (System.nanoTime() - now) / 1_000_000L;
            long sleepMs = (long) interval - frameTime;
            if (sleepMs < 1) sleepMs = 1;
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
        }
    }
}
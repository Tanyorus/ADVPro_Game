package advpro_game.controller;

import advpro_game.model.Bullet;
import advpro_game.model.GameCharacter;
import advpro_game.view.GameStage;
import javafx.scene.input.KeyCode;

import java.util.Iterator;
import java.util.List;

public class GameLoop implements Runnable {
    private final GameStage gameStage;
    private final int frameRate = 60;
    private final float interval = 1000.0f / frameRate;
    private volatile boolean running = true;

    public GameLoop(GameStage gameStage) { this.gameStage = gameStage; }

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
            // Prone when pressing down without move OR while holding down
            if ((down && !left && !right) || (down && !(up))) c.prone();

            c.handleDownKey(down);

            // Fire (space or mouse)
            Bullet b = c.tryCreateBullet(gameStage.getKeys());
            if (b != null) gameStage.addBullet(b);
        }
    }

    private void updateBullets(double dtSeconds) {
        dtSeconds *= gameStage.getTimeScale(); // slow-time
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
        // Single player HUD score
        if (!gameStage.getScoreList().isEmpty() && !chars.isEmpty()) {
            gameStage.getScoreList().get(0).setPoint(chars.get(0).getScore());
            // update lives HUD too
            gameStage.updateLivesHUD(chars.get(0).getLives());
        }
    }

    @Override
    public void run() {
        long last = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            double dt = (now - last) / 1_000_000_000.0;  // seconds
            last = now;

            // Slow-time: hold LEFT SHIFT
            boolean wantSlow = gameStage.getKeys().isPressed(KeyCode.SHIFT);
            gameStage.tickSlowMo(wantSlow, dt);

            updateCharacters(gameStage.getGameCharacterList());

            // Physics & collisions (platforms)
            for (GameCharacter c : gameStage.getGameCharacterList()) {
                c.checkPlatformCollision(gameStage.getPlatforms());
                c.checkReachHighest();
                c.checkReachFloor();
                // Advance animation/position with time scale for animations
                c.repaint(16.7 * gameStage.getTimeScale());
            }

            updateBullets(dt);
            updateScore(gameStage.getGameCharacterList());

            long frameTime = (System.nanoTime() - now) / 1_000_000L;
            long sleepMs = (long) interval - frameTime;
            if (sleepMs < 1) sleepMs = 1;
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
        }
    }
}

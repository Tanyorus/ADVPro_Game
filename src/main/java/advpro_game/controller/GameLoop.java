package advpro_game.controller;

import advpro_game.model.Bullet;
import advpro_game.model.GameCharacter;
import advpro_game.view.GameStage;
import advpro_game.view.Score;
import javafx.application.Platform;

import java.util.List;

public class GameLoop implements Runnable {
    private final GameStage gameStage;
    private final int frameRate = 60;
    private final double intervalMs = 1000.0 / frameRate;
    private volatile boolean running = true;

    public GameLoop(GameStage gameStage) {
        this.gameStage = gameStage;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long start = System.nanoTime();
            double dtMs = (start - last) / 1_000_000.0; // ms
            last = start;

            // Do all scene work on FX thread
            Platform.runLater(() -> updateFrameOnFxThread(dtMs));

            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            long sleepMs = Math.max(1, (long)(intervalMs - elapsedMs));
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
        }
    }

    private void updateFrameOnFxThread(double dtMs) {
        // edge-trigger helpers (optional; safe even if unused)
        gameStage.getKeys().beginFrame();

        // --- Players ---
        List<GameCharacter> chars = gameStage.getGameCharacterList();
        for (GameCharacter gc : chars) {
            gc.beginFrame();

            boolean left  = gameStage.getKeys().isPressed(gc.getLeftKey());
            boolean right = gameStage.getKeys().isPressed(gc.getRightKey());
            boolean up    = gameStage.getKeys().isPressed(gc.getUpKey());
            boolean down  = gameStage.getKeys().isPressed(gc.getDownKey());

            // movement intents
            if (up && right)      gc.jumpForward(1);
            else if (up && left)  gc.jumpForward(-1);
            else if (left && right) gc.stop();
            else if (left)        gc.moveLeft();
            else if (right)       gc.moveRight();
            else                  gc.stop();

            if (up) gc.jump();
            gc.handleDownKey(down);
            if (down && !left && !right) gc.prone();

            // mouse fire (8-way)
            Bullet created = gc.tryCreateBullet(gameStage.getKeys());
            if (created != null) gameStage.addBullet(created);

            // keyboard fire (Space) — straight shot
            if (gameStage.getKeys().isPressed(javafx.scene.input.KeyCode.SPACE)) {
                gameStage.addBullet(gc.shoot());
            }

            // physics + collisions
            gc.repaint(dtMs); // applies gravity & horizontal motion
            gc.checkPlatformCollision(gameStage.getPlatforms());
            gc.checkReachHighest();
            gc.checkReachFloor();
            gc.checkReachGameWall();
        }

        // --- Bullets ---
        updateBullets( dtMs / 1000.0 );

        // --- HUD ---
        updateScore(gameStage.getScoreList(), chars);
    }

    private void updateScore(List<Score> scoreList, List<GameCharacter> list) {
        for (int i = 0; i < scoreList.size() && i < list.size(); i++) {
            scoreList.get(i).setPoint(list.get(i).getScore());
        }
    }

    private void updateBullets(double dtSeconds) {
        var bullets = gameStage.getBullets();
        var it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            b.update(dtSeconds);
            if (b.getX() < -100 || b.getX() > GameStage.WIDTH + 100
                    || b.getY() < -100 || b.getY() > GameStage.HEIGHT + 200) {
                it.remove();
                gameStage.removeBullet(b);
            }
        }
    }
}

package advpro_game.model;

import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class Enemy {
    protected double x, y, w, h;
    private int hp = 1;
    protected final Rectangle node;

    // Movement
    protected double vx = 0;  // horizontal velocity
    protected double moveSpeed = 30.0;  // pixels per second
    protected int direction = -1;  // -1 = left, 1 = right

    // Shooting
    protected long lastShotTime = 0;
    protected int shootCooldownMs = 2000;  // 2 seconds between shots
    protected double shootRange = 400;  // can shoot if player within this range

    public Enemy(double x, double y, double w, double h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        node = new Rectangle(w, h, Color.DARKRED);
        node.setTranslateX(x);
        node.setTranslateY(y);
    }

    public Rectangle2D getHitbox() { return new Rectangle2D(x, y, w, h); }
    public Rectangle getNode() { return node; }

    // HP API
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = Math.max(0, hp); }
    public void addHp(int delta) { setHp(this.hp + delta); }
    public boolean isDead() { return hp <= 0; }

    /** returns true if dead after hit */
    public boolean hit(int dmg) {
        setHp(hp - Math.max(0, dmg));
        node.setFill(isDead()? Color.GRAY : Color.FIREBRICK);
        return isDead();
    }

    // Movement API
    public void update(double dtSeconds, GameCharacter player) {
        if (isDead()) return;  // Don't move if dead

        // Simple AI: move toward player
        double playerX = player.getX() + player.getCharacterWidth() / 2.0;  // player center
        double enemyX = x + w / 2.0;  // enemy center
        double dx = playerX - enemyX;

        if (Math.abs(dx) > 50) {  // if not too close
            direction = dx > 0 ? 1 : -1;
            vx = direction * moveSpeed;
            double oldX = x;
            x += vx * dtSeconds;

            // Keep enemies within screen bounds
            if (x < 0) x = 0;
            if (x > 800 - w) x = 800 - w;

            // Debug: print when enemy moves
            if (Math.abs(x - oldX) > 0.1) {
                System.out.println("Enemy moving: x=" + (int)x + " toward player at " + (int)playerX);
            }
        } else {
            vx = 0;  // stop if close enough
        }

        // Update visual position on FX thread
        final double finalX = x;
        final double finalY = y;
        javafx.application.Platform.runLater(() -> {
            node.setTranslateX(finalX);
            node.setTranslateY(finalY);
        });
    }

    // Shooting API
    public Bullet tryShoot(GameCharacter player) {
        long now = System.currentTimeMillis();
        if (now - lastShotTime < shootCooldownMs) {
            return null;  // cooldown not ready
        }

        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double distance = Math.hypot(dx, dy);

        if (distance > shootRange) {
            return null;  // player too far
        }

        lastShotTime = now;

        // Calculate bullet spawn position (center of enemy)
        double bulletX = x + w / 2;
        double bulletY = y + h / 2;

        // Shoot toward player - mark as enemy bullet
        return new Bullet(bulletX, bulletY, dx, dy, 300.0, 1, 1.6, true);
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return w; }
    public double getHeight() { return h; }
}
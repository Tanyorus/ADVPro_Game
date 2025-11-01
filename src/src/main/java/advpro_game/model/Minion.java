package advpro_game.model;

import javafx.scene.paint.Color;

public class Minion extends Enemy {
    private BulletConfig bulletConfig = null;

    // Original constructor (for backward compatibility)
    public Minion(double x, double y) {
        super(x, y, 24, 40);
        setHp(1);
        this.moveSpeed = 0;
        this.shootCooldownMs = 5000;  // 5 seconds
        this.shootRange = 300;
        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * 3000);
    }

    // Constructor with custom sprite parameters
    public Minion(double x, double y, double w, double h, String spritePath,
                  int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(1);
        this.moveSpeed = 0;
        this.shootCooldownMs = 3000;
        this.shootRange = 9999;
        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * 3000);

        // Define animations for custom sprite
        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 150
            ));
            sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 100
            ));
        }

        // Set color for fallback rectangle
        if (fallbackNode != null) {
            fallbackNode.setFill(Color.DARKRED);
        }
    }

    // Full constructor with custom sprite parameters, shoot cooldown, and shoot range
    public Minion(double x, double y, double w, double h, String spritePath,
                  int frameCount, int columns, int rows, int frameWidth, int frameHeight,
                  int shootCooldownMs, int shootRange) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(1);
        this.moveSpeed = 0;
        this.shootCooldownMs = shootCooldownMs;
        this.shootRange = shootRange;
        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * 3000);

        // Define animations for custom sprite
        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 150
            ));
            sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 100
            ));
        }

        // Set color for fallback rectangle
        if (fallbackNode != null) {
            fallbackNode.setFill(Color.DARKRED);
        }
    }

    // Set custom bullet sprite (static image)
    public void setCustomBullet(String spritePath, double scale) {
        this.bulletConfig = new BulletConfig(spritePath, scale);
    }

    // Set custom animated bullet sprite
    public void setCustomAnimatedBullet(String spritePath, double scale, int frameCount,
                                        int columns, int rows, int frameWidth, int frameHeight) {
        this.bulletConfig = new BulletConfig(spritePath, scale, frameCount, columns, rows,
                frameWidth, frameHeight);
    }

    // Set bullet config directly
    public void setBulletConfig(BulletConfig config) {
        this.bulletConfig = config;
    }

    @Override
    public Bullet tryShoot(GameCharacter player) {
        long now = System.currentTimeMillis();
        if (now - lastShotTime < shootCooldownMs) {
            return null;
        }

        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double distance = Math.hypot(dx, dy);

        if (distance > shootRange) {
            return null;
        }

        lastShotTime = now;

        double bulletX = x + w / 2;
        double bulletY = y + h / 2;

        // Use custom bullet if set, otherwise use default
        if (bulletConfig != null) {
            return bulletConfig.createBullet(bulletX, bulletY, dx, dy, 300.0, 1, true);
        } else {
            return new Bullet(bulletX, bulletY, dx, dy, 300.0, 1, 1.6, true);
        }
    }

    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        if (!dead && fallbackNode != null) {
            fallbackNode.setFill(Color.FIREBRICK);
        }
        return dead;
    }
}
package advpro_game.model;

import javafx.scene.paint.Color;

public class Boss extends Enemy {
    private static final int BASE_HP = 12;
    private final int bossType;
    private BulletConfig bulletConfig = null;

    // Original constructor (for backward compatibility)
    public Boss(double x, double y) {
        this(x, y, 48, 72, "/advpro_game/assets/BossSprite.png", 8, 8, 1, 48, 72, 1);
    }

    // New constructor with custom sprite parameters and boss type
    public Boss(double x, double y, double w, double h, String spritePath,
                int frameCount, int columns, int rows, int frameWidth, int frameHeight, int bossType) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        this.bossType = bossType;
        setHp(BASE_HP);
        this.moveSpeed = 0;
        this.shootCooldownMs = 1000;
        this.shootRange = 99999;

        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * 1000);

        // Set color for fallback rectangle
        if (fallbackNode != null) {
            fallbackNode.setFill(getBossColor());
        }

        // Define animations for boss (customize based on your sprite sheets)
        if (sprite != null) {
            defineAnimationsForBossType();
        }
    }

    // Set custom bullet sprite for this boss (static image)
    public void setCustomBullet(String spritePath, double scale) {
        this.bulletConfig = new BulletConfig(spritePath, scale);
    }

    // Set custom animated bullet sprite for this boss
    public void setCustomAnimatedBullet(String spritePath, double scale, int frameCount,
                                        int columns, int rows, int frameWidth, int frameHeight) {
        this.bulletConfig = new BulletConfig(spritePath, scale, frameCount, columns, rows,
                frameWidth, frameHeight);
    }

    // Set bullet config directly
    public void setBulletConfig(BulletConfig config) {
        this.bulletConfig = config;
    }

    // Get color based on boss type
    private Color getBossColor() {
        return switch (bossType) {
            case 1 -> Color.DARKVIOLET;
            case 2 -> Color.DARKRED;
            case 3 -> Color.DARKGREEN;
            default -> Color.DARKVIOLET;
        };
    }

    // Define animations based on boss type
    private void defineAnimationsForBossType() {
        switch (bossType) {
            case 1 -> {
                // Stage 1 Boss animations
                sprite.define(AnimatedSprite.Action.javaShoot, new AnimatedSprite.ActionSpec(
                        0, 0, 2, 2, 90, 10, 90000000
                ));
            }
            case 2 -> {
                // Stage 2 Boss animations
                sprite.define(AnimatedSprite.Action.javaShoot, new AnimatedSprite.ActionSpec(
                        0, 2, 2, 2, 112, 112, 99999999
                ));
            }
            case 3 -> {
                // Stage 3 Boss animations
                sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                        0, 0, 4, 8, (int)w, (int)h, 150
                ));
                sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                        4, 0, 4, 8, (int)w, (int)h, 80
                ));
            }
            default -> {
                // Default animations
                sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                        0, 0, 4, 8, (int)w, (int)h, 180
                ));
                sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                        4, 0, 4, 8, (int)w, (int)h, 100
                ));
            }
        }
    }

    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        if (!dead && fallbackNode != null) {
            if (getHp() > BASE_HP / 2) {
                fallbackNode.setFill(Color.MEDIUMPURPLE);
            } else {
                fallbackNode.setFill(Color.ORCHID);
            }
        }
        return dead;
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

        // Play shoot animation
        if (sprite != null) {
            javafx.application.Platform.runLater(() -> {
                sprite.setAction(AnimatedSprite.Action.shoot);
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
                pause.setOnFinished(e -> sprite.setAction(AnimatedSprite.Action.idle));
                pause.play();
            });
        }

        // Use custom bullet if set, otherwise use default
        if (bulletConfig != null) {
            return bulletConfig.createBullet(bulletX, bulletY, dx, dy, 300.0, 1, true);
        } else {
            return new Bullet(bulletX, bulletY, dx, dy, 300.0, 1, 1.6, true);
        }
    }

    public int getBossType() {
        return bossType;
    }
}
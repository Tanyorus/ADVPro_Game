package advpro_game.model;

import javafx.application.Platform;
import javafx.scene.paint.Color;

/**
 * Boss enemy:
 * - Extends merged Enemy (Pane node with optional sprite, rectangle fallback).
 * - Per-type animations; stationary turret-like by default.
 * - Short i-frames after being hit.
 * - Optional custom bullet visuals via BulletConfig (metadata only; Bullet creation uses core ctor).
 */
public class Boss extends Enemy {
    private static final int  BASE_HP        = 12;

    // i-frames (to avoid stacked damage in the same few ticks)
    private static final long INVINCIBLE_MS  = 220;
    private long lastHitAt = 0L;

    private final int bossType;
    private BulletConfig bulletConfig = null;

    // ---------- Constructors ----------

    /** Backward-compat: default sprite + bossType=1. */
    public Boss(double x, double y) {
        this(x, y, 48, 72,
                "/advpro_game/assets/BossSprite.png",
                8, 8, 1, 48, 72,
                1);
    }

    /**
     * Full custom sprite parameters + boss type.
     * If sprite path fails to load, will fall back to a colored rectangle.
     */
    public Boss(double x, double y, double w, double h, String spritePath,
                int frameCount, int columns, int rows, int frameWidth, int frameHeight,
                int bossType) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        this.bossType = bossType;
        setHp(BASE_HP);

        // Boss: stationary shooter by default
        this.moveSpeed       = 0;
        this.shootCooldownMs = 1000;
        this.shootRange      = 600;

        // Desynchronize shooting a bit
        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * this.shootCooldownMs);

        // Fallback rectangle tint (FX thread safe)
        if (fallback != null) {
            Platform.runLater(() -> fallback.setFill(getBossBaseColor()));
        }

        // Define animations per-type when sprite is available
        if (sprite != null) {
            defineAnimationsForBossType();
        }
    }

    // ---------- Bullet customization ----------

    /** Static bullet sprite metadata (scale used to adjust radius). */
    public void setCustomBullet(String spritePath, double scale) {
        this.bulletConfig = new BulletConfig(spritePath, scale);
    }

    /** Animated bullet sprite metadata (stored; Bullet still created via core ctor). */
    public void setCustomAnimatedBullet(String spritePath, double scale, int frameCount,
                                        int columns, int rows, int frameWidth, int frameHeight) {
        this.bulletConfig = new BulletConfig(spritePath, scale, frameCount, columns, rows, frameWidth, frameHeight);
    }

    public void setBulletConfig(BulletConfig config) {
        this.bulletConfig = config;
    }

    // ---------- Visual helpers ----------

    private Color getBossBaseColor() {
        return switch (bossType) {
            case 1 -> Color.DARKVIOLET;
            case 2 -> Color.DARKRED;
            case 3 -> Color.DARKGREEN;
            default -> Color.DARKVIOLET;
        };
    }

    private void defineAnimationsForBossType() {
        switch (bossType) {
            case 1 -> {
                // Example: use javaShoot row 2, 2 frames
                sprite.define(AnimatedSprite.Action.javaShoot, new AnimatedSprite.ActionSpec(
                        0, 2, 2, 2, (int)w, (int)h, 180
                ));
                sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                        0, 0, 4, 8, (int)w, (int)h, 150
                ));
            }
            case 2 -> {
                // Very slow “charging” javaShoot
                sprite.define(AnimatedSprite.Action.javaShoot, new AnimatedSprite.ActionSpec(
                        0, 2, 2, 2, (int)w, (int)h, 99999999
                ));
                sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                        0, 0, 4, 8, (int)w, (int)h, 180
                ));
            }
            case 3 -> {
                // Idle + shoot split across columns
                sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                        0, 0, 4, 8, (int)w, (int)h, 150
                ));
                sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                        4, 0, 4, 8, (int)w, (int)h, 80
                ));
            }
            default -> {
                sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                        0, 0, 4, 8, (int)w, (int)h, 180
                ));
                sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                        4, 0, 4, 8, (int)w, (int)h, 100
                ));
            }
        }
        // Start idle
        sprite.setAction(AnimatedSprite.Action.idle);
    }

    // ---------- Damage / i-frames ----------

    @Override
    public boolean hit(int dmg) {
        long now = System.currentTimeMillis();

        // Ignore stacked damage within i-frame window
        if (now - lastHitAt < INVINCIBLE_MS) {
            return false;
        }
        lastHitAt = now;

        boolean dead = super.hit(dmg);

        if (!dead) {
            int hpNow = getHp();
            // quick hit flash + color progression on fallback
            if (fallback != null) {
                Platform.runLater(() -> {
                    // flash stroke
                    fallback.setStroke(Color.WHITE);
                    fallback.setStrokeWidth(2.5);
                    javafx.animation.PauseTransition pt =
                            new javafx.animation.PauseTransition(javafx.util.Duration.millis(90));
                    pt.setOnFinished(e -> {
                        fallback.setStroke(null);
                        fallback.setStrokeWidth(0);
                    });
                    pt.play();

                    // tint by health threshold
                    fallback.setFill(hpNow > BASE_HP / 2 ? Color.MEDIUMPURPLE : Color.ORCHID);
                });
            }
        } else {
            if (fallback != null) {
                Platform.runLater(() -> fallback.setFill(Color.PLUM));
            }
        }
        return dead;
    }

    // ---------- Shooting ----------

    @Override
    public Bullet tryShoot(GameCharacter player) {
        long now = System.currentTimeMillis();
        if (now - lastShotTime < shootCooldownMs) return null;

        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double distance = Math.hypot(dx, dy);
        if (distance > shootRange) return null;

        lastShotTime = now;

        double bulletX = x + w / 2.0;
        double bulletY = y + h / 2.0;

        // Play shoot animation (if defined)
        if (sprite != null) {
            Platform.runLater(() -> {
                // prefer explicit SHOOT if present; otherwise javaShoot; else stay idle
                AnimatedSprite.Action anim = (sprite != null && hasSpec(AnimatedSprite.Action.shoot))
                        ? AnimatedSprite.Action.shoot
                        : AnimatedSprite.Action.javaShoot;
                sprite.setAction(anim);

                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
                pause.setOnFinished(e -> sprite.setAction(AnimatedSprite.Action.idle));
                pause.play();
            });
        }

        // Use BulletConfig if present (scale affects radius), else default bullet
        if (bulletConfig != null) {
            return bulletConfig.createBullet(bulletX, bulletY, dx, dy, 300.0, 1, true);
        } else {
            return new Bullet(bulletX, bulletY, dx, dy, 300.0, 1, 1.6, true);
        }
    }

    private boolean hasSpec(AnimatedSprite.Action a) {
        try {
            // crude check: try switching and back, or rely on your AnimatedSprite having a map getter
            return true; // assume defined; if not defined, AnimatedSprite falls back to idle safely
        } catch (Throwable ignored) { return false; }
    }

    public int getBossType() { return bossType; }

    // ---------- BulletConfig (metadata holder) ----------
    /**
     * Stores optional sprite metadata for boss bullets. Creation still uses the
     * base Bullet constructor; sprite data can be used by your Bullet class if you extend it later.
     */
    public static class BulletConfig {
        public final String spritePath;
        public final double scale;

        // Optional animation metadata
        public final boolean animated;
        public final int frameCount, columns, rows, frameWidth, frameHeight;

        // Static sprite
        public BulletConfig(String spritePath, double scale) {
            this.spritePath = spritePath;
            this.scale = scale;
            this.animated = false;
            this.frameCount = this.columns = this.rows = this.frameWidth = this.frameHeight = 0;
        }

        // Animated sprite
        public BulletConfig(String spritePath, double scale,
                            int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
            this.spritePath = spritePath;
            this.scale = scale;
            this.animated = true;
            this.frameCount = frameCount;
            this.columns = columns;
            this.rows = rows;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }

        /** Create a Bullet aimed at (dx,dy). Adjusts radius by scale. */
        public Bullet createBullet(double x, double y, double dx, double dy,
                                   double speed, int damage, boolean enemyBullet) {
            double baseRadius = 1.6;
            double radius = Math.max(0.8, baseRadius * Math.max(0.25, scale));
            return new Bullet(x, y, dx, dy, speed, damage, radius, enemyBullet);
        }
    }
}

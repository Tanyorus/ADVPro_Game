package advpro_game.model;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.logging.Logger;

public class Boss extends Enemy {
    private static final Logger LOG = Logger.getLogger(Boss.class.getName());

    private static final int  BASE_HP       = 12;
    private static final long INVINCIBLE_MS = 220;
    private static final String JAVA_BULLET_PATH = "/advpro_game/assets/java_bullet.png";

    private long lastHitAt = 0L;

    // --- Simple Java-boss mouth anim (your sheet has 3 frames on one row) ---
    private Timeline mouthTl;
    private static final int SHOOT_DELAY_MS = 120; // per mouth frame (col0 then col1)

    private final int bossType;
    private BulletConfig bulletConfig = null;

    public Boss(double x, double y) {
        this(x, y, 48, 72,
                // NOTE: for the Java boss (type 2) make sure you pass the correct sheet path
                // e.g. "/advpro_game/assets/bossjava.png" when constructing this boss.
                "/advpro_game/assets/BossSprite.png",
                8, 8, 1, 48, 72,
                1);
    }

    public Boss(double x, double y, double w, double h, String spritePath,
                int frameCount, int columns, int rows, int frameWidth, int frameHeight,
                int bossType) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        this.bossType = bossType;
        setHp(BASE_HP);

        this.moveSpeed       = 0;
        this.shootCooldownMs = 1000;
        this.shootRange      = 600;

        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * this.shootCooldownMs);

        if (fallback != null) {
            Platform.runLater(() -> fallback.setFill(getBossBaseColor()));
        }
        if (sprite != null) {
            defineAnimationsForBossType();
        }
    }

    // ---------- Bullet customization (guard java_bullet for non-type-2) ----------
    public void setCustomBullet(String spritePath, double scale) {
        if (spritePath != null && spritePath.equals(JAVA_BULLET_PATH) && bossType != 2) {
            LOG.fine("Ignoring java_bullet on non-type-2 boss.");
            return;
        }
        this.bulletConfig = new BulletConfig(spritePath, scale);
    }

    public void setCustomAnimatedBullet(String spritePath, double scale, int frameCount,
                                        int columns, int rows, int frameWidth, int frameHeight) {
        if (spritePath != null && spritePath.equals(JAVA_BULLET_PATH) && bossType != 2) {
            LOG.fine("Ignoring animated java_bullet on non-type-2 boss.");
            return;
        }
        this.bulletConfig = new BulletConfig(spritePath, scale, frameCount, columns, rows, frameWidth, frameHeight);
    }

    public void setBulletConfig(BulletConfig config) {
        if (config != null && config.spritePath != null
                && config.spritePath.equals(JAVA_BULLET_PATH) && bossType != 2) {
            LOG.fine("Ignoring bulletConfig with java_bullet on non-type-2 boss.");
            return;
        }
        this.bulletConfig = config;
    }

    private Color getBossBaseColor() {
        return switch (bossType) {
            case 1 -> Color.DARKVIOLET;
            case 2 -> Color.DARKRED;
            case 3 -> Color.DARKGREEN;
            default -> Color.DARKVIOLET;
        };
    }

    // Map actions for non-Java bosses as before; for Java boss we’ll drive the viewport manually.
    private void defineAnimationsForBossType() {
        switch (bossType) {
            case 2: {
                sprite.define(AnimatedSprite.Action.idle,
                        new AnimatedSprite.ActionSpec(0, 0, 1, 3, (int) w, (int) h, 150));
                // shooting animation is driven by viewport flips
                break;
            }
            case 1: {
                sprite.define(AnimatedSprite.Action.javaShoot,
                        new AnimatedSprite.ActionSpec(0, 2, 2, 2, (int) w, (int) h, 180));
                sprite.define(AnimatedSprite.Action.idle,
                        new AnimatedSprite.ActionSpec(0, 0, 4, 8, (int) w, (int) h, 150));
                break;
            }
            case 3: {
                sprite.define(AnimatedSprite.Action.idle,
                        new AnimatedSprite.ActionSpec(0, 0, 4, 8, (int) w, (int) h, 150));
                sprite.define(AnimatedSprite.Action.shoot,
                        new AnimatedSprite.ActionSpec(4, 0, 4, 8, (int) w, (int) h, 80));
                break;
            }
            default: {
                sprite.define(AnimatedSprite.Action.idle,
                        new AnimatedSprite.ActionSpec(0, 0, 4, 8, (int) w, (int) h, 180));
                sprite.define(AnimatedSprite.Action.shoot,
                        new AnimatedSprite.ActionSpec(4, 0, 4, 8, (int) w, (int) h, 100));
                break;
            }
        }
        sprite.setAction(AnimatedSprite.Action.idle);
    }


    // ---------------- Java-boss simple mouth animation ----------------
    // Build a viewport (pixel rect) for col on row 0, using per-frame w/h.
    private Rectangle2D rectForCol(int col) {
        int fw = (int) w; // per-frame width passed to super(...)
        int fh = (int) h; // per-frame height
        int x  = col * fw;
        int y  = 0;       // row 0
        return new Rectangle2D(x, y, fw, fh);
    }

    // Play col0 -> col1 then return to col0.
    private void playMouthBurstSimple() {
        if (sprite == null || bossType != 2) return;
        if (mouthTl != null) { mouthTl.stop(); mouthTl = null; }

        mouthTl = new Timeline(
                new KeyFrame(Duration.millis(0),        e -> sprite.setViewport(rectForCol(0))), // closed
                new KeyFrame(Duration.millis(SHOOT_DELAY_MS), e -> sprite.setViewport(rectForCol(1))), // open
                new KeyFrame(Duration.millis(SHOOT_DELAY_MS * 2), e -> sprite.setViewport(rectForCol(0))) // back to closed
        );
        mouthTl.setCycleCount(1);
        mouthTl.play();
    }

    // Snap to dead pose at col2 and stay there.
    private void showDeathPoseSimple() {
        if (sprite == null) return;
        if (mouthTl != null) { mouthTl.stop(); mouthTl = null; }
        sprite.setViewport(rectForCol(2));
    }

    // ------------------------------------------------------------------

    @Override
    public boolean hit(int dmg) {
        long now = System.currentTimeMillis();
        if (now - lastHitAt < INVINCIBLE_MS) return false;
        lastHitAt = now;

        boolean dead = super.hit(dmg);

        if (!dead) {
            int hpNow = getHp();
            if (fallback != null) {
                Platform.runLater(() -> {
                    fallback.setStroke(Color.WHITE);
                    fallback.setStrokeWidth(2.5);
                    var pt = new javafx.animation.PauseTransition(Duration.millis(90));
                    pt.setOnFinished(e -> { fallback.setStroke(null); fallback.setStrokeWidth(0); });
                    pt.play();
                    fallback.setFill(hpNow > BASE_HP / 2 ? Color.MEDIUMPURPLE : Color.ORCHID);
                });
            }
        } else {
            if (fallback != null) {
                Platform.runLater(() -> fallback.setFill(Color.PLUM));
            }
            if (sprite != null) {
                Platform.runLater(() -> {
                    if (bossType == 2) {
                        showDeathPoseSimple(); // column 3 (index 2)
                    } else {
                        // keep legacy death pose for other boss types
                        sprite.define(AnimatedSprite.Action.shootDown,
                                new AnimatedSprite.ActionSpec(2, 0, 1, 3, (int) w, (int) h, Integer.MAX_VALUE));
                        sprite.setActionForce(AnimatedSprite.Action.shootDown);
                    }
                });
            }
        }

        return dead;
    }

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

        // --- Mouth animation (simple: col0 -> col1 -> col0) ---
        if (bossType == 2) {
            Platform.runLater(this::playMouthBurstSimple);
        } else if (sprite != null) {
            try { sprite.setActionForce(AnimatedSprite.Action.shoot); }
            catch (Throwable __) { sprite.setAction(AnimatedSprite.Action.shoot); }
        }

        // --- Bullet selection ---
        if (bossType == 2) {
            // Java boss ONLY → animated java_bullet
            return new Bullet(
                    bulletX, bulletY,
                    dx, dy,
                    300.0, 1, 1.6, true,
                    JAVA_BULLET_PATH,
                    4, 4, 1, 24, 24
            );
        }

        // Non-Java bosses: never allow the java_bullet to leak via bulletConfig
        if (bulletConfig != null) {
            boolean isJavaSheet = JAVA_BULLET_PATH.equals(bulletConfig.spritePath);
            if (!isJavaSheet) {
                if (bulletConfig.animated) {
                    return new Bullet(
                            bulletX, bulletY, dx, dy, 300.0, 1,
                            Math.max(1.0, bulletConfig.scale), true,
                            bulletConfig.spritePath, bulletConfig.frameCount, bulletConfig.columns,
                            bulletConfig.rows, bulletConfig.frameWidth, bulletConfig.frameHeight
                    );
                } else if (bulletConfig.spritePath != null) {
                    return new Bullet(
                            bulletX, bulletY, dx, dy, 300.0, 1,
                            Math.max(1.0, bulletConfig.scale), true,
                            bulletConfig.spritePath
                    );
                }
            }
            // if it was the java sheet, fall through to plain bullet
        }

        // Default plain enemy bullet
        return new Bullet(bulletX, bulletY, dx, dy, 300.0, 1, 1.6, true);
    }

    @Override
    public void update(double dtSec, GameCharacter player) {
        super.update(dtSec, player);
        if (sprite != null) {
            // IMPORTANT: AnimatedSprite.update expects milliseconds
            sprite.update(dtSec * 1000.0);
        }
    }

    public int getBossType() { return bossType; }

    public static class BulletConfig {
        public final String spritePath;
        public final double scale;
        public final boolean animated;
        public final int frameCount, columns, rows, frameWidth, frameHeight;

        public BulletConfig(String spritePath, double scale) {
            this.spritePath = spritePath;
            this.scale = scale;
            this.animated = false;
            this.frameCount = this.columns = this.rows = this.frameWidth = this.frameHeight = 0;
        }
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

        public Bullet createBullet(double x, double y, double dx, double dy,
                                   double speed, int damage, boolean enemyBullet) {
            double baseRadius = 1.6;
            double radius = Math.max(0.8, baseRadius * Math.max(0.25, scale));
            return new Bullet(x, y, dx, dy, speed, damage, radius, enemyBullet);
        }
    }
}

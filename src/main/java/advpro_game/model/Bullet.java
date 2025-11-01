package advpro_game.model;

import advpro_game.Launcher;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Bullet {
    private static final Logger LOG = LogManager.getLogger(Bullet.class);
    private double x, y;
    private double vx, vy;
    private double speed;
    private int damage;
    private boolean isEnemyBullet = false;

    private Image img;
    private AnimatedSprite sprite;
    private ImageView fallbackNode;
    private double baseW = 8, baseH = 8;
    private double scale = 1.6;
    private boolean isAnimated = false;

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage) {
        this(x, y, dirX, dirY, speed, damage, 1.6, false);
    }

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage, double renderScale) {
        this(x, y, dirX, dirY, speed, damage, renderScale, false);
    }

    // Constructor with enemy bullet flag
    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage, double renderScale, boolean isEnemyBullet) {
        this(x, y, dirX, dirY, speed, damage, renderScale, isEnemyBullet, null, 0, 0, 0, 0, 0);
    }

    // Constructor with custom sprite path (static image)
    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage, double renderScale, boolean isEnemyBullet, String customSpritePath) {
        this(x, y, dirX, dirY, speed, damage, renderScale, isEnemyBullet, customSpritePath, 0, 0, 0, 0, 0);
    }

    // NEW: Full constructor with animation support
    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage,
                  double renderScale, boolean isEnemyBullet, String customSpritePath,
                  int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.damage = damage;
        this.scale = renderScale;
        this.isEnemyBullet = isEnemyBullet;

        double len = Math.hypot(dirX, dirY);
        if (len == 0) {
            dirX = 1;
            dirY = 0;
            len = 1;
        }
        this.vx = dirX / len;
        this.vy = dirY / len;

        // Determine if this should be animated
        this.isAnimated = (frameCount > 1 && columns > 0 && rows > 0 && frameWidth > 0 && frameHeight > 0);

        // Load sprite (custom or default)
        String spritePath = (customSpritePath != null) ? customSpritePath : "/advpro_game/assets/Bullet.png";

        try (InputStream in = Launcher.class.getResourceAsStream(spritePath)) {
            if (in != null) {
                img = new Image(in);

                if (isAnimated) {
                    // Create animated sprite
                    sprite = new AnimatedSprite(img, frameCount, columns, rows, 0, 0, frameWidth, frameHeight);
                    baseW = frameWidth;
                    baseH = frameHeight;
                    sprite.setFitWidth(baseW * scale);
                    sprite.setFitHeight(baseH * scale);
                    sprite.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);

                    // Define idle animation (loops through all frames)
                    sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                            0, 0, frameCount, columns, frameWidth, frameHeight, 80
                    ));
                    sprite.setAction(AnimatedSprite.Action.idle);
                } else {
                    // Static image
                    baseW = img.getWidth();
                    baseH = img.getHeight();
                    fallbackNode = new ImageView(img);
                    fallbackNode.setManaged(false);
                    fallbackNode.setFitWidth(baseW * scale);
                    fallbackNode.setFitHeight(baseH * scale);
                    fallbackNode.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);
                }
            } else {
                LOG.warn("Bullet sprite not found: {}; using fallback.", spritePath);
                createFallbackNode();
            }
        } catch (Exception ex) {
            LOG.error("Error loading bullet sprite", ex);
            createFallbackNode();
        }
    }

    private void createFallbackNode() {
        fallbackNode = new ImageView();
        fallbackNode.setManaged(false);
        fallbackNode.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);
    }

    // Convenience overloads
    public Bullet(double x, double y, int dirSign) {
        this(x, y, (dirSign >= 0 ? 1.0 : -1.0), 0.0, 480.0, 1, 1.6, false);
    }

    public Bullet(double x, double y, double dirX, double dirY) {
        this(x, y, dirX, dirY, 480.0, 1, 1.6, false);
    }

    public void update(double dtSeconds) {
        // --- logic (any thread) ---
        x += vx * speed * dtSeconds;
        y += vy * speed * dtSeconds;

        final double newX = x - (baseW * scale) / 2.0;
        final double newY = y - (baseH * scale) / 2.0;
        final double ms   = dtSeconds * 1000.0;

        // --- visuals (FX thread only) ---
        Fx.runLater(() -> {
            if (isAnimated && sprite != null) {
                sprite.relocate(newX, newY);
                // AnimatedSprite.update expects milliseconds
                try { sprite.update(ms); } catch (Throwable ignored) {}
            } else if (fallbackNode != null) {
                fallbackNode.relocate(newX, newY);
            }
        });
    }


    public void draw(GraphicsContext gc, double camX, double camY) {
        // If this bullet is animated, its AnimatedSprite node handles rendering.
        if (isAnimated && sprite != null) return;

        if (img != null) {
            gc.drawImage(
                    img,
                    x - camX - (baseW * scale) / 2.0,
                    y - camY - (baseH * scale) / 2.0,
                    baseW * scale,
                    baseH * scale
            );
        } else {
            gc.fillOval(
                    x - camX - (baseW * scale) / 2.0,
                    y - camY - (baseH * scale) / 2.0,
                    baseW * scale,
                    baseH * scale
            );
        }
    }


    public Rectangle2D getHitbox() {
        return new Rectangle2D(
                x - (baseW * scale) / 2.0,
                y - (baseH * scale) / 2.0,
                baseW * scale,
                baseH * scale
        );
    }

    public final class Fx {
        private Fx() {}
        public static void runLater(Runnable r) {
            if (r == null) return;
            if (javafx.application.Platform.isFxApplicationThread()) r.run();
            else javafx.application.Platform.runLater(r);
        }
    }



    public Node getNode() {
        return isAnimated ? sprite : fallbackNode;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getDamage() {
        return damage;
    }

    public boolean isEnemyBullet() {
        return isEnemyBullet;
    }

    public boolean isAnimated() {
        return isAnimated;
    }
}
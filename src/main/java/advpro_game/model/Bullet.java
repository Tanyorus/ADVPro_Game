package advpro_game.model;

import advpro_game.Launcher;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.util.logging.Logger;

public class Bullet {
    // ---- Kinematics / gameplay ----
    private double x, y;              // world-space center
    private double vx, vy;            // normalized direction
    private double speed;             // px/sec
    private int    damage;
    private boolean isEnemyBullet = false;

    // ---- Visuals ----
    private Image img;                // static image (when not animated)
    private AnimatedSprite sprite;    // animated image-view (optional)
    private ImageView staticNode;     // ImageView for static case
    private double baseW = 8, baseH = 8;
    private double scale = 1.6;
    private boolean isAnimated = false;

    // ===================== Constructors (rich) =====================

    /** Core ctor (no custom sprite, not enemy). */
    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage) {
        this(x, y, dirX, dirY, speed, damage, 1.6, false);
    }

    /** Core ctor (with scale; not enemy). */
    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage, double renderScale) {
        this(x, y, dirX, dirY, speed, damage, renderScale, false);
    }

    /** Core ctor (with scale + enemy flag). */
    public Bullet(double x, double y, double dirX, double dirY,
                  double speed, int damage, double renderScale, boolean isEnemyBullet) {
        this(x, y, dirX, dirY, speed, damage, renderScale, isEnemyBullet,
                /*customSpritePath*/ null, 0,0,0,0,0);
    }

    /** Static custom sprite. */
    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage,
                  double renderScale, boolean isEnemyBullet, String customSpritePath) {
        this(x, y, dirX, dirY, speed, damage, renderScale, isEnemyBullet,
                customSpritePath, 0,0,0,0,0);
    }

    /**
     * Full ctor (animated/custom-capable).
     * If frameCount > 1 with valid columns/rows/frameW/frameH → animated; else static.
     */
    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage,
                  double renderScale, boolean isEnemyBullet, String customSpritePath,
                  int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        this.x = x; this.y = y;
        this.speed = speed;
        this.damage = damage;
        this.scale = renderScale;
        this.isEnemyBullet = isEnemyBullet;

        // normalize direction
        double len = Math.hypot(dirX, dirY);
        if (len == 0) { dirX = 1; dirY = 0; len = 1; }
        this.vx = dirX / len;
        this.vy = dirY / len;

        // animated?
        this.isAnimated = (frameCount > 1 && columns > 0 && rows > 0 && frameWidth > 0 && frameHeight > 0);

        // choose sprite path
        String spritePath = (customSpritePath != null) ? customSpritePath : "/advpro_game/assets/Bullet.png";

        // load
        try (InputStream in = Launcher.class.getResourceAsStream(spritePath)) {
            if (in != null) {
                img = new Image(in);
                if (isAnimated) {
                    // Animated sprite
                    sprite = new AnimatedSprite(img, frameCount, columns, rows, 0, 0, frameWidth, frameHeight);
                    baseW = frameWidth;
                    baseH = frameHeight;

                    // map a looping idle action across all frames
                    sprite.define(AnimatedSprite.Action.idle,
                            new AnimatedSprite.ActionSpec(0, 0, frameCount, columns, frameWidth, frameHeight, 80));
                    sprite.setAction(AnimatedSprite.Action.idle);

                    sprite.setManaged(false);
                    sprite.setFitWidth(baseW * scale);
                    sprite.setFitHeight(baseH * scale);
                    sprite.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);
                } else {
                    // Static image
                    baseW = img.getWidth();
                    baseH = img.getHeight();
                    staticNode = new ImageView(img);
                    staticNode.setManaged(false);
                    staticNode.setFitWidth(baseW * scale);
                    staticNode.setFitHeight(baseH * scale);
                    staticNode.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);
                }
            } else {
                Logger.getLogger(Bullet.class.getName()).warning("Bullet sprite not found: " + spritePath + "; using empty fallback.");
                createFallbackStatic();
            }
        } catch (Exception ex) {
            Logger.getLogger(Bullet.class.getName()).severe("Error loading bullet sprite: " + ex);
            createFallbackStatic();
        }
    }

    // Convenience overloads used elsewhere in your codebase
    public Bullet(double x, double y, int dirSign) {
        this(x, y, (dirSign >= 0 ? 1.0 : -1.0), 0.0, 480.0, 1, 1.6, false);
    }
    public Bullet(double x, double y, double dirX, double dirY) {
        this(x, y, dirX, dirY, 480.0, 1, 1.6, false);
    }

    private void createFallbackStatic() {
        staticNode = new ImageView(); // invisible but non-null to avoid NPEs
        staticNode.setManaged(false);
        staticNode.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);
        img = null; // ensures draw() uses simple gc.fillOval path if ever called
        isAnimated = false;
    }

    // ===================== Update / Render =====================

    public void update(double dtSeconds) {
        x += vx * speed * dtSeconds;
        y += vy * speed * dtSeconds;

        double newX = x - (baseW * scale) / 2.0;
        double newY = y - (baseH * scale) / 2.0;

        if (isAnimated && sprite != null) {
            sprite.relocate(newX, newY);
            sprite.update(dtSeconds * 1000.0); // AnimatedSprite expects ms
        } else if (staticNode != null) {
            staticNode.relocate(newX, newY);
        }
    }

    /** Canvas fallback (usually you’ll add the node to a Pane instead). */
    public void draw(GraphicsContext gc, double camX, double camY) {
        if (!isAnimated && img != null) {
            gc.drawImage(img,
                    x - camX - (baseW * scale) / 2.0,
                    y - camY - (baseH * scale) / 2.0,
                    baseW * scale, baseH * scale);
        } else {
            // simple dot when no image available (or animated handled by scene graph)
            gc.fillOval(
                    x - camX - (baseW * scale) / 2.0,
                    y - camY - (baseH * scale) / 2.0,
                    baseW * scale, baseH * scale);
        }
    }

    // ===================== Collision / Node =====================

    public Rectangle2D getHitbox() {
        // FIX: use baseH for Y offset/height (old code used baseW twice)
        return new Rectangle2D(
                x - (baseW * scale) / 2.0,
                y - (baseH * scale) / 2.0,
                baseW * scale, baseH * scale
        );
    }

    /** Add this to your bullet layer. */
    public Node getNode() {
        return isAnimated ? sprite : staticNode;
    }

    // ===================== Getters =====================

    public double getX()            { return x; }
    public double getY()            { return y; }
    public int getDamage()          { return damage; }
    public boolean isEnemyBullet()  { return isEnemyBullet; }
    public boolean isAnimated()     { return isAnimated; }
}

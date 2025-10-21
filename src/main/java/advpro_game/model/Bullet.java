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
    private static final Logger LOG = Logger.getLogger(Bullet.class.getName());
    private static final String SPRITE_PATH = "/advpro_game/assets/Bullet.jpeg";

    // Cache sprite once for all bullets
    private static Image SPRITE;
    private static double SPRITE_W = 8, SPRITE_H = 8;

    private static void ensureSprite() {
        if (SPRITE != null) return;
        try (InputStream in = Launcher.class.getResourceAsStream(SPRITE_PATH)) {
            if (in != null) {
                SPRITE = new Image(in);
                SPRITE_W = SPRITE.getWidth();
                SPRITE_H = SPRITE.getHeight();
            } else {
                LOG.warning("Bullet sprite not found at " + SPRITE_PATH + "; using fallback size.");
            }
        } catch (Exception ex) {
            LOG.severe("Error loading bullet sprite: " + ex);
        }
    }

    // === Instance state ===
    private double x, y;     // world center position
    private double vx, vy;   // normalized direction
    private double speed;    // pixels/sec
    private int damage;

    private ImageView node;  // scene-graph node
    private double w = 8, h = 8; // size (from sprite, else fallback)

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage) {
        ensureSprite();

        this.x = x; this.y = y;
        this.speed = speed;
        this.damage = damage;

        double len = Math.hypot(dirX, dirY);
        if (len == 0) { dirX = 1; dirY = 0; len = 1; }
        this.vx = dirX / len;
        this.vy = dirY / len;

        // Node setup
        if (SPRITE != null) {
            this.w = SPRITE_W; this.h = SPRITE_H;
            node = new ImageView(SPRITE);
        } else {
            node = new ImageView(); // still a Node so we can add/remove
        }
        node.setManaged(false);
        node.setMouseTransparent(true);
        node.setPickOnBounds(false);
        node.setCache(true);

        // place with (x,y) as center
        node.relocate(x - w / 2.0, y - h / 2.0);
    }

    // Convenience: horizontal-only using direction sign
    public Bullet(double x, double y, int dirSign) {
        this(x, y, (dirSign >= 0 ? 1.0 : -1.0), 0.0, 480.0, 1);
    }

    // Convenience: dx,dy with defaults
    public Bullet(double x, double y, double dirX, double dirY) {
        this(x, y, dirX, dirY, 480.0, 1);
    }

    // Convenience: angle in radians
    public Bullet(double x, double y, double angleRadians, boolean useAngle) {
        this(x, y, Math.cos(angleRadians), Math.sin(angleRadians), 480.0, 1);
    }

    /** Call every frame with dt in seconds (e.g., 1/60.0) — must be on FX thread if node is in scene. */
    public void update(double dtSeconds) {
        x += vx * speed * dtSeconds;
        y += vy * speed * dtSeconds;
        node.relocate(x - w / 2.0, y - h / 2.0);
    }

    /** Optional canvas debug draw (world coords) */
    public void draw(GraphicsContext gc, double camX, double camY) {
        if (SPRITE != null) {
            gc.drawImage(SPRITE, x - camX - w / 2.0, y - camY - h / 2.0);
        } else {
            gc.fillOval(x - camX - w / 2.0, y - camY - h / 2.0, w, h);
        }
    }

    /** Axis-aligned hitbox in world space */
    public Rectangle2D getHitbox() {
        return new Rectangle2D(x - w / 2.0, y - h / 2.0, w, h);
    }

    /** Scene Node so you can add/remove it from GameStage children */
    public Node getNode() { return node; }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getDamage() { return damage; }

    // (Optional) helpers if you ever need them:
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public double getDirX() { return vx; }
    public double getDirY() { return vy; }
}

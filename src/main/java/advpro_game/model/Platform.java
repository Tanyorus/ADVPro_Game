package advpro_game.model;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;

import static javafx.scene.paint.Color.*;

public class Platform {
    /** Collision tolerance to avoid jitter when snapping to surfaces */
    public static final double EPS = 0.5;

    /** Axis-aligned hitbox in world space */
    private final Rectangle2D hitbox;

    /**
     * If true => blocks on all sides (walls/ground).
     * If false => treated as ONE-WAY (top face only). Your character code already interprets it this way.
     */
    private final boolean solid;

    public Platform(double x, double y, double width, double height, boolean solid) {
        this.hitbox = new Rectangle2D(x, y, width, height);
        this.solid = solid;
    }

    /** Convenience factories */
    public static Platform solid(double x, double y, double w, double h)  { return new Platform(x, y, w, h, true); }
    public static Platform oneWay(double x, double y, double w, double h) { return new Platform(x, y, w, h, false); }

    // ---- Accessors ----
    public Rectangle2D getHitbox() { return this.hitbox; }
    public boolean isSolid() { return this.solid; }

    // ---- Geometry helpers (optional, handy for collision logic) ----
    public double left()   { return hitbox.getMinX(); }
    public double right()  { return hitbox.getMaxX(); }
    public double top()    { return hitbox.getMinY(); }
    public double bottom() { return hitbox.getMaxY(); }
    public double width()  { return hitbox.getWidth(); }
    public double height() { return hitbox.getHeight(); }

    /** Returns true if two boxes overlap horizontally (with small epsilon). */
    public boolean horizontalOverlap(Rectangle2D other) {
        return other.getMaxX() > left() + EPS && other.getMinX() < right() - EPS;
    }

    // ---- Debug draw ----
    public void drawDebug(GraphicsContext gc) {
        gc.setStroke(solid ? RED : CYAN);
        gc.strokeRect(hitbox.getMinX(), hitbox.getMinY(), hitbox.getWidth(), hitbox.getHeight());
    }

    @Override
    public String toString() {
        return "Platform{"
                + (solid ? "SOLID" : "ONEWAY")
                + ", x=" + hitbox.getMinX()
                + ", y=" + hitbox.getMinY()
                + ", w=" + hitbox.getWidth()
                + ", h=" + hitbox.getHeight()
                + '}';
    }
}

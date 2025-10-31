package advpro_game.model;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Level platform: either SOLID (full block) or ONE-WAY (top-face only).
 */
public class Platform {
    private final Rectangle2D hitbox;
    private final boolean solid;   // true = solid block; false = one-way ledge (top-face only)

    public Platform(double x, double y, double width, double height, boolean solid) {
        this.hitbox = new Rectangle2D(x, y, width, height);
        this.solid = solid;
    }

    /** Convenience factories used by GameStage.setStage(...) */
    public static Platform solid(double x, double y, double w, double h)  { return new Platform(x, y, w, h, true); }
    public static Platform oneWay(double x, double y, double w, double h) { return new Platform(x, y, w, h, false); }

    // === API used by GameCharacter / DrawingLoop ===
    public Rectangle2D getHitbox() {
        return hitbox;
    }

    public boolean isSolid()       {
        return solid;
    }

    // Optional convenience getters
    public double getX()      { return hitbox.getMinX(); }
    public double getY()      { return hitbox.getMinY(); }
    public double getWidth()  { return hitbox.getWidth(); }
    public double getHeight() { return hitbox.getHeight(); }
    public double getTop()    { return hitbox.getMinY(); }
    public double getBottom() { return hitbox.getMaxY(); }
    public double getLeft()   { return hitbox.getMinX(); }
    public double getRight()  { return hitbox.getMaxX(); }

    // Debug draw (optional)
    public void drawDebug(GraphicsContext gc) {
        gc.setStroke(solid ? Color.RED : Color.CYAN);
        gc.strokeRect(hitbox.getMinX(), hitbox.getMinY(), hitbox.getWidth(), hitbox.getHeight());
    }
}

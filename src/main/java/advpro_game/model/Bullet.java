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
    private double x, y;         // world center
    private double vx, vy;       // normalized direction
    private double speed;        // px/sec
    private int damage;

    private Image img;
    private ImageView node;      // scene-graph node
    private double baseW = 8, baseH = 8;
    private double scale = 1.6;  // <— medium size (tweak 1.3..2.0)

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage) {
        this(x, y, dirX, dirY, speed, damage, 1.6);
    }

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage, double renderScale) {
        this.x = x; this.y = y;
        this.speed = speed;
        this.damage = damage;
        this.scale = renderScale;

        double len = Math.hypot(dirX, dirY);
        if (len == 0) { dirX = 1; dirY = 0; len = 1; }
        this.vx = dirX / len;
        this.vy = dirY / len;

        try (InputStream in = Launcher.class.getResourceAsStream("/advpro_game/assets/Bullet.jpeg")) {
            if (in != null) {
                img = new Image(in);
                baseW = img.getWidth();
                baseH = img.getHeight();
                node = new ImageView(img);
                node.setManaged(false);
                ((ImageView)node).setFitWidth(baseW * scale);
                ((ImageView)node).setFitHeight(baseH * scale);
                node.relocate(x - (baseW*scale)/2.0, y - (baseH*scale)/2.0);
            } else {
                Logger.getLogger(Bullet.class.getName()).warning("Bullet.jpeg not found; using fallback circle.");
                node = new ImageView(); // placeholder
                node.setManaged(false);
                node.relocate(x - (baseW*scale)/2.0, y - (baseH*scale)/2.0);
            }
        } catch (Exception ex) {
            Logger.getLogger(Bullet.class.getName()).severe("Error loading bullet sprite: " + ex);
            node = new ImageView();
            node.setManaged(false);
            node.relocate(x - (baseW*scale)/2.0, y - (baseH*scale)/2.0);
        }
    }

    // convenience overloads
    public Bullet(double x, double y, int dirSign) {
        this(x, y, (dirSign >= 0? 1.0 : -1.0), 0.0, 480.0, 1, 1.6);
    }
    public Bullet(double x, double y, double dirX, double dirY) {
        this(x, y, dirX, dirY, 480.0, 1, 1.6);
    }

    public void update(double dtSeconds) {
        x += vx * speed * dtSeconds;
        y += vy * speed * dtSeconds;
        node.relocate(x - (baseW*scale)/2.0, y - (baseH*scale)/2.0);
    }

    public void draw(GraphicsContext gc, double camX, double camY) {
        if (img != null) {
            gc.drawImage(img, x - camX - (baseW*scale)/2.0, y - camY - (baseH*scale)/2.0,
                    baseW*scale, baseH*scale);
        } else {
            gc.fillOval(x - camX - (baseW*scale)/2.0, y - camY - (baseH*scale)/2.0,
                    baseW*scale, baseH*scale);
        }
    }

    public Rectangle2D getHitbox() {
        return new Rectangle2D(x - (baseW*scale)/2.0, y - (baseH*scale)/2.0, baseW*scale, baseH*scale);
    }

    public Node getNode() { return node; }
    public double getX() { return x; }
    public double getY() { return y; }
    public int getDamage() { return damage; }
}

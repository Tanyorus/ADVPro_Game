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
    private double x, y;
    private double vx, vy;
    private double speed;
    private int damage;
    private boolean isEnemyBullet = false;

    // ---- Visuals ----
    private Image img;
    private AnimatedSprite sprite;
    private ImageView staticNode;
    private double baseW = 8, baseH = 8;
    private double scale = 1.6;
    private boolean isAnimated = false;

    // ---- Sprite paths ----
    private static final String PLAYER_BULLET_SPRITE = "/advpro_game/assets/Bullet.png";
    private static final String ENEMY_BULLET_SPRITE  = "/advpro_game/assets/Bullet.png"; // <-- neutral
    private static final String JAVA_BOSS_BULLET     = "/advpro_game/assets/java_bullet.png";

    // Default Java-boss animation
    private static final int JAVA_FRAMES   = 4;
    private static final int JAVA_COLS     = 4;
    private static final int JAVA_ROWS     = 1;
    private static final int JAVA_FRAME_W  = 24;
    private static final int JAVA_FRAME_H  = 24;

    // ===================== Constructors =====================

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage) {
        this(x, y, dirX, dirY, speed, damage, 1.6, false);
    }

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage, double renderScale) {
        this(x, y, dirX, dirY, speed, damage, renderScale, false);
    }

    public Bullet(double x, double y, double dirX, double dirY,
                  double speed, int damage, double renderScale, boolean isEnemyBullet) {
        this(x, y, dirX, dirY, speed, damage, renderScale, isEnemyBullet, null, 0,0,0,0,0);
    }

    public Bullet(double x, double y, double dirX, double dirY, double speed, int damage,
                  double renderScale, boolean isEnemyBullet, String customSpritePath) {
        this(x, y, dirX, dirY, speed, damage, renderScale, isEnemyBullet,
                customSpritePath, 0,0,0,0,0);
    }

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

        // -------- Decide sprite source safely --------
        String spritePath = customSpritePath;
        boolean animatedParams =
                (frameCount > 1 && columns > 0 && rows > 0 && frameWidth > 0 && frameHeight > 0);

        if (spritePath == null) {
            if (isEnemyBullet) {
                // ✅ Use neutral bullet for ordinary enemies
                spritePath = ENEMY_BULLET_SPRITE;
            } else {
                // player bullet
                spritePath = PLAYER_BULLET_SPRITE;
            }
        }

        // ✅ Ensure only explicitly requested Java boss bullets animate
        if (JAVA_BOSS_BULLET.equals(spritePath)) {
            frameCount = JAVA_FRAMES;
            columns    = JAVA_COLS;
            rows       = JAVA_ROWS;
            frameWidth = JAVA_FRAME_W;
            frameHeight= JAVA_FRAME_H;
            animatedParams = true;
        }

        this.isAnimated = animatedParams;

        // ---- Load sprite ----
        try (InputStream in = Launcher.class.getResourceAsStream(spritePath)) {
            if (in != null) {
                img = new Image(in);
                if (this.isAnimated) {
                    sprite = new AnimatedSprite(img, frameCount, columns, rows, 0, 0, frameWidth, frameHeight);
                    baseW = frameWidth;
                    baseH = frameHeight;
                    sprite.define(AnimatedSprite.Action.idle,
                            new AnimatedSprite.ActionSpec(0, 0, frameCount, columns, frameWidth, frameHeight, 80));
                    sprite.setAction(AnimatedSprite.Action.idle);
                    sprite.setManaged(false);
                    sprite.setFitWidth(baseW * scale);
                    sprite.setFitHeight(baseH * scale);
                    sprite.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);
                } else {
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

    public Bullet(double x, double y, int dirSign) {
        this(x, y, (dirSign >= 0 ? 1.0 : -1.0), 0.0, 480.0, 1, 1.6, false);
    }
    public Bullet(double x, double y, double dirX, double dirY) {
        this(x, y, dirX, dirY, 480.0, 1, 1.6, false);
    }

    private void createFallbackStatic() {
        staticNode = new ImageView();
        staticNode.setManaged(false);
        staticNode.relocate(x - (baseW * scale) / 2.0, y - (baseH * scale) / 2.0);
        img = null;
        isAnimated = false;
        baseW = 8; baseH = 8;
    }

    // ===================== Update / Render =====================

    public void update(double dtSeconds) {
        x += vx * speed * dtSeconds;
        y += vy * speed * dtSeconds;

        double newX = x - (baseW * scale) / 2.0;
        double newY = y - (baseH * scale) / 2.0;

        if (isAnimated && sprite != null) {
            sprite.relocate(newX, newY);
            sprite.update(dtSeconds * 1000.0);
        } else if (staticNode != null) {
            staticNode.relocate(newX, newY);
        }
    }

    public void draw(GraphicsContext gc, double camX, double camY) {
        if (!isAnimated && img != null) {
            gc.drawImage(img,
                    x - camX - (baseW * scale) / 2.0,
                    y - camY - (baseH * scale) / 2.0,
                    baseW * scale, baseH * scale);
        } else {
            gc.fillOval(
                    x - camX - (baseW * scale) / 2.0,
                    y - camY - (baseH * scale) / 2.0,
                    baseW * scale, baseH * scale);
        }
    }

    public Rectangle2D getHitbox() {
        return new Rectangle2D(
                x - (baseW * scale) / 2.0,
                y - (baseH * scale) / 2.0,
                baseW * scale, baseH * scale
        );
    }

    public Node getNode() {
        return isAnimated ? sprite : staticNode;
    }

    public double getX()            { return x; }
    public double getY()            { return y; }
    public int getDamage()          { return damage; }
    public boolean isEnemyBullet()  { return isEnemyBullet; }
    public boolean isAnimated()     { return isAnimated; }
}

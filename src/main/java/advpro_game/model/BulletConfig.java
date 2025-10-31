package advpro_game.model;

/**
 * Configuration class for custom bullet sprites and animations
 */
public class BulletConfig {
    public final String spritePath;
    public final double scale;
    public final int frameCount;
    public final int columns;
    public final int rows;
    public final int frameWidth;
    public final int frameHeight;
    public final boolean isAnimated;

    // Static bullet (no animation)
    public BulletConfig(String spritePath, double scale) {
        this.spritePath = spritePath;
        this.scale = scale;
        this.frameCount = 0;
        this.columns = 0;
        this.rows = 0;
        this.frameWidth = 0;
        this.frameHeight = 0;
        this.isAnimated = false;
    }

    // Animated bullet
    public BulletConfig(String spritePath, double scale, int frameCount, int columns, int rows,
                        int frameWidth, int frameHeight) {
        this.spritePath = spritePath;
        this.scale = scale;
        this.frameCount = frameCount;
        this.columns = columns;
        this.rows = rows;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.isAnimated = true;
    }

    // Create bullet with this config
    public Bullet createBullet(double x, double y, double dirX, double dirY, double speed,
                               int damage, boolean isEnemyBullet) {
        if (isAnimated) {
            return new Bullet(x, y, dirX, dirY, speed, damage, scale, isEnemyBullet,
                    spritePath, frameCount, columns, rows, frameWidth, frameHeight);
        } else {
            return new Bullet(x, y, dirX, dirY, speed, damage, scale, isEnemyBullet, spritePath);
        }
    }
}
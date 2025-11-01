package advpro_game.model;

import advpro_game.Launcher;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class Enemy extends Pane {
    // --- Logical state (source of truth) ---
    protected double x, y, w, h;
    private   int    hp = 1;

    // Visuals
    protected AnimatedSprite sprite;   // optional
    protected Rectangle      fallback; // used if sprite fails/missing

    // Movement
    protected double vx = 0.0;
    protected double moveSpeed = 30.0; // px/s
    protected int    direction = -1;   // -1 left, 1 right

    // Shooting
    protected long   lastShotTime   = 0;
    protected int    shootCooldownMs = 2000;  // 2s
    protected double shootRange      = 400.0; // px

    // *** NEW: force non-Java enemies to use a neutral bullet sprite ***
    protected static final String DEFAULT_ENEMY_BULLET_PATH = "/advpro_game/assets/Bullet.png";

    /** Sprite-enabled constructor (falls back to rectangle if sprite not available). */
    public Enemy(double x, double y, double w, double h,
                 String spritePath, int frameCount, int columns, int rows,
                 int frameWidth, int frameHeight) {
        this.x = x; this.y = y; this.w = w; this.h = h;

        boolean spriteOk = false;
        try {
            Image img = new Image(Launcher.class.getResourceAsStream(spritePath));
            if (img != null && !img.isError()) {
                sprite = new AnimatedSprite(img, frameCount, columns, rows,
                        0, 0, frameWidth, frameHeight);

                sprite.setFitWidth(w);
                sprite.setFitHeight(h);

                int framesPerRow = Math.min(frameCount, columns);
                sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                        0, 0, framesPerRow, columns, frameWidth, frameHeight, 150
                ));
                sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                        0, 0, framesPerRow, columns, frameWidth, frameHeight, 100
                ));
                sprite.setAction(AnimatedSprite.Action.idle);

                getChildren().add(sprite);
                spriteOk = true;
            }
        } catch (Exception ignore) {}
        if (!spriteOk) {
            fallback = makeFallbackRect(w, h);
            getChildren().add(fallback);
        }

        setTranslateX(x);
        setTranslateY(y);
    }

    /** Simple rectangle-only constructor. */
    public Enemy(double x, double y, double w, double h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        fallback = makeFallbackRect(w, h);
        getChildren().add(fallback);
        setTranslateX(x);
        setTranslateY(y);
    }

    private Rectangle makeFallbackRect(double w, double h) {
        Rectangle r = new Rectangle(w, h, Color.DARKRED);
        r.setArcWidth(6); r.setArcHeight(6);
        return r;
    }

    // -------------------- Collision / Node API --------------------
    public Rectangle2D getHitbox() { return new Rectangle2D(x, y, w, h); }
    public Pane getNode() { return this; } // compatibility

    // -------------------- HP API --------------------
    public int  getHp()          { return hp; }
    public void setHp(int hp)    { this.hp = Math.max(0, hp); }
    public void addHp(int delta) { setHp(this.hp + delta); }
    public boolean isDead()      { return hp <= 0; }

    /** @return true if dead after hit */
    public boolean hit(int dmg) {
        setHp(hp - Math.max(0, dmg));
        if (isDead()) {
            if (sprite != null) sprite.setOpacity(0.5);
            if (fallback != null) fallback.setFill(Color.GRAY);
        } else {
            if (fallback != null) fallback.setFill(Color.FIREBRICK);
        }
        return isDead();
    }

    // -------------------- Movement / Update --------------------
    public void update(double dtSeconds, GameCharacter player) {
        if (isDead()) return;

        double playerX = player.getX() + player.getCharacterWidth() / 2.0;
        double enemyX  = x + w / 2.0;
        double dx = playerX - enemyX;

        if (Math.abs(dx) > 50 && moveSpeed > 0) {
            direction = dx > 0 ? 1 : -1;
            vx = direction * moveSpeed;
            x += vx * dtSeconds;

            // Keep within 800px screen bounds; adjust if your width differs
            if (x < 0) x = 0;
            if (x > 800 - w) x = 800 - w;

            if (sprite != null) {
                sprite.setAction(AnimatedSprite.Action.run);
                final int dir = direction;
                javafx.application.Platform.runLater(() -> setScaleX(dir));
            }
        } else {
            vx = 0;
            if (sprite != null) sprite.setAction(AnimatedSprite.Action.idle);
        }

        if (sprite != null) {
            sprite.update(dtSeconds * 1000.0); // ms
        }

        final double fx = x, fy = y;
        javafx.application.Platform.runLater(() -> {
            setTranslateX(fx);
            setTranslateY(fy);
        });
    }

    // -------------------- Shooting --------------------
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

        // *** CRITICAL: Always use the neutral enemy bullet for base Enemy ***
        // This prevents java_bullet.png from ever leaking into regular enemies.
        return new Bullet(
                bulletX, bulletY,
                dx, dy,
                280.0,                // speed
                1,                    // damage
                1.4,                  // render scale
                /*isEnemyBullet*/ true,
                DEFAULT_ENEMY_BULLET_PATH // explicit neutral sprite
        );
    }

    // -------------------- Renamed logical size getters --------------------
    public double getEnemyWidth()  { return w; }
    public double getEnemyHeight() { return h; }

    // Optional setters for tuning
    public void setMoveSpeed(double pxPerSec) { this.moveSpeed = pxPerSec; }
    public void setShootCooldownMs(int ms)    { this.shootCooldownMs = ms; }
    public void setShootRange(double px)      { this.shootRange = px; }

    //-------getter-----------
    public double getX()    { return x; }
    public double getY()    { return y; }
    public double getPosX() { return x; } // optional aliases
    public double getPosY() { return y; }
}

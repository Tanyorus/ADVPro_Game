package advpro_game.model;

import advpro_game.Launcher;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;

public class Enemy extends Pane {
    protected double x, y, w, h;
    private int hp = 1;
    protected AnimatedSprite sprite;
    protected Rectangle fallbackNode;  // Fallback if no sprite

    // Movement
    protected double vx = 0;
    protected double moveSpeed = 30.0;
    protected int direction = -1;

    // Shooting
    protected long lastShotTime = 0;
    protected int shootCooldownMs = 2000;
    protected double shootRange = 400;

    public Enemy(double x, double y, double w, double h, String spritePath,
                 int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        // Try to load sprite sheet
        try {
            Image img = new Image(Launcher.class.getResourceAsStream(spritePath));
            sprite = new AnimatedSprite(img, frameCount, columns, rows, 0, 0, frameWidth, frameHeight);

            sprite.setFitWidth(w);
            sprite.setFitHeight(h);

            getChildren().add(sprite);

            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 150
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 100
            ));

            sprite.setAction(AnimatedSprite.Action.idle);

        } catch (Exception e) {
            System.err.println("Failed to load enemy sprite: " + spritePath + ", using fallback rectangle");
            // Use fallback rectangle
            fallbackNode = new Rectangle(w, h, Color.DARKRED);
            getChildren().add(fallbackNode);
        }

        setTranslateX(x);
        setTranslateY(y);
    }

    // Fallback constructor for enemies without sprites
    public Enemy(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        // Create fallback rectangle
        fallbackNode = new Rectangle(w, h, Color.DARKRED);
        getChildren().add(fallbackNode);
        setTranslateX(x);
        setTranslateY(y);
    }

    public Rectangle2D getHitbox() {
        return new Rectangle2D(x, y, w, h);
    }

    // For compatibility - returns the visual node
    public Pane getNode() {
        return this;
    }

    // HP API
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = Math.max(0, hp); }
    public void addHp(int delta) { setHp(this.hp + delta); }
    public boolean isDead() { return hp <= 0; }

    public boolean hit(int dmg) {
        setHp(hp - Math.max(0, dmg));
        if (isDead()) {
            if (sprite != null) {
                sprite.setOpacity(0.5);
            }
            if (fallbackNode != null) {
                fallbackNode.setFill(Color.GRAY);
            }
        } else {
            if (fallbackNode != null) {
                fallbackNode.setFill(Color.FIREBRICK);
            }
        }
        return isDead();
    }

    // Movement API
    public void update(double dtSeconds, GameCharacter player) {
        if (isDead()) return;

        double playerX = player.getX() + player.getCharacterWidth() / 2.0;
        double enemyX = x + w / 2.0;
        double dx = playerX - enemyX;

        if (Math.abs(dx) > 50 && moveSpeed > 0) {
            direction = dx > 0 ? 1 : -1;
            vx = direction * moveSpeed;
            x += vx * dtSeconds;

            if (x < 0) x = 0;
            if (x > 800 - w) x = 800 - w;

            if (sprite != null) {
                sprite.setAction(AnimatedSprite.Action.run);
                javafx.application.Platform.runLater(() -> setScaleX(direction));
            }
        } else {
            vx = 0;
            if (sprite != null) {
                sprite.setAction(AnimatedSprite.Action.idle);
            }
        }

        if (sprite != null) {
            sprite.update(dtSeconds * 1000);
        }

        final double finalX = x;
        final double finalY = y;
        javafx.application.Platform.runLater(() -> {
            setTranslateX(finalX);
            setTranslateY(finalY);
        });
    }

    // Shooting API
    public Bullet tryShoot(GameCharacter player) {
        long now = System.currentTimeMillis();
        if (now - lastShotTime < shootCooldownMs) {
            return null;
        }

        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double distance = Math.hypot(dx, dy);

        if (distance > shootRange) {
            return null;
        }

        lastShotTime = now;

        double bulletX = x + w / 2;
        double bulletY = y + h / 2;

        return new Bullet(bulletX, bulletY, dx, dy, 300.0, 1, 1.6, true);
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
//    public double getWidth() { return w; }
//    public double getHeight() { return h; }
}
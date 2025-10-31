package advpro_game.model;

import javafx.scene.paint.Color;

public class Minion extends Enemy {
    // Original constructor (for backward compatibility)
    public Minion(double x, double y) {
        super(x, y, 24, 40);
        setHp(1);
        this.moveSpeed = 0;
        this.shootCooldownMs = 5000;  // 5 seconds
        this.shootRange = 300;
        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * 3000);
    }

    // New constructor with custom sprite parameters
    public Minion(double x, double y, double w, double h, String spritePath,
                  int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(1);
        this.moveSpeed = 0;
        this.shootCooldownMs = 5000;
        this.shootRange = 300;
        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * 3000);

        // Define animations for custom sprite
        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 150
            ));
            sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 100
            ));
        }

        // Set color for fallback rectangle
        if (fallbackNode != null) {
            fallbackNode.setFill(Color.DARKRED);
        }
    }

    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        if (!dead && fallbackNode != null) {
            fallbackNode.setFill(Color.FIREBRICK);
        }
        return dead;
    }
}
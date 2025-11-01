package advpro_game.model;

import javafx.scene.paint.Color;

public class EliteMinion extends Enemy {
    // Original constructor (for backward compatibility)
    public EliteMinion(double x, double y) {
        super(x, y, 30, 60,
                "/advpro_game/assets/EliteMinionSprite.png",
                6, 6, 1, 30, 60);

        setHp(5);
        this.moveSpeed = 25.0;
        this.shootCooldownMs = 999999;
        this.shootRange = 0;

        if (fallbackNode != null) {
            fallbackNode.setFill(Color.DARKBLUE);
        }

        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, 2, 6, 30, 60, 300
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    2, 0, 4, 6, 30, 60, 120
            ));
        }
    }

    // Constructor with custom move speed
    public EliteMinion(double x, double y, double moveSpeed) {
        super(x, y, 30, 60,
                "/advpro_game/assets/EliteMinionSprite.png",
                6, 6, 1, 30, 60);

        setHp(5);
        this.moveSpeed = moveSpeed;
        this.shootCooldownMs = 999999;
        this.shootRange = 0;

        if (fallbackNode != null) {
            fallbackNode.setFill(Color.DARKBLUE);
        }

        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, 2, 6, 30, 60, 300
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    2, 0, 4, 6, 30, 60, 120
            ));
        }
    }

    // New constructor with custom sprite parameters
    public EliteMinion(double x, double y, double w, double h, String spritePath,
                       int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(5);
        this.moveSpeed = 25.0;
        this.shootCooldownMs = 999999;
        this.shootRange = 0;

        // Define animations for custom sprite
        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 300
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 120
            ));
        }

        // Set color for fallback rectangle
        if (fallbackNode != null) {
            fallbackNode.setFill(Color.DARKBLUE);
        }
    }

    // Constructor with custom sprite parameters AND custom move speed
    public EliteMinion(double x, double y, double w, double h, String spritePath,
                       int frameCount, int columns, int rows, int frameWidth, int frameHeight,
                       double moveSpeed) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(5);
        this.moveSpeed = moveSpeed;
        this.shootCooldownMs = 999999;
        this.shootRange = 0;

        // Define animations for custom sprite
        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 300
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, columns), columns, frameWidth, frameHeight, 120
            ));
        }

        // Set color for fallback rectangle
        if (fallbackNode != null) {
            fallbackNode.setFill(Color.DARKBLUE);
        }
    }

    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        if (!dead && fallbackNode != null) {
            fallbackNode.setFill(Color.MEDIUMBLUE);
        }
        return dead;
    }

    @Override
    public Bullet tryShoot(GameCharacter player) {
        return null;
    }
}
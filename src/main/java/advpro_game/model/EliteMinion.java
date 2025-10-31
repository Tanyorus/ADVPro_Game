package advpro_game.model;

import javafx.scene.paint.Color;

public class EliteMinion extends Enemy {

    // -------------------- Backward-compat constructor (sprite-based) --------------------
    public EliteMinion(double x, double y) {
        super(x, y, 30, 60,
                "/advpro_game/assets/EliteMinionSprite.png",
                6, 6, 1, 30, 60);

        setHp(5);
        this.moveSpeed = 25.0;     // fast-ish minion
        this.shootCooldownMs = 999_999; // never shoots
        this.shootRange = 0;

        // Fallback rectangle tint
        if (fallback != null) fallback.setFill(Color.DARKBLUE);

        // Define basic animations if the sprite is available
        if (sprite != null) {
            // idle: first 2 frames
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, 2, 6, 30, 60, 300
            ));
            // run: next 4 frames
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    2, 0, 4, 6, 30, 60, 120
            ));
            sprite.setAction(AnimatedSprite.Action.idle);
        }
    }

    // -------------------- Sprite-based, custom move speed --------------------
    public EliteMinion(double x, double y, double moveSpeed) {
        super(x, y, 30, 60,
                "/advpro_game/assets/EliteMinionSprite.png",
                6, 6, 1, 30, 60);

        setHp(5);
        this.moveSpeed = moveSpeed;
        this.shootCooldownMs = 999_999;
        this.shootRange = 0;

        if (fallback != null) fallback.setFill(Color.DARKBLUE);

        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, 2, 6, 30, 60, 300
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    2, 0, 4, 6, 30, 60, 120
            ));
            sprite.setAction(AnimatedSprite.Action.idle);
        }
    }

    // -------------------- Fully custom sprite parameters --------------------
    public EliteMinion(double x, double y, double w, double h, String spritePath,
                       int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(5);
        this.moveSpeed = 25.0;
        this.shootCooldownMs = 999_999;
        this.shootRange = 0;

        if (sprite != null) {
            // Generic: map entire sheet; tweak delays to your taste
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, Math.max(1, columns * rows)), columns, frameWidth, frameHeight, 300
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, Math.max(1, columns * rows)), columns, frameWidth, frameHeight, 120
            ));
            sprite.setAction(AnimatedSprite.Action.idle);
        }

        if (fallback != null) fallback.setFill(Color.DARKBLUE);
    }

    // -------------------- Fully custom sprite + custom move speed --------------------
    public EliteMinion(double x, double y, double w, double h, String spritePath,
                       int frameCount, int columns, int rows, int frameWidth, int frameHeight,
                       double moveSpeed) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(5);
        this.moveSpeed = moveSpeed;
        this.shootCooldownMs = 999_999;
        this.shootRange = 0;

        if (sprite != null) {
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, Math.max(1, columns * rows)), columns, frameWidth, frameHeight, 300
            ));
            sprite.define(AnimatedSprite.Action.run, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, Math.max(1, columns * rows)), columns, frameWidth, frameHeight, 120
            ));
            sprite.setAction(AnimatedSprite.Action.idle);
        }

        if (fallback != null) fallback.setFill(Color.DARKBLUE);
    }

    // -------------------- Rectangle-only variant (works with merged Enemy) --------------------
    public static EliteMinion rectangleOnly(double x, double y) {
        EliteMinion m = new EliteMinion(x, y, 30, 60,
                /*spritePath*/ null, 0,0,0,0,0);
        // The super(...) above will fall back automatically when spritePath is null/invalid
        m.setHp(5);
        m.moveSpeed = 100; // faster for the rectangle-only flavor (like your v2)
        m.shootCooldownMs = 999_999;
        m.shootRange = 0;
        if (m.fallback != null) m.fallback.setFill(Color.DARKBLUE);
        return m;
    }

    // -------------------- Combat behavior --------------------
    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        if (!dead && fallback != null) {
            fallback.setFill(Color.MEDIUMBLUE);
        }
        return dead;
    }

    @Override
    public Bullet tryShoot(GameCharacter player) {
        // Elite minions don't shoot
        return null;
    }
}

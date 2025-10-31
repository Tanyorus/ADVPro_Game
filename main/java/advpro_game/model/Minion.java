package advpro_game.model;

import javafx.scene.paint.Color;

public class Minion extends Enemy {

    // -------- Rectangle-only (backward compatible) --------
    public Minion(double x, double y) {
        super(x, y, 24, 40);
        setHp(1);

        // Stationary shooter
        this.moveSpeed       = 0;
        this.shootCooldownMs = 5000;          // 5s between shots
        this.shootRange      = 300;
        this.lastShotTime    = System.currentTimeMillis() - (long)(Math.random() * 3000);

        if (fallback != null) fallback.setFill(Color.DARKRED);
    }

    // -------- Sprite-capable variant --------
    public Minion(double x, double y, double w, double h, String spritePath,
                  int frameCount, int columns, int rows, int frameWidth, int frameHeight) {
        super(x, y, w, h, spritePath, frameCount, columns, rows, frameWidth, frameHeight);

        setHp(1);
        this.moveSpeed       = 0;
        this.shootCooldownMs = 5000;
        this.shootRange      = 300;
        this.lastShotTime    = System.currentTimeMillis() - (long)(Math.random() * 3000);

        // Define simple animations if a sheet is available
        if (sprite != null) {
            // idle: loop a small number of frames (tweak as needed for your sheet)
            sprite.define(AnimatedSprite.Action.idle, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, Math.max(1, columns * rows)),
                    columns, frameWidth, frameHeight, 150
            ));
            // shoot: reuse same strip but a bit faster
            sprite.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(
                    0, 0, Math.min(frameCount, Math.max(1, columns * rows)),
                    columns, frameWidth, frameHeight, 100
            ));
            sprite.setAction(AnimatedSprite.Action.idle);
        }

        // Fallback tint if sprite missing
        if (fallback != null) fallback.setFill(Color.DARKRED);
    }

    // -------- Hit feedback --------
    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        if (!dead && fallback != null) {
            fallback.setFill(Color.FIREBRICK);
        }
        return dead;
    }
}

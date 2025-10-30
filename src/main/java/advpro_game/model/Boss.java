package advpro_game.model;

import javafx.scene.paint.Color;

public class Boss extends Enemy {
    private static final int BASE_HP = 12;

    public Boss(double x, double y) {
        super(x, y, 48, 72);
        setHp(BASE_HP);

        // Bosses don't move but shoot frequently with long range
        this.moveSpeed = 0;
        this.shootCooldownMs = 1000;  // 1 second
        this.shootRange = 600;

        // Purple color for boss
        node.setFill(Color.DARKVIOLET);
    }

    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        // Change color as health decreases
        if (!dead) {
            if (getHp() > BASE_HP / 2) {
                node.setFill(Color.MEDIUMPURPLE);
            } else {
                node.setFill(Color.ORCHID);
            }
        }
        return dead;
    }
}
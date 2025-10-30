package advpro_game.model;

import javafx.scene.paint.Color;

public class EliteMinion extends Enemy {
    public EliteMinion(double x, double y) {
        super(x, y, 30, 60);
        setHp(5);

        // Elite minions move but don't shoot
        this.moveSpeed = 100;
        this.shootCooldownMs = 999999;  // Never shoot (very long cooldown)
        this.shootRange = 0;  // No shoot range

        // Different color to distinguish them
        node.setFill(Color.DARKBLUE);
    }

    @Override
    public boolean hit(int dmg) {
        boolean dead = super.hit(dmg);
        // Keep blue color unless dead
        if (!dead) {
            node.setFill(Color.MEDIUMBLUE);
        }
        return dead;
    }

    @Override
    public Bullet tryShoot(GameCharacter player) {
        // Elite minions don't shoot
        return null;
    }
}
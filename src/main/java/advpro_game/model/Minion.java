package advpro_game.model;

public class Minion extends Enemy {
    public Minion(double x, double y) {
        super(x, y, 24, 40);
        setHp(1);

        // Minions don't move
        this.moveSpeed = 0;
        this.shootCooldownMs = 5000;  // 5 seconds
        this.shootRange = 300;

        // Add random initial delay so they don't all shoot at once
        this.lastShotTime = System.currentTimeMillis() - (long)(Math.random() * 3000);
    }
}
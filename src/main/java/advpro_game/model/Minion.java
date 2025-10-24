package advpro_game.model;

public class Minion extends Enemy {
    public Minion(double x, double y) {
        super(x, y, 24, 36);
        setHp(1);
    }
}

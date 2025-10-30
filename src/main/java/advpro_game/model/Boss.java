package advpro_game.model;

public class Boss extends Enemy {
    private static final int BASE_HP = 12;
    public Boss(double x, double y) {
        super(x, y, 48, 72);
        setHp(BASE_HP);
    }
}
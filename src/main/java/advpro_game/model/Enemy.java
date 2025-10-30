package advpro_game.model;

import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class Enemy {
    protected double x, y, w, h;
    private int hp = 1;
    protected final Rectangle node;

    public Enemy(double x, double y, double w, double h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        node = new Rectangle(w, h, Color.DARKRED);
        node.setTranslateX(x);
        node.setTranslateY(y);
    }

    public Rectangle2D getHitbox() { return new Rectangle2D(x, y, w, h); }
    public Rectangle getNode() { return node; }

    // HP API
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = Math.max(0, hp); }
    public void addHp(int delta) { setHp(this.hp + delta); }
    public boolean isDead() { return hp <= 0; }

    /** returns true if dead after hit */
    public boolean hit(int dmg) {
        setHp(hp - Math.max(0, dmg));
        node.setFill(isDead()? Color.GRAY : Color.FIREBRICK);
        return isDead();
    }
}
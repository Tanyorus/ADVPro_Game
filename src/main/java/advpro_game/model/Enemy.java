package advpro_game.model;

import javafx.animation.FillTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class Enemy {
    private double x, y, w, h;
    private final Rectangle node;

    // simple health / i-frames
    private int health = 3;
    private boolean alive = true;
    private long lastHitMs = 0;
    private static final int IFRAME_MS = 180;

    public Enemy(double x, double y, double w, double h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        node = new Rectangle(w, h);
        node.setFill(Color.DARKRED);
        node.setStroke(Color.BLACK);
        node.setStrokeWidth(1);
        node.setManaged(false);           // no layout interference
        node.setMouseTransparent(true);   // don't steal clicks
        syncNode();
    }

    private void syncNode() {
        node.setTranslateX(x);
        node.setTranslateY(y);
    }

    /** Axis-aligned world hitbox */
    public Rectangle2D getHitbox() { return new Rectangle2D(x, y, w, h); }

    /** Scene node for layering in GameStage */
    public Rectangle getNode() { return node; }

    /** Called when a bullet hits this enemy. Includes short flash + i-frames. */
    public void hit() {
        if (!alive) return;

        long now = System.currentTimeMillis();
        if (now - lastHitMs < IFRAME_MS) return; // ignore rapid multi-hits same frame
        lastHitMs = now;

        health = Math.max(0, health - 1);

        // Flash feedback (non-blocking)
        Color from = (Color) node.getFill();
        Color to = Color.GRAY;
        FillTransition ft = new FillTransition(Duration.millis(120), node, from, to);
        ft.setAutoReverse(true);
        ft.setCycleCount(2);
        ft.setOnFinished(e -> { if (alive) node.setFill(from); });
        ft.play();

        if (health == 0) {
            alive = false;
            node.setFill(Color.DARKGRAY);
            node.setOpacity(0.85);
        }
    }

    // ---- Optional helpers for later AI/movement/respawn ----
    public void setPosition(double nx, double ny) { this.x = nx; this.y = ny; syncNode(); }
    public void moveBy(double dx, double dy)      { this.x += dx; this.y += dy; syncNode(); }
    public void update(double dtSeconds) { /* add patrol/logic here if you want */ }

    public boolean isAlive() { return alive; }
    public int getHealth()   { return health; }

    public void revive(int hp, double nx, double ny) {
        this.health = Math.max(1, hp);
        this.alive = true;
        this.x = nx; this.y = ny;
        syncNode();
        node.setOpacity(1.0);
        node.setFill(Color.DARKRED);
    }
}

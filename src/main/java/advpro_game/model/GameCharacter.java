package advpro_game.model;

import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import advpro_game.Launcher;
import advpro_game.view.GameStage;

public class GameCharacter extends Pane {
    private Image characterImg;
    private AnimatedSprite imageView;

    public enum Action { idle, run, jump, prone, shoot }

    // LOGICAL position (source of truth)
    private int x, y, startX, startY;
    private int characterWidth, characterHeight;
    private int score = 0;

    private KeyCode leftKey, rightKey, upKey, downKey;
    private MouseButton leftButton;

    // simple discrete “per frame” velocities/accels that match your current code
    int xVelocity = 0, yVelocity = 0;
    int xAcceleration = 1, yAcceleration = 1;
    int xMaxVelocity = 8 , yMaxVelocity = 24;

    boolean isMoveLeft = false, isMoveRight = false;
    boolean isFalling = true;
    boolean canJump = false, isJumping = false;
    int life = 3;

    // One-way drop-through (double tap DOWN)
    private long ignorePlatformsUntilMs = 0L;
    private long lastDownTapMs = 0L;
    private boolean wasDownPressed = false;

    // Shooting
    private long lastShotMs = 0L;
    private int shotCooldownMs = 120; // ~8.3 shots/sec

    // --- Collider constants (MUST match getHitbox) ---
    private static final int COL_W = 30;
    private static final int COL_H = 60;
    private static final int COL_OFF_Y = 30;
    private int colOffX() { return (getScaleX() > 0 ? 43 : 65 + 8 - COL_W); }
    private int colliderBottomY() { return y + COL_OFF_Y + COL_H; }
    private int spriteYForColliderBottom(int bottomY) { return bottomY - (COL_OFF_Y + COL_H); }

    // debug prev position
    public int prevX, prevY;
    public void beginFrame() { prevX = x; prevY = y; }

    // --------Constructor----------//
    public GameCharacter(int id, int x, int y, String imgName,
                         int count, int column, int row, int width, int height,
                         KeyCode leftKey, KeyCode rightKey, KeyCode upKey, KeyCode downKey, MouseButton mouseButton1) {
        this.startX = x; this.startY = y; this.x = x; this.y = y;
        this.characterWidth = width;
        this.characterHeight = height;

        this.characterImg = new Image(Launcher.class.getResourceAsStream(imgName));
        this.imageView = new AnimatedSprite(characterImg, count, column, row, 0, 0, width, height);

        // visual scale (same as before)
        this.imageView.setFitWidth((int) (width * 1.8));
        this.imageView.setFitHeight((int) (height * 1.8));

        this.leftKey = leftKey; this.rightKey = rightKey; this.upKey = upKey; this.downKey = downKey;
        this.leftButton = mouseButton1;

        this.getChildren().addAll(this.imageView);
        setScaleX(id % 2 * 2 - 1); // face direction by id as before

        int frameW = width, frameH = height;
        imageView.define(AnimatedSprite.Action.run,   new AnimatedSprite.ActionSpec(0,  0, 12, 16, frameW, frameH, 80));
        imageView.define(AnimatedSprite.Action.jump,  new AnimatedSprite.ActionSpec(12, 1,  4, 16, frameW, frameH,100));
        imageView.define(AnimatedSprite.Action.prone, new AnimatedSprite.ActionSpec(14, 0,  2, 16, frameW, frameH,120));
        imageView.define(AnimatedSprite.Action.idle,  new AnimatedSprite.ActionSpec(0,  0,  1, 16, frameW, frameH,500));
        imageView.setAction(AnimatedSprite.Action.idle);

        // match visuals to logic at start
        setTranslateX(this.x);
        setTranslateY(this.y);
    }

    // ---------------- Movement & State ----------------
    public void moveLeft() {
        setScaleX(-1);
        isMoveLeft = true; isMoveRight = false;
        imageView.setAction(AnimatedSprite.Action.run);
    }
    public void moveRight() {
        setScaleX(1);
        isMoveLeft = false; isMoveRight = true;
        imageView.setAction(AnimatedSprite.Action.run);
    }
    public void stop() {
        isMoveLeft = false; isMoveRight = false;
        if (!isJumping && !isFalling) imageView.setAction(AnimatedSprite.Action.idle);
    }
    public void prone() {
        isJumping = false; isMoveRight = false; isMoveLeft = false;
        imageView.setAction(AnimatedSprite.Action.prone);
    }

    // === Physics (discrete) ===
    private void stepHorizontal() {
        if (isMoveLeft) {
            xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration);
            x -= xVelocity;
        }
        if (isMoveRight) {
            xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration);
            x += xVelocity;
        }
        if (!isMoveLeft && !isMoveRight) xVelocity = 0;
    }

    private void stepVertical() {
        if (isFalling) {
            yVelocity = Math.min(yMaxVelocity, yVelocity + yAcceleration);
            y += yVelocity;
        } else if (isJumping) {
            yVelocity = Math.max(0, yVelocity - yAcceleration);
            y -= yVelocity;
        }
    }

    public void checkReachGameWall() {
        if (x <= 0) x = 0;
        else if (x + getCharacterWidth() >= GameStage.WIDTH)
            x = GameStage.WIDTH - getCharacterWidth();
    }

    public void jump() {
        if (canJump) {
            yVelocity = yMaxVelocity;
            canJump = false;
            isJumping = true;
            isFalling = false;
            imageView.setAction(AnimatedSprite.Action.jump);
        }
    }

    public void jumpForward(int direction) {
        if (canJump) {
            yVelocity = yMaxVelocity;
            xVelocity = xMaxVelocity;
            canJump = false;
            isJumping = true;
            isFalling = false;

            x += (int) (direction * xVelocity * 1.2);
            setScaleX(direction);
            imageView.setAction(AnimatedSprite.Action.jump);
        }
    }

    // === DOWN key handling for drop-through ===
    public void handleDownKey(boolean downPressed) {
        long now = System.currentTimeMillis();
        if (downPressed && !wasDownPressed) {
            if (now - lastDownTapMs <= 250) {
                ignorePlatformsUntilMs = now + 220; // short window to drop
                y += 2; // ensure we’re not still touching top face
            }
            lastDownTapMs = now;
        }
        wasDownPressed = downPressed;
    }
    public boolean isIgnoringOneWay() { return System.currentTimeMillis() < ignorePlatformsUntilMs; }

    public void checkReachHighest() {
        if (isJumping && yVelocity <= 0) {
            isJumping = false; isFalling = true; yVelocity = 0;
        }
    }

    public void checkReachFloor() {
        if (isFalling && colliderBottomY() >= GameStage.GROUND) {
            y = spriteYForColliderBottom(GameStage.GROUND);
            isFalling = false;
            canJump = true;
            yVelocity = 0;
            imageView.setAction((isMoveLeft || isMoveRight) ? AnimatedSprite.Action.run : AnimatedSprite.Action.idle);
        }
    }

    // ---------------- Animation/Frame hook ----------------
    /** Call this every loop with dt in milliseconds */
    public void repaint(double dtMs) {
        // 1) physics in logical space
        stepHorizontal();
        stepVertical();

        // 2) world bounds clamp
        checkReachGameWall();

        // 3) visual sync AFTER logic
        setTranslateX(x);
        setTranslateY(y);

        // 4) animation
        imageView.update(dtMs);
    }
    public void repaint() { repaint(16.7); } // ~60fps default

    // ---------------- Collision vs Players ----------------
    public boolean collided(GameCharacter c) {
        Rectangle2D aBox = this.getHitbox();
        Rectangle2D bBox = c.getHitbox();
        if (aBox.intersects(bBox)) {
            if (this.isMoveLeft && this.x > c.getX()) {
                this.x = Math.max(this.x, c.getX() + c.getCharacterWidth());
                this.stop();
            } else if (this.isMoveRight && this.x < c.getX()) {
                this.x = Math.min(this.x, c.getX() - this.characterWidth);
                this.stop();
            }
            // Land on top of the other player's collider
            if (this.isFalling && this.y < c.getY()) {
                int otherTop = (int) c.getHitbox().getMinY();
                this.y = spriteYForColliderBottom(otherTop);
                score++;
                this.repaint(0);
                c.collapsed();
                c.respawn();
                return true;
            }
        }
        return false;
    }

    // ---------------- Platforms: clean landing (top-face only) ----------------
    public void checkPlatformCollision(Iterable<Platform> platforms) {
        boolean onPlatform = false;

        Rectangle2D playerNow = this.getHitbox(); // using (x,y)
        Rectangle2D playerNextFall = new Rectangle2D(
                playerNow.getMinX(), playerNow.getMinY() + Math.max(0, yVelocity),
                playerNow.getWidth(), playerNow.getHeight()
        );

        for (Platform p : platforms) {
            Rectangle2D platformBox = p.getHitbox();

            // Only check landing when falling, not jumping
            if (!isFalling) continue;

            // Drop-through window
            if (isIgnoringOneWay()) continue;

            // Must be horizontally overlapping
            boolean horizontalOverlap = playerNow.getMaxX() > platformBox.getMinX() &&
                    playerNow.getMinX() < platformBox.getMaxX();

            // Crossed the top face this frame?
            boolean crossedTop = playerNow.getMaxY() <= platformBox.getMinY() &&
                    playerNextFall.getMaxY() >= platformBox.getMinY();

            if (horizontalOverlap && crossedTop) {
                // Snap so collider bottom sits on platform top
                y = spriteYForColliderBottom((int) platformBox.getMinY());
                yVelocity = 0;
                isFalling = false;
                canJump = true;
                onPlatform = true;
                break;
            }
        }

        if (!onPlatform && !isJumping) {
            // If not standing on any platform and not in a jump ascent, fall
            isFalling = true;
            canJump = false;
        }
    }

    // Non-blocking “collapse” (no sleep on FX thread)
    public void collapsed() {
        this.imageView.setFitHeight(5);
        this.y = this.y + this.characterHeight - 5;
        this.repaint(0);
        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.play();
    }

    public void respawn() {
        this.x = this.startX; this.y = this.startY;
        this.xVelocity = 0; this.yVelocity = 0;
        this.isMoveLeft = false; this.isMoveRight = false;
        this.isFalling = true; this.canJump = false; this.isJumping = false;

        // small grace so we don't re-collide with the spawn platform underside
        this.ignorePlatformsUntilMs = System.currentTimeMillis() + 150;

        // restore sprite size
        this.imageView.setFitWidth((int) (this.characterWidth * 1.8));
        this.imageView.setFitHeight((int) (this.characterHeight * 1.8));

        imageView.setAction(AnimatedSprite.Action.idle);

        setTranslateX(this.x);
        setTranslateY(this.y);
    }

    // ---------------- Hitbox & Shooting (use logical x,y) ----------------
    public Rectangle2D getHitbox(){
        return new Rectangle2D(this.x + colOffX(), this.y + COL_OFF_Y, COL_W, COL_H);
    }

    public Bullet shoot(){
        double mx = this.x + (getScaleX() > 0 ? 62 : 3);
        double my = this.y + 34; // fixed: constant pixel offset
        int dir = (getScaleX() > 0 ? 1 : -1);
        return new Bullet(mx, my, dir);
    }

    // -------- getters / setters --------
    public int getY() { return y; }
    public int getX() { return x; }
    public void setY(int y) { this.y = y; }
    public void setX(int x) { this.x = x; }
    public void setFalling(boolean falling) {isFalling = falling;}
    public void setCanJump(boolean canJump) {this.canJump = canJump;}

    public int getCharacterWidth() { return characterWidth; }
    public void setCharacterWidth(int w) { this.characterWidth = w; }
    public int getCharacterHeight() { return characterHeight; }
    public void setCharacterHeight(int h) { this.characterHeight = h; }
    public int getScore() { return score; }
    public KeyCode getLeftKey() { return leftKey; }
    public KeyCode getRightKey() { return rightKey; }
    public KeyCode getUpKey() { return upKey; }
    public KeyCode getDownKey() { return downKey; }
    public MouseButton getLeftButton() { return leftButton;}
    public AnimatedSprite getImageView() { return imageView; }

    public int getyMaxVelocity() { return yMaxVelocity; }
    public void setyMaxVelocity(int yMaxVelocity) { this.yMaxVelocity = yMaxVelocity; }
    public int getyVelocity() { return yVelocity; }
    public void setyVelocity(int yVelocity) { this.yVelocity = yVelocity; }
    public int getxVelocity() { return xVelocity; }
    public void setxVelocity(int xVelocity) { this.xVelocity = xVelocity; }
    public int getxMaxVelocity() { return xMaxVelocity; }
    public void setxMaxVelocity(int xMaxVelocity) { this.xMaxVelocity = xMaxVelocity; }
    public int getyAcceleration() { return yAcceleration; }
    public void setyAcceleration(int yAcceleration) { this.yAcceleration = yAcceleration; }
    public int getxAcceleration() { return xAcceleration; }
    public void setxAcceleration(int xAcceleration) { this.xAcceleration = xAcceleration; }

    // === Aiming & Firing (8-way) ===
    public static class Shot {
        public final double x, y, dx, dy;
        public Shot(double x, double y, double dx, double dy) { this.x=x; this.y=y; this.dx=dx; this.dy=dy; }
    }

    public Shot computeShot(advpro_game.model.Keys keys) {
        boolean up = keys.isPressed(getUpKey());
        boolean left = keys.isPressed(getLeftKey());
        boolean right = keys.isPressed(getRightKey());
        boolean down = keys.isPressed(getDownKey());
        double dx = 0, dy = 0;
        if (up && (left || right)) { dy = -1; dx = right ? 1 : -1; }
        else if (up)               { dy = -1; }
        else if (down)             { dy =  1; }
        else                       { dx = (getScaleX() < 0) ? -1 : 1; }
        double muzzleX = getX() + getCharacterWidth() / 2.0;
        double muzzleY = getY() + getCharacterHeight() * 0.45;
        if (dx == 0 && dy == 0) dx = 1;
        return new Shot(muzzleX, muzzleY, dx, dy);
    }

    /** Old signature kept (returns boolean). Leaves adding the Node to the caller (loop). */
    public boolean tryFire(advpro_game.model.Keys keys, java.util.List<advpro_game.model.Bullet> bullets) {
        Bullet b = tryCreateBullet(keys);
        if (b == null) return false;
        bullets.add(b);
        return true;
    }

    /** New helper: returns a Bullet if fired (so the loop can also add its Node). */
    public Bullet tryCreateBullet(advpro_game.model.Keys keys) {
        if (!keys.isClicked(javafx.scene.input.MouseButton.PRIMARY)) return null;
        long now = System.currentTimeMillis();
        if (now - lastShotMs < shotCooldownMs) return null;
        Shot s = computeShot(keys);
        lastShotMs = now;
        return new advpro_game.model.Bullet(s.x, s.y, s.dx, s.dy, 480.0, 1);
    }
}

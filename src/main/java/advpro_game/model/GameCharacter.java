package advpro_game.model;

import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import advpro_game.Launcher;
import advpro_game.view.GameStage;
import java.util.concurrent.TimeUnit;

public class GameCharacter extends Pane {
    private Image characterImg;
    private AnimatedSprite imageView;

    public enum Action { idle, run, jump, prone, shoot }

    private int x, y, startX, startY;
    private int characterWidth, characterHeight;
    private int score = 0;

    private KeyCode leftKey, rightKey, upKey, downKey;
    int xVelocity = 0, yVelocity = 0;
    int xAcceleration = 1, yAcceleration = 1;
    int xMaxVelocity = 7, yMaxVelocity = 17;
    boolean isMoveLeft = false, isMoveRight = false;
    boolean isFalling = true;
    boolean canJump = false, isJumping = false;
    int life = 3;

    public GameCharacter(int id, int x, int y, String imgName,
                         int count, int column, int row, int width, int height,
                         KeyCode leftKey, KeyCode rightKey, KeyCode upKey, KeyCode downKey) {
        this.startX = x; this.startY = y; this.x = x; this.y = y;
        this.setTranslateX(x);
        this.setTranslateY(y);
        this.characterWidth = width;
        this.characterHeight = height;

        this.characterImg = new Image(Launcher.class.getResourceAsStream(imgName));

        // AnimatedSprite with your original signature
        this.imageView = new AnimatedSprite(characterImg, count, column, row, 0, 0, width, height);

        // Scale for visual size — keep your multiplier
        this.imageView.setFitWidth((int) (width * 1.8));
        this.imageView.setFitHeight((int) (height * 1.8));

        this.leftKey = leftKey; this.rightKey = rightKey; this.upKey = upKey; this.downKey = downKey;

        this.getChildren().addAll(this.imageView);
        setScaleX(id % 2 * 2 - 1); // face direction by id as before

        // ----------------------------
        // DEFINE ACTION REGIONS (ONE PNG)
        // Adjust these to match your sheet rows/cols
        // ----------------------------
        int frameW = width, frameH = height;

        imageView.define(AnimatedSprite.Action.run,
                new AnimatedSprite.ActionSpec(0, 0, 12, 16, frameW, frameH, 80));

        imageView.define(AnimatedSprite.Action.jump,
                new AnimatedSprite.ActionSpec(12, 1, 4, 16, frameW, frameH, 100));

        imageView.define(AnimatedSprite.Action.prone,
                new AnimatedSprite.ActionSpec(14, 0, 2, 16, frameW, frameH, 120));

        imageView.define(AnimatedSprite.Action.idle,
                new AnimatedSprite.ActionSpec(0,0,1,16, frameW, frameH, 500));

        imageView.setAction(AnimatedSprite.Action.idle);
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

    public void moveX() {
        setTranslateX(x);
        if (isMoveLeft) {
            xVelocity = xVelocity >= xMaxVelocity ? xMaxVelocity : xVelocity + xAcceleration;
            x -= xVelocity;
        }
        if (isMoveRight) {
            xVelocity = xVelocity >= xMaxVelocity ? xMaxVelocity : xVelocity + xAcceleration;
            x += xVelocity;
        }
        if (!isMoveLeft && !isMoveRight) xVelocity = 0;
    }

    public void moveY() {
        setTranslateY(y);
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
        else if (x + getWidth() >= GameStage.WIDTH)
            x = GameStage.WIDTH - (int) getWidth();
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
            xVelocity = xMaxVelocity / 2;
            canJump = false;
            isJumping = true;
            isFalling = false;

            //Move slightly forward
            x +=  direction *xVelocity;
            setScaleX(direction); // face the correct way
            imageView.setAction(AnimatedSprite.Action.jump);
        }
    }

    public void checkReachHighest() {
        if (isJumping && yVelocity <= 0) {
            isJumping = false; isFalling = true; yVelocity = 0;
            // Keep jump action while falling if your sheet has rising+falling frames together.
        }
    }

    public void checkReachFloor() {
        if (isFalling && y >= GameStage.GROUND - this.characterHeight) {
            y = GameStage.GROUND - this.characterHeight;
            isFalling = false;
            canJump = true;
            yVelocity = 0;

            if (isMoveLeft) imageView.setAction(AnimatedSprite.Action.run);
            else if (isMoveRight) imageView.setAction(AnimatedSprite.Action.run);
            imageView.setAction(AnimatedSprite.Action.idle);
        }
    }

    // ---------------- Animation hook ----------------
    /** Call this every loop with dt in milliseconds */
    public void repaint(double dtMs) {
        moveX();
        moveY();
        imageView.update(dtMs);   // advance animation by time
    }

    /** Backward compat if someone still calls repaint() without dt */
    public void repaint() {
        repaint(16.7); // ~60fps default
    }

    // --------------- Collision (unchanged) ---------------
    public boolean collided(GameCharacter c) {
        if (this.isMoveLeft && this.x > c.getX()) {
            this.x = Math.max(this.x, c.getX() + c.getCharacterWidth());
            this.stop();
        } else if (this.isMoveRight && this.x < c.getX()) {
            this.x = Math.min(this.x, c.getX() - this.characterWidth);
            this.stop();
        }
        if (this.isFalling && this.y < c.getY()) {
            this.y = Math.min(GameStage.GROUND - this.characterHeight, c.getY());
            score++;
            this.repaint(0);
            c.collapsed();
            c.respawn();
            return true;
        }
        return false;
    }

    public void collapsed() {
        this.imageView.setFitHeight(5);
        this.y = this.y + this.characterHeight - 5;
        this.repaint(0);
        try { TimeUnit.MILLISECONDS.sleep(300); } catch (InterruptedException e) { e.printStackTrace(); }
    }

    public void respawn() {
        this.x = this.startX; this.y = this.startY;
        this.imageView.setFitWidth(this.characterWidth);
        this.imageView.setFitHeight(this.characterHeight);
        this.isMoveLeft = false; this.isMoveRight = false;
        this.isFalling = true; this.canJump = false; this.isJumping = false;
        imageView.setAction(AnimatedSprite.Action.idle);
    }

    // -------- getters you already had (kept) --------
    public int getY() { return y; }
    public int getX() { return x; }
    public int getCharacterWidth() { return characterWidth; }
    public void setCharacterWidth(int w) { this.characterWidth = w; }
    public int getCharacterHeight() { return characterHeight; }
    public void setCharacterHeight(int h) { this.characterHeight = h; }
    public int getScore() { return score; }
    public KeyCode getLeftKey() { return leftKey; }
    public KeyCode getRightKey() { return rightKey; }
    public KeyCode getUpKey() { return upKey; }
    public KeyCode getDownKey() { return downKey; }
    public AnimatedSprite getImageView() { return imageView; }
}

package advpro_game.model;

import javafx.animation.Animation;
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

    public enum Action { idle, run, jump, prone, shoot }

    private Image characterImg;
    private AnimatedSprite imageView;

    // -------- Logical state (source of truth) --------
    private int x, y, startX, startY;
    private final int characterWidth, characterHeight;
    private int score = 0;
    private int lives = 3;                // default 3 lives

    private final KeyCode leftKey, rightKey, upKey, downKey;
    private final MouseButton leftButton;

    // Per-frame discrete kinematics
    private int xVelocity = 0, yVelocity = 0;
    private int xAcceleration = 1, yAcceleration = 1;
    private int xMaxVelocity = 8, yMaxVelocity = 24;

    private boolean isMoveLeft = false, isMoveRight = false;
    private boolean isFalling = true;
    private boolean canJump = false, isJumping = false;
    private boolean isProne = false;      // track prone state

    // Double-tap down drop-through
    private int dropTapWindowMs = 250;
    private int dropIgnoreMs    = 260;
    private long ignorePlatformsUntilMs = 0L;
    private long lastDownTapMs = 0L;
    private boolean wasDownPressed = false;

    // Shooting & pose
    private long lastShotMs = 0L;
    private int shotCooldownMs = 120;
    private int shootPoseHoldMs = 260;
    private PauseTransition shootRestorePT;

    // Recoil
    private int recoilMinPx = 1, recoilMaxPx = 3;
    private int recoilReturnMs = 70;

    // Collider box (relative to sprite)
    private static final int COL_W = 30;
    private static final int COL_H = 60;
    private static final int COL_OFF_Y = 30;
    private int colOffX() { return (getScaleX() > 0 ? 43 : 65 + 8 - COL_W); }
    private int colliderBottomY() { return y + COL_OFF_Y + COL_H; }
    private int spriteYForColliderBottom(int bottomY) { return bottomY - (COL_OFF_Y + COL_H); }

    // Shoot animation spec
    private static final int SPRITE_SHEET_COLS = 16;
    private static final int SHOOT_START_INDEX = 11;
    private static final int SHOOT_ROW         = 0;
    private static final int SHOOT_FRAMES      = 1;
    private static final int SHOOT_FRAME_MS    = 80;

    // Muzzle offsets (X is per-facing; Y is computed from character height)
    private int muzzleRightX = 62;
    private int muzzleLeftX  = 3;
    // standing ~45% height, prone ~65% height (lower)
    private double standingMuzzleYFactor = 0.45;
    private double proneMuzzleYFactor    = 0.65;

    // Debug previous position
    public int prevX, prevY;
    public void beginFrame() { prevX = x; prevY = y; }

    // -------- Tunables --------
    public void setShootPoseHoldMs(int ms) { shootPoseHoldMs = Math.max(60, ms); }
    public void setRecoilRange(int minPx, int maxPx) {
        recoilMinPx = Math.max(0, minPx);
        recoilMaxPx = Math.max(recoilMinPx, maxPx);
    }
    /** Set spawn X offsets and Y factors (0..1 from top). */
    public void setMuzzleParams(int rightX, int leftX, double standingYFactor, double proneYFactor) {
        muzzleRightX = rightX; muzzleLeftX = leftX;
        standingMuzzleYFactor = standingYFactor; proneMuzzleYFactor = proneYFactor;
    }
    public void setDropTiming(int doubleTapWindowMs, int ignoreMs) {
        dropTapWindowMs = Math.max(80, doubleTapWindowMs);
        dropIgnoreMs = Math.max(120, ignoreMs);
    }

    private static void runFx(Runnable r) {
        if (javafx.application.Platform.isFxApplicationThread()) r.run();
        else javafx.application.Platform.runLater(r);
    }

    // -------------------- Constructor --------------------
    public GameCharacter(int id, int x, int y, String imgName,
                         int count, int column, int row, int width, int height,
                         KeyCode leftKey, KeyCode rightKey, KeyCode upKey, KeyCode downKey, MouseButton mouseButton1) {
        this.startX = x; this.startY = y; this.x = x; this.y = y;
        this.characterWidth = width; this.characterHeight = height;
        this.leftKey = leftKey; this.rightKey = rightKey; this.upKey = upKey; this.downKey = downKey;
        this.leftButton = mouseButton1;

        this.characterImg = new Image(Launcher.class.getResourceAsStream(imgName));
        this.imageView = new AnimatedSprite(characterImg, count, column, row, 0, 0, width, height);

        runFx(() -> {
            imageView.setFitWidth((int) (width * 1.8));
            imageView.setFitHeight((int) (height * 1.8));
            getChildren().add(imageView);
            setScaleX(id % 2 * 2 - 1);
        });

        int frameW = width, frameH = height;
        imageView.define(AnimatedSprite.Action.run,   new AnimatedSprite.ActionSpec(0,  0, 12, SPRITE_SHEET_COLS, frameW, frameH, 80));
        imageView.define(AnimatedSprite.Action.jump,  new AnimatedSprite.ActionSpec(12, 1,  4, SPRITE_SHEET_COLS, frameW, frameH,100));
        imageView.define(AnimatedSprite.Action.prone, new AnimatedSprite.ActionSpec(14, 0,  2, SPRITE_SHEET_COLS, frameW, frameH,120));
        imageView.define(AnimatedSprite.Action.idle,  new AnimatedSprite.ActionSpec(0,  0,  1, SPRITE_SHEET_COLS, frameW, frameH,500));
        imageView.define(AnimatedSprite.Action.shoot, new AnimatedSprite.ActionSpec(SHOOT_START_INDEX, SHOOT_ROW, SHOOT_FRAMES, SPRITE_SHEET_COLS, frameW, frameH, SHOOT_FRAME_MS));

        runFx(() -> {
            imageView.setAction(AnimatedSprite.Action.idle);
            setTranslateX(this.x);
            setTranslateY(this.y);
        });
    }

    // ---------------- Movement & State ----------------
    public void moveLeft()  { runFx(() -> setScaleX(-1)); isMoveLeft = true;  isMoveRight = false; isProne = false; setGroundAnim(AnimatedSprite.Action.run); }
    public void moveRight() { runFx(() -> setScaleX(1));  isMoveLeft = false; isMoveRight = true;  isProne = false; setGroundAnim(AnimatedSprite.Action.run); }
    public void stop()      { isMoveLeft = false; isMoveRight = false; if (!isJumping && !isFalling) { isProne = false; setGroundAnim(AnimatedSprite.Action.idle); } }
    public void prone()     { isJumping = false; isMoveRight = false; isMoveLeft = false; isProne = true; setGroundAnim(AnimatedSprite.Action.prone); }

    private void setGroundAnim(AnimatedSprite.Action a) {
        if (isInShootPose()) return;
        runFx(() -> imageView.setAction(a));
    }

    private void stepHorizontal() {
        if (isMoveLeft)  { xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration); x -= xVelocity; }
        if (isMoveRight) { xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration); x += xVelocity; }
        if (!isMoveLeft && !isMoveRight) xVelocity = 0;
        clampToWalls();
    }
    private void stepVertical() {
        if (isFalling)   { yVelocity = Math.min(yMaxVelocity, yVelocity + yAcceleration); y += yVelocity; }
        else if (isJumping) { yVelocity = Math.max(0, yVelocity - yAcceleration); y -= yVelocity; }
    }
    private void clampToWalls() {
        if (x < 0) x = 0;
        int maxX = GameStage.WIDTH - characterWidth;
        if (x > maxX) x = maxX;
    }

    public void jump() {
        if (canJump) {
            yVelocity = yMaxVelocity; canJump = false; isJumping = true; isFalling = false; isProne = false;
            runFx(() -> imageView.setAction(AnimatedSprite.Action.jump));
        }
    }
    public void jumpForward(int direction) {
        if (canJump) {
            yVelocity = yMaxVelocity; xVelocity = xMaxVelocity; canJump = false; isJumping = true; isFalling = false; isProne = false;
            x += (int) (direction * xVelocity * 1.2);
            clampToWalls();
            runFx(() -> { setScaleX(direction); imageView.setAction(AnimatedSprite.Action.jump); });
        }
    }

    // ---- DOWN double-tap drop-through ----
    public void handleDownKey(boolean downPressed) {
        long now = System.currentTimeMillis();
        if (downPressed && !wasDownPressed) {
            if (now - lastDownTapMs <= dropTapWindowMs) {
                ignorePlatformsUntilMs = now + dropIgnoreMs;
                y += 3; isFalling = true; canJump = false;
            }
            lastDownTapMs = now;
        }
        wasDownPressed = downPressed;
    }
    public boolean isIgnoringOneWay() { return System.currentTimeMillis() < ignorePlatformsUntilMs; }

    public void checkReachHighest() {
        if (isJumping && yVelocity <= 0) { isJumping = false; isFalling = true; yVelocity = 0; }
    }
    public void checkReachFloor() {
        if (isFalling && colliderBottomY() >= GameStage.GROUND) onLandedAtTop(GameStage.GROUND);
    }

    private boolean isInShootPose() {
        return shootRestorePT != null && shootRestorePT.getStatus() == Animation.Status.RUNNING;
    }
    private void setGroundAnimIfAllowed() {
        if (isInShootPose()) return;
        runFx(() -> imageView.setAction(
                isProne ? AnimatedSprite.Action.prone :
                        (isMoveLeft || isMoveRight) ? AnimatedSprite.Action.run : AnimatedSprite.Action.idle));
    }
    private void onLandedAtTop(int topY) {
        y = spriteYForColliderBottom(topY);
        yVelocity = 0; isFalling = false; canJump = true;
        setGroundAnimIfAllowed();
    }

    // ---------------- Animation/Frame hook ----------------
    public void repaint(double dtMs) {
        stepHorizontal();
        stepVertical();
        clampToWalls();

        final int fx = x, fy = y;
        runFx(() -> {
            setTranslateX(fx);
            setTranslateY(fy);
            imageView.update(dtMs);
        });
    }
    public void repaint() { repaint(16.7); }

    // ---------------- Platforms: top-face only ----------------
    public void checkPlatformCollision(Iterable<Platform> platforms) {
        boolean stood = false;

        Rectangle2D playerNow = this.getHitbox();
        final int prevBottom = prevY + COL_OFF_Y + COL_H;
        final int currBottom = colliderBottomY();
        final boolean movingDown = currBottom > prevBottom;

        for (Platform p : platforms) {
            if (!movingDown) continue;
            if (!p.isSolid() && isIgnoringOneWay()) continue;

            Rectangle2D platformBox = p.getHitbox();
            boolean horizontal = playerNow.getMaxX() > platformBox.getMinX() &&
                    playerNow.getMinX() < platformBox.getMaxX();
            if (!horizontal) continue;

            int top = (int) platformBox.getMinY();
            boolean wasAbove = prevBottom <= top + 1;
            boolean nowBelow = currBottom >= top;

            if (wasAbove && nowBelow) {
                onLandedAtTop(top);
                stood = true; break;
            }
        }
        if (!stood && !isJumping) { isFalling = true; canJump = false; }
    }

    // ---------------- Collapse / Respawn ----------------
    public void collapsed() {
        runFx(() -> imageView.setFitHeight(5));
        this.y = this.y + this.characterHeight - 5;
        this.repaint(0);
        new PauseTransition(Duration.millis(300)).play();
    }
    public void respawn() {
        this.x = this.startX; this.y = this.startY;
        this.xVelocity = 0; this.yVelocity = 0;
        this.isMoveLeft = false; this.isMoveRight = false;
        this.isFalling = true; this.canJump = false; this.isJumping = false;
        this.ignorePlatformsUntilMs = System.currentTimeMillis() + 150;
        this.isProne = false;

        runFx(() -> {
            imageView.setFitWidth((int) (characterWidth * 1.8));
            imageView.setFitHeight((int) (characterHeight * 1.8));
            imageView.setAction(AnimatedSprite.Action.idle);
            setTranslateX(x); setTranslateY(y);
        });
    }

    // ---------------- Hitbox & Shooting ----------------
    public Rectangle2D getHitbox() {
        return new Rectangle2D(this.x + colOffX(), this.y + COL_OFF_Y, COL_W, COL_H);
    }

    private void playShootPoseAndRecoil(int facingDir) {
        runFx(() -> imageView.setAction(AnimatedSprite.Action.shoot));

        int recoil = recoilMinPx + (int)(Math.random() * (recoilMaxPx - recoilMinPx + 1));
        x -= facingDir * recoil;
        clampToWalls();

        PauseTransition back = new PauseTransition(Duration.millis(recoilReturnMs));
        back.setOnFinished(ev -> { x += facingDir * recoil; clampToWalls(); });
        back.play();

        int hold = Math.max(shootPoseHoldMs, shotCooldownMs + 40);
        if (shootRestorePT != null) shootRestorePT.stop();
        shootRestorePT = new PauseTransition(Duration.millis(hold));
        shootRestorePT.setOnFinished(ev -> setGroundAnimIfAllowed());
        shootRestorePT.playFromStart();
    }

    private double currentMuzzleY() {
        double factor = isProne ? proneMuzzleYFactor : standingMuzzleYFactor;
        return this.y + (this.characterHeight * factor);
    }
    private double currentMuzzleX() {
        int dir = (getScaleX() > 0 ? 1 : -1);
        return this.x + (dir > 0 ? muzzleRightX : muzzleLeftX);
    }

    /** Fire immediately (used by SPACE or mouse) */
    public Bullet shoot(){
        int dir = (getScaleX() > 0 ? 1 : -1);
        double mx = currentMuzzleX();
        double my = currentMuzzleY();  // middle-ish; lower when prone
        playShootPoseAndRecoil(dir);
        return new Bullet(mx, my, dir);
    }

    // -------- scoring & lives --------
    public void addScore(int delta){ this.score += delta; }
    public int getLives() { return lives; }
    public void setLives(int lives) { this.lives = Math.max(0, lives); }
    public void loseLife() { if (lives > 0) lives--; }
    public void gainLife() { lives++; }

    // -------- getters / setters --------
    public int getY() { return y; }
    public int getX() { return x; }
    public void setY(int y) { this.y = y; }
    public void setX(int x) { this.x = x; }
    public void setFalling(boolean falling) { isFalling = falling; }
    public void setCanJump(boolean canJump) { this.canJump = canJump; }
    public int getCharacterWidth() { return characterWidth; }
    public int getCharacterHeight() { return characterHeight; }
    public int getScore() { return score; }
    public KeyCode getLeftKey() { return leftKey; }
    public KeyCode getRightKey() { return rightKey; }
    public KeyCode getUpKey() { return upKey; }
    public KeyCode getDownKey() { return downKey; }
    public MouseButton getLeftButton() { return leftButton; }
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

    // === 8-way aim computation (used by tryCreateBullet) ===
    public static class Shot {
        public final double x, y, dx, dy;
        public Shot(double x, double y, double dx, double dy) { this.x=x; this.y=y; this.dx=dx; this.dy=dy; }
    }
    public Shot computeShot(advpro_game.model.Keys keys) {
        boolean up = keys.isPressed(getUpKey());
        boolean left = keys.isPressed(getLeftKey());
        boolean right = keys.isPressed(getRightKey());
        boolean down = keys.isPressed(getDownKey());

        // When PRONE (or down-without-move), force horizontal shot at lower muzzle
        boolean proneShoot = isProne || (down && !left && !right);

        double dx = 0, dy = 0;
        if (proneShoot) {
            dx = (getScaleX() < 0) ? -1 : 1;
            dy = 0;
        } else if (up && (left || right)) { dy = -1; dx = right ? 1 : -1; }
        else if (up)                      { dy = -1; }
        else if (down)                    { dy =  1; }
        else                              { dx = (getScaleX() < 0) ? -1 : 1; }

        double muzzleX = currentMuzzleX();
        double muzzleY = currentMuzzleY();
        if (dx == 0 && dy == 0) dx = 1;
        return new Shot(muzzleX, muzzleY, dx, dy);
    }

    /** Creates a bullet if trigger pressed & cooldown ok. */
    public Bullet tryCreateBullet(advpro_game.model.Keys keys) {
        boolean mouseShoot = keys.isClicked(javafx.scene.input.MouseButton.SECONDARY)
                || keys.isClicked(javafx.scene.input.MouseButton.PRIMARY);
        boolean keyShoot   = keys.isPressed(javafx.scene.input.KeyCode.SPACE);
        if (!mouseShoot && !keyShoot) return null;

        long now = System.currentTimeMillis();
        if (now - lastShotMs < shotCooldownMs) return null;

        Shot s = computeShot(keys);
        lastShotMs = now;

        int facing = (getScaleX() > 0 ? 1 : -1);
        playShootPoseAndRecoil(facing);

        return new Bullet(s.x, s.y, s.dx, s.dy, 480.0, 1);
    }
}

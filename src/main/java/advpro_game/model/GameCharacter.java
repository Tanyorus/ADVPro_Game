package advpro_game.model;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import java.util.function.Consumer;

import advpro_game.Launcher;
import advpro_game.view.GameStage;
import advpro_game.audio.AudioManager;

public class GameCharacter extends Pane {

    private Image characterImg;
    private AnimatedSprite imageView;

    // Bullet sink (GameStage wires this to add bullets safely on FX thread)
    private Consumer<Bullet> bulletSink;
    public void setBulletSink(Consumer<Bullet> sink) { this.bulletSink = sink; }

    // -------- Logical state (source of truth) --------
    private int x, y, startX, startY;
    private final int characterWidth, characterHeight;
    private int score = 0;
    private int lives = 50;
    private final KeyCode leftKey, rightKey, upKey, downKey;

    // Per-frame discrete kinematics
    private int xVelocity = 0, yVelocity = 0;
    private int xAcceleration = 1, yAcceleration = 1;
    private int xMaxVelocity = 3, yMaxVelocity = 21;

    private boolean isMoveLeft = false, isMoveRight = false;
    private boolean isFalling = true;
    private boolean canJump = false, isJumping = false;
    private boolean isProne = false;

    // Double-tap down drop-through
    private int dropTapWindowMs = 250;
    private int dropIgnoreMs    = 260;
    private long ignorePlatformsUntilMs = 0L;
    private long lastDownTapMs = 0L;
    private boolean wasDownPressed = false;

    // Shooting & pose
    private long lastShotMs = 0L;
    private int shotCooldownMs = 120;
    private long lastLaserMs = 0L;
    private int laserCooldownMs = 1200;
    private int laserDamage = 4;
    private int shootPoseHoldMs = 260;
    private PauseTransition shootRestorePT; // stand/run/jump shoot pose hold
    private PauseTransition runShootHoldPT; // hold for run+shoot strip

    // Recoil
    private int recoilMinPx = 1, recoilMaxPx = 3;
    private int recoilReturnMs = 70;

    // ---------- Collider: standing vs prone (bottom-aligned) ----------
    private static final int STAND_COL_W     = 25;
    private static final int STAND_COL_H     = 60;
    private static final int STAND_COL_OFF_Y = 30; // 30 + 60 = 90

    private static final int PRONE_COL_W     = 25;
    private static final int PRONE_COL_H     = 35;
    private static final int PRONE_COL_OFF_Y = 55; // 55 + 35 = 90 (same bottom)

    // Previous-frame snapshot (for stable platform crossing tests)
    public int prevX, prevY;
    private int prevColOffY = STAND_COL_OFF_Y;
    private int prevColH    = STAND_COL_H;

    private int currentColW()    { return isProne ? PRONE_COL_W     : STAND_COL_W; }
    private int currentColH()    { return isProne ? PRONE_COL_H     : STAND_COL_H; }
    private int currentColOffY() { return isProne ? PRONE_COL_OFF_Y : STAND_COL_OFF_Y; }

    // X offset depends on facing AND collider width
    private int colOffX() {
        int w = currentColW();
        return (getScaleX() > 0 ? 43 : 65 + 8 - w);
    }

    private int colliderBottomY() { return y + currentColOffY() + currentColH(); }
    private int spriteYForColliderBottom(int bottomY) { return bottomY - (currentColOffY() + currentColH()); }

    // Shoot animation spec
    private static final int SPRITE_SHEET_COLS = 16;
    private static final int SHOOT_START_INDEX = 11;
    private static final int SHOOT_ROW         = 0;
    private static final int SHOOT_FRAMES      = 1;
    private static final int SHOOT_FRAME_MS    = 80;

    // Muzzle offsets (X is per-facing; Y is computed from character height)
    private int muzzleRightX = 62;
    private int muzzleLeftX  = 3;
    // standing ~77% height, prone ~97% height (lower)
    private double standingMuzzleYFactor = 0.77;
    private double proneMuzzleYFactor    = 0.99;

    // -------- Tunables --------
    public void setShootPoseHoldMs(int ms) { shootPoseHoldMs = Math.max(60, ms); }
    public void setRecoilRange(int minPx, int maxPx) {
        recoilMinPx = Math.max(0, minPx);
        recoilMaxPx = Math.max(recoilMinPx, maxPx);
    }
    public void setMuzzleParams(int rightX, int leftX, double standingYFactor, double proneYFactor) {
        muzzleRightX = rightX; muzzleLeftX = leftX;
        standingMuzzleYFactor = standingYFactor; proneMuzzleYFactor = proneYFactor;
    }
    public void setDropTiming(int doubleTapWindowMs, int ignoreMs) {
        dropTapWindowMs = Math.max(80, doubleTapWindowMs);
        dropIgnoreMs = Math.max(120, ignoreMs);
    }

    public void setLaserCooldownMs(int ms) { laserCooldownMs = Math.max(200, ms); }
    public void setLaserDamage(int damage) { laserDamage = Math.max(1, damage); }
    public int getLaserDamage() { return laserDamage; }

    private static void runFx(Runnable r) {
        if (javafx.application.Platform.isFxApplicationThread()) r.run();
        else javafx.application.Platform.runLater(r);
    }

    // -------------------- Constructor --------------------
    public GameCharacter(int id, int x, int y, String imgName,
                         int count, int column, int row, int width, int height,
                         KeyCode leftKey, KeyCode rightKey, KeyCode upKey, KeyCode downKey, MouseButton primary) {
        this.startX = x; this.startY = y; this.x = x; this.y = y;
        this.characterWidth = width; this.characterHeight = height;
        this.leftKey = leftKey; this.rightKey = rightKey; this.upKey = upKey; this.downKey = downKey;

        this.characterImg = new Image(Launcher.class.getResourceAsStream(imgName));
        this.imageView = new AnimatedSprite(characterImg, count, column, row, 0, 0, width, height);

        runFx(() -> {
            imageView.setFitWidth((int) (width * 1.8));
            imageView.setFitHeight((int) (height * 1.8));
            getChildren().add(imageView);
            setScaleX(id % 2 * 2 - 1);
        });

        int frameW = width, frameH = height;
        imageView.define(AnimatedSprite.Action.run,        new AnimatedSprite.ActionSpec(0,  0,  6, SPRITE_SHEET_COLS, frameW, frameH, 80));
        // Use 'proneShoot' action for RUN+SHOOT strip (row 0, cols 6–11)
        imageView.define(AnimatedSprite.Action.proneShoot, new AnimatedSprite.ActionSpec(6,  0,  6, SPRITE_SHEET_COLS, frameW, frameH, 100));
        imageView.define(AnimatedSprite.Action.jump,       new AnimatedSprite.ActionSpec(12, 1,  4, SPRITE_SHEET_COLS, frameW, frameH,100));
        imageView.define(AnimatedSprite.Action.prone,      new AnimatedSprite.ActionSpec(14, 0,  2, SPRITE_SHEET_COLS, frameW, frameH,120));
        imageView.define(AnimatedSprite.Action.idle,       new AnimatedSprite.ActionSpec(0,  0,  1, SPRITE_SHEET_COLS, frameW, frameH,500));
        imageView.define(AnimatedSprite.Action.shoot,      new AnimatedSprite.ActionSpec(SHOOT_START_INDEX, SHOOT_ROW, SHOOT_FRAMES, SPRITE_SHEET_COLS, frameW, frameH, SHOOT_FRAME_MS));
        // ---- ShootUp & ShootDown (single-frame poses) ----
        imageView.define(AnimatedSprite.Action.shootUp,    new AnimatedSprite.ActionSpec(5,  1, 1, SPRITE_SHEET_COLS, frameW, frameH, 80));
        imageView.define(AnimatedSprite.Action.shootDown,  new AnimatedSprite.ActionSpec(11, 1, 1, SPRITE_SHEET_COLS, frameW, frameH, 80));
        // ---- Run+Shoot Up/Down strips (row 1) ----
        imageView.define(AnimatedSprite.Action.runShootDown, new AnimatedSprite.ActionSpec(7,  1, 6, SPRITE_SHEET_COLS, frameW, frameH, 100));
        imageView.define(AnimatedSprite.Action.runShootUp,   new AnimatedSprite.ActionSpec(0,  1, 6, SPRITE_SHEET_COLS, frameW, frameH, 100));

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

    private AnimatedSprite.Action currentGroundAction = null;
    private void setGroundAnim(AnimatedSprite.Action a) {
        if (isInShootPose() || isRunShootActive()) return;

        // lock to prone when S is held
        if (isProne && a != AnimatedSprite.Action.prone) return;

        if (currentGroundAction == a) return;
        currentGroundAction = a;
        runFx(() -> imageView.setAction(a));
    }

    private void stepHorizontal() {
        if (isMoveLeft)  { xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration); x -= xVelocity; }
        if (isMoveRight) { xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration); x += xVelocity; }
        if (!isMoveLeft && !isMoveRight) xVelocity = 0;
        clampToWalls();
    }
    private void stepVertical() {
        if (isFalling)       { yVelocity = Math.min(yMaxVelocity, yVelocity + yAcceleration); y += yVelocity; }
        else if (isJumping)  { yVelocity = Math.max(0, yVelocity - yAcceleration); y -= yVelocity; }
    }
    private void clampToWalls() {
        if (x < 0) x = 0;
        int maxX = GameStage.WIDTH - characterWidth;
        if (x > maxX) x = maxX;
    }


    public void jump() {
        if (canJump) {
            canJump = false;
            isJumping = true;
            isFalling = false;
            isProne = false;

            // vertical boost
            yVelocity = yMaxVelocity;

            // add a small horizontal boost if walking
            if (isMoveLeft) {
                xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration);
                x -= (int) (xVelocity * 0.8);
            } else if (isMoveRight) {
                xVelocity = Math.min(xMaxVelocity, xVelocity + xAcceleration);
                x += (int) (xVelocity * 0.8);
            }

            clampToWalls();

            runFx(() -> imageView.setAction(AnimatedSprite.Action.jump));
            currentGroundAction = AnimatedSprite.Action.jump;
        }
    }

    public void jumpForward(int direction) {
        if (canJump) {
            yVelocity = yMaxVelocity; xVelocity = xMaxVelocity; canJump = false; isJumping = true; isFalling = false; isProne = false;
            x += (int) (direction * xVelocity * 1.2);
            clampToWalls();
            runFx(() -> { setScaleX(direction); imageView.setAction(AnimatedSprite.Action.jump); });
            currentGroundAction = AnimatedSprite.Action.jump;
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
    private boolean isRunShootActive() {
        return runShootHoldPT != null && runShootHoldPT.getStatus() == Animation.Status.RUNNING;
    }

    private void setGroundAnimIfAllowed() {
        if (isInShootPose() || isRunShootActive()) return;

        // if still prone (S held), never override
        if (isProne) {
            setGroundAnim(AnimatedSprite.Action.prone);
            return;
        }

        if (isMoveLeft || isMoveRight) setGroundAnim(AnimatedSprite.Action.run);
        else                           setGroundAnim(AnimatedSprite.Action.idle);
    }
    private void onLandedAtTop(int topY) {
        y = spriteYForColliderBottom(topY);
        yVelocity = 0; isFalling = false; canJump = true;
        setGroundAnimIfAllowed();
    }

    // ---------------- Animation/Frame hook ----------------
    public void beginFrame() {
        prevX = x;
        prevY = y;
        prevColOffY = currentColOffY();
        prevColH    = currentColH();
    }
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
        final int prevBottom = prevY + prevColOffY + prevColH; // last frame collider
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
        currentGroundAction = AnimatedSprite.Action.idle;
    }

    private static void fx(Runnable r) {
        if (javafx.application.Platform.isFxApplicationThread()) r.run();
        else javafx.application.Platform.runLater(r);
    }


    // ---------------- Hitbox & Shooting ----------------
    public Rectangle2D getHitbox() {
        return new Rectangle2D(this.x + colOffX(), this.y + currentColOffY(), currentColW(), currentColH());
    }

    // Respect the chosen shoot action (no overriding); keep recoil & holds
    private void playShootPoseAndRecoil(int facingDir, AnimatedSprite.Action shootAction) {
        fx(() -> imageView.setAction(shootAction));

        boolean isRunStrip = shootAction == AnimatedSprite.Action.proneShoot
                || shootAction == AnimatedSprite.Action.runShootUp
                || shootAction == AnimatedSprite.Action.runShootDown;

        if (isRunStrip) {
            fx(() -> {
                if (runShootHoldPT == null) runShootHoldPT = new PauseTransition();
                runShootHoldPT.stop();
                runShootHoldPT.setDuration(Duration.millis(Math.max(shotCooldownMs + 40, 160)));
                runShootHoldPT.setOnFinished(ev -> setGroundAnimIfAllowed());
                runShootHoldPT.playFromStart();
            });
        } else {
            fx(() -> {
                int hold = Math.max(shootPoseHoldMs, shotCooldownMs + 40);
                if (shootRestorePT != null) shootRestorePT.stop();
                shootRestorePT = new PauseTransition(Duration.millis(hold));
                shootRestorePT.setOnFinished(ev -> setGroundAnimIfAllowed());
                shootRestorePT.playFromStart();
            });
        }

        fx(() -> AudioManager.playSFX("/advpro_game/assets/sfx_shoot.mp3"));

        final int recoil = (isProne ? (int)Math.round((recoilMinPx + (int)(Math.random()*(recoilMaxPx-recoilMinPx+1))) * 0.4)
                : recoilMinPx + (int)(Math.random()*(recoilMaxPx-recoilMinPx+1)));
        fx(() -> {
            x -= facingDir * recoil;
            clampToWalls();
            PauseTransition back = new PauseTransition(Duration.millis(recoilReturnMs));
            back.setOnFinished(ev -> { x += facingDir * recoil; clampToWalls(); });
            back.play();
        });
    }


    private AnimatedSprite.Action chooseShootAnim(Double aimDegOpt) {
        // PRONE: lock to prone pose (horizontal)
        if (isProne) return AnimatedSprite.Action.prone;

        final boolean onGround = !isJumping && !isFalling;
        final boolean running  = onGround && (isMoveLeft || isMoveRight);

        boolean upAim = false, downAim = false;
        if (aimDegOpt != null) {
            upAim   = (aimDegOpt <= -20); // -45
            downAim = (aimDegOpt >=  20); // +45
        }

        if (running) {
            if (upAim)   return AnimatedSprite.Action.runShootUp;
            if (downAim) return AnimatedSprite.Action.runShootDown;
            return AnimatedSprite.Action.proneShoot; // horizontal running strip (row0 col6–11)
        } else {
            if (upAim)   return AnimatedSprite.Action.shootUp;
            if (downAim) return AnimatedSprite.Action.shootDown;
            return AnimatedSprite.Action.shoot;      // standing/jump single-frame
        }
    }

    private double currentMuzzleY() {
        double factor = isProne ? proneMuzzleYFactor : standingMuzzleYFactor;
        return this.y + (this.characterHeight * factor);
    }
    private double currentMuzzleX() {
        int dir = (getScaleX() > 0 ? 1 : -1);
        return this.x + (dir > 0 ? muzzleRightX : muzzleLeftX);
    }

    /** Fire immediately (used by legacy triggers) */
    public Bullet shoot(){
        int facing = getFacingDir();
        double mx = currentMuzzleX();
        double my = currentMuzzleY();

        AnimatedSprite.Action anim = chooseShootAnim(null);
        playShootPoseAndRecoil(facing, anim);

        // Horizontal bullet (legacy)
        Bullet b = new Bullet(mx, my, facing, 0, 480.0, 1, 1.6, false);
        if (bulletSink != null) bulletSink.accept(b);
        return b;
    }

    // Creates a bullet if trigger pressed & cooldown ok. (keyboard or mouse; angle optional)
    public Bullet tryCreateBullet(Keys keys) {
        // trigger (hold to shoot with mouse buttons or SPACE)
        boolean trigger =
                keys.isClicked(MouseButton.PRIMARY) ||
                        keys.isClicked(MouseButton.SECONDARY) ||
                        keys.isPressed(KeyCode.SPACE);
        if (!trigger) return null;

        long now = System.currentTimeMillis();
        if (now - lastShotMs < shotCooldownMs) return null;

        Shot s = computeShot(keys);      // fallback 8-way keyboard aim
        lastShotMs = now;

        int facing = getFacingDir();
        AnimatedSprite.Action anim = chooseShootAnim(null);
        playShootPoseAndRecoil(facing, anim);

        Bullet bullet = new Bullet(s.x, s.y, s.dx, s.dy, 480.0, 1, 1.6, false);
        if (bulletSink != null) bulletSink.accept(bullet);
        return bullet;
    }

    // Angle-aware version used by GameLoop. aimDeg is snapped to {-45, 0, +45}.
    public Bullet tryCreateBullet(Keys keys, Double aimDegOpt) {
        // trigger (hold to shoot with mouse buttons or SPACE)␊
        boolean trigger =
        keys.isClicked(MouseButton.PRIMARY) ||
        keys.isClicked(MouseButton.SECONDARY) ||
                        keys.isPressed(KeyCode.SPACE);
        if (!trigger) return null;

        long now = System.currentTimeMillis();
        if (now - lastShotMs < shotCooldownMs) return null;

        double mx = currentMuzzleX();
        double my = currentMuzzleY();
        double dirX, dirY;

        if (aimDegOpt != null) {
            // Use snapped mouse angle, mirrored by facing; screen Y+ is down, so sin(rad) is correct
            int facing = getFacingDir(); // +1 right, -1 left
            double rad = Math.toRadians(aimDegOpt);
            dirX = Math.cos(rad) * facing;
            dirY = Math.sin(rad);
            double len = Math.hypot(dirX, dirY);
            if (len == 0) { dirX = facing; dirY = 0; } else { dirX /= len; dirY /= len; }
        } else {
            // Fallback to keyboard 8-way aim
            Shot s = computeShot(keys);
            mx = s.x; my = s.y; dirX = s.dx; dirY = s.dy;
        }

        lastShotMs = now;

        // PRONE lock: force horizontal only
        if (isProne) { dirY = 0; dirX = (getFacingDir() > 0 ? 1 : -1); }

        int facing = getFacingDir();
        AnimatedSprite.Action anim = chooseShootAnim(aimDegOpt);
        playShootPoseAndRecoil(facing, anim);

        Bullet bullet = new Bullet(mx, my, dirX, dirY, 480.0, 1, 1.6, false);
        if (bulletSink != null) bulletSink.accept(bullet);
        return bullet;
    }

    public Shot tryCreateLaser(Keys keys, Double aimDegOpt) {
        if (keys == null) return null;

        long now = System.currentTimeMillis();
        if (now - lastLaserMs < laserCooldownMs) return null;

        lastLaserMs = now;

        double mx = currentMuzzleX();
        double my = currentMuzzleY();
        double dirX;
        double dirY;

        if (aimDegOpt != null) {
            int facing = getFacingDir();
            double rad = Math.toRadians(aimDegOpt);
            dirX = Math.cos(rad) * facing;
            dirY = Math.sin(rad);
        } else {
            Shot s = computeShot(keys);
            mx = s.x;
            my = s.y;
            dirX = s.dx;
            dirY = s.dy;
        }

        if (isProne) {
            dirY = 0;
            dirX = (getFacingDir() > 0 ? 1 : -1);
        }

        double len = Math.hypot(dirX, dirY);
        int facing = getFacingDir();
        if (len == 0) {
            dirX = facing;
            dirY = 0;
        } else {
            dirX /= len;
            dirY /= len;
        }

        AnimatedSprite.Action anim = chooseShootAnim(aimDegOpt);
        playShootPoseAndRecoil(facing, anim);

        return new Shot(mx, my, dirX, dirY);
    }

    // Keep a primitive overload for existing call sites; delegate to the main version.
    public Bullet tryCreateBullet(Keys keys, double aimDeg) {
        return tryCreateBullet(keys, Double.valueOf(aimDeg));
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

    // === 8-way aim computation (used by tryCreateBullet fallback) ===
    public static class Shot {
        public final double x, y, dx, dy;
        public Shot(double x, double y, double dx, double dy) { this.x=x; this.y=y; this.dx=dx; this.dy=dy; }
    }
    public Shot computeShot(Keys keys) {
        boolean up = keys.isPressed(getUpKey());
        boolean left = keys.isPressed(getLeftKey());
        boolean right = keys.isPressed(getRightKey());
        boolean down = keys.isPressed(getDownKey());

        // When PRONE (or down-without-move), force horizontal shot at lower muzzle
        boolean proneShootLogic = isProne || (down && !left && !right);

        double dx = 0, dy = 0;
        if (proneShootLogic) {
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

    //-----------helper------------
    public int getFacingDir() { return getScaleX() >= 0 ? 1 : -1; }

    // Selects the proper shoot animation for a snapped angle (-45/0/+45),
    // honoring the "prone = horizontal only" rule.
    private void setShootAnimForAngle(double aimDeg) {
        if (isRunShootActive()) return;

        if (isProne) {
            setGroundAnim(AnimatedSprite.Action.prone);
            return;
        }

        final boolean onGround = !isJumping && !isFalling;
        final boolean running  = onGround && (isMoveLeft || isMoveRight);
        final boolean upAim    = (aimDeg <= -20);
        final boolean downAim  = (aimDeg >=  20);

        AnimatedSprite.Action a;
        if (running) {
            a = upAim   ? AnimatedSprite.Action.runShootUp
                    : downAim ? AnimatedSprite.Action.runShootDown
                    :           AnimatedSprite.Action.proneShoot; // horizontal run+shoot strip
        } else {
            a = upAim   ? AnimatedSprite.Action.shootUp
                    : downAim ? AnimatedSprite.Action.shootDown
                    :           AnimatedSprite.Action.shoot;
        }
        runFx(() -> imageView.setAction(a));
    }
}
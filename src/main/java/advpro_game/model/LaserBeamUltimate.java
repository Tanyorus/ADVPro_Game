package advpro_game.model;

import advpro_game.Launcher;
import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class LaserBeamUltimate {
    private static final String SPRITE_PATH = "/advpro_game/assets/laser_beam.png";

    // === SHEET LAYOUT (fixed to your spec) ===
    private static final int SHEET_COLS = 3;
    private static final int SHEET_ROWS = 6;
    private static final int FRAME_W = 360;   // px
    private static final int FRAME_H = 180;   // px
    private static final int TOTAL_FRAMES = 18;

    // === ALIGNMENT / SIZE KNOBS ===
    private static final double OFFSET_X = 0;      // +forward, -back from muzzle
    private static final double OFFSET_Y = 40;    // +down, -up from muzzle baseline
    private static final double SCALE_X  = 1.00;   // beam length scale
    private static final double SCALE_Y  = 0.70;   // beam thickness scale  ← change THIS for height

    // keep vertical position stable even when height changes
    private enum Anchor { TOP, CENTER, BOTTOM }
    private static final Anchor ANCHOR_MODE = Anchor.BOTTOM;

    // Optional: expand viewport a bit to avoid cropping the head flare
    private static final int PAD_LEFT = 0, PAD_RIGHT = 0, PAD_TOP = -30, PAD_BOTTOM = 0;

    // animation timing
    private static final double FPS = 30.0;
    private static final long NANOS_PER_FRAME = (long)(1_000_000_000.0 / FPS);

    private Image sheet;
    private ImageView view;
    private Rectangle fallback;
    private AnimationTimer timer;
    private int frameIndex = 0;
    private long lastFrameTime = 0L;

    public LaserBeamUltimate(double muzzleX, double muzzleY, int dir, double scale) {
        try (var s = Launcher.class.getResourceAsStream(SPRITE_PATH)) {
            if (s != null) sheet = new Image(s);
        } catch (Throwable t) {
            System.err.println("[ULT] load failed: " + t);
        }

        if (sheet != null && sheet.getWidth() > 0 && sheet.getHeight() > 0) {
            view = new ImageView(sheet);
            view.setViewport(frameToViewport(0));
            view.setPreserveRatio(false);
            view.setSmooth(false);

            double sx = Math.abs(scale) * SCALE_X;
            double sy = Math.abs(scale) * SCALE_Y;
            view.setScaleX(dir >= 0 ? sx : -sx);
            view.setScaleY(sy);

            double wScaled = (FRAME_W + PAD_LEFT + PAD_RIGHT) * Math.abs(view.getScaleX());
            double hScaled = (FRAME_H + PAD_TOP + PAD_BOTTOM) * Math.abs(view.getScaleY());

            double x = (dir >= 0) ? (muzzleX + OFFSET_X) : (muzzleX - wScaled - OFFSET_X);
            double y = switch (ANCHOR_MODE) {
                case TOP    -> muzzleY + OFFSET_Y;
                case CENTER -> muzzleY - hScaled / 2.0 + OFFSET_Y;
                case BOTTOM -> muzzleY - hScaled + OFFSET_Y;
            };

            view.setTranslateX(x);
            view.setTranslateY(y);
            view.setMouseTransparent(true);
        } else {
            // visible fallback line
            double len = 640, th = 18;
            double x = (dir >= 0) ? muzzleX : (muzzleX - len);
            double y = muzzleY - th / 2.0;
            fallback = new Rectangle(x, y, len, th);
            fallback.setFill(Color.web("#C07CFF"));
            fallback.setOpacity(0.95);
            fallback.setMouseTransparent(true);
        }
    }

    public void playOnce(Pane layer) {
        if (view != null) {
            layer.getChildren().add(view);
            timer = new AnimationTimer() {
                @Override public void handle(long now) {
                    if (lastFrameTime == 0L) lastFrameTime = now;
                    if (now - lastFrameTime >= NANOS_PER_FRAME) {
                        lastFrameTime = now;
                        frameIndex++;
                        if (frameIndex >= TOTAL_FRAMES) {
                            stop();
                            layer.getChildren().remove(view);
                            return;
                        }
                        view.setViewport(frameToViewport(frameIndex));
                    }
                }
            };
            timer.start();
        } else {
            layer.getChildren().add(fallback);
            var fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(140), fallback);
            fade.setFromValue(0.95);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> layer.getChildren().remove(fallback));
            fade.playFromStart();
        }
    }

    public Node getNode() { return (view != null) ? view : fallback; }

    private Rectangle2D frameToViewport(int idx) {
        int col = idx % SHEET_COLS;   // 0..2
        int row = idx / SHEET_COLS;   // 0..5

        double x = col * FRAME_W;
        double y = row * FRAME_H;
        double w = FRAME_W;
        double h = FRAME_H;

        // padding (if needed to avoid cropping)
        x -= PAD_LEFT;   w += PAD_LEFT + PAD_RIGHT;
        y -= PAD_TOP;    h += PAD_TOP  + PAD_BOTTOM;

        // clamp to sheet bounds
        double maxW = sheet.getWidth();
        double maxH = sheet.getHeight();
        if (x < 0) { w += x; x = 0; }
        if (y < 0) { h += y; y = 0; }
        if (x + w > maxW) w = maxW - x;
        if (y + h > maxH) h = maxH - y;

        return new Rectangle2D(x, y, Math.max(1, w), Math.max(1, h));
    }
}

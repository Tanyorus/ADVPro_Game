package advpro_game.model;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.EnumMap;
import java.util.Objects;

/**
 * AnimatedSprite (FX-safe)
 * - Extends ImageView.
 * - Per-action sprite regions & timing via ActionSpec.
 * - All mutations happen on the JavaFX thread.
 * - dt is milliseconds for update(...).
 * - Supports time scaling (slow-mo): setTimeScale(0.5) to run at half speed.
 */
public class AnimatedSprite extends ImageView {

    // Actions used across your codebase
    public enum Action {
        idle, run, jump, prone,
        shoot, shootUp, shootDown,
        proneShoot,          // run+shoot horizontal (row0 col6–11 in your sheet)
        runShootUp, runShootDown,
        javaShoot            // optional/custom; safe to keep
    }



    /** Per-action sprite definition. */
    public static class ActionSpec {
        public final int startCol;   // starting column (0-based)
        public final int startRow;   // starting row (0-based)
        public final int frames;     // frames in this action
        public final int columns;    // columns across for this action (wrap onto rows)
        public final int frameW;     // width of each frame (px)
        public final int frameH;     // height of each frame (px)
        public final int delayMs;    // delay between frames (ms)

        public ActionSpec(int startCol, int startRow, int frames, int columns,
                          int frameW, int frameH, int delayMs) {
            this.startCol = Math.max(0, startCol);
            this.startRow = Math.max(0, startRow);
            this.frames  = Math.max(1, frames);
            this.columns = Math.max(1, columns);
            this.frameW  = Math.max(1, frameW);
            this.frameH  = Math.max(1, frameH);
            this.delayMs = Math.max(1, delayMs);
        }
    }

    // --- State ---
    private final Image sheet;
    private final EnumMap<Action, ActionSpec> specs = new EnumMap<>(Action.class);

    private Action currentAction = Action.idle;
    private ActionSpec spec;        // current action spec (never null after ctor)
    private int frame = 0;          // frame index within current spec
    private double accMs = 0;       // accumulator (ms) for update()

    // Optional: original sheet meta (kept for compatibility)
    private final int count, columns, rows, offsetX, offsetY, width, height;

    // Time scaling (1.0 = normal, <1 faster, >1 slower)
    private double timeScale = 1.0;

    // --------------- FX helpers ---------------
    private static boolean onFx() {
        return javafx.application.Platform.isFxApplicationThread();
    }
    private static void fx(Runnable r) {
        if (r == null) return;
        if (onFx()) r.run(); else javafx.application.Platform.runLater(r);
    }

    /**
     * @param image   sprite sheet
     * @param count   total frames available in the sheet region
     * @param columns columns across in sheet region
     * @param rows    rows in sheet region
     * @param offsetX pixel offset X in the sheet for the first frame
     * @param offsetY pixel offset Y in the sheet for the first frame
     * @param width   per-frame width (px)
     * @param height  per-frame height (px)
     */
    public AnimatedSprite(Image image, int count, int columns, int rows,
                          int offsetX, int offsetY, int width, int height) {
        this.sheet   = Objects.requireNonNull(image, "image");
        this.count   = count;
        this.columns = columns;
        this.rows    = rows;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width   = width;
        this.height  = height;

        setSmooth(false);
        setPreserveRatio(false);
        fx(() -> setImage(image));

        // Derive starting grid cell from pixel offsets (within bounds)
        final int safeW = Math.max(1, width);
        final int safeH = Math.max(1, height);
        int startCol = Math.max(0, offsetX / safeW);
        int startRow = Math.max(0, offsetY / safeH);

        // Default IDLE mapping
        define(Action.idle, new ActionSpec(
                startCol, startRow,
                /* frames */ Math.min(count, Math.max(1, columns * rows)),
                /* columns */ Math.max(1, columns),
                width, height,
                /* delayMs */ 120
        ));

        // Optional default; can be overridden later
        define(Action.javaShoot, new ActionSpec(
                startCol, startRow,
                Math.min(count, Math.max(1, columns * rows)),
                Math.max(1, columns),
                width, height,
                100
        ));

        // Initialize viewport
        applyAction(Action.idle);
    }

    // ---------------- Public API ----------------

    /** Add/override an action's mapping. */
    public void define(Action action, ActionSpec s) {
        specs.put(action, s);
        if (action == currentAction) {
            // If redefining current action, apply immediately
            spec = s;
            frame = 0;
            accMs = 0;
            applyViewport(0, 0);
        }
    }

    /** Backward-compat alias (old code used defne). */
    public void defne(Action action, ActionSpec s) { define(action, s); }

    /** Switch to a mapped action; falls back to IDLE if not defined. */
    public void setAction(Action action) {
        fx(() -> applyAction(action));
    }

    /** Force re-applying the same action (useful for "pose nudge"). */
    public void setActionForce(Action action) {
        fx(() -> applyActionForce(action));
    }

    /** Advance time in milliseconds; will tick frames when delay is exceeded. */
    public void update(double dtMs) {
        if (spec == null) return;

        // Single-frame actions need no ticking
        if (spec.frames <= 1) return;

        double scaled = dtMs * Math.max(0.0001, timeScale);
        accMs += scaled;

        while (accMs >= spec.delayMs) {
            accMs -= spec.delayMs;
            // Tick must mutate viewport on FX
            fx(this::tickOnceFx);
        }
    }

    /** Change the global time scale (1.0 = normal; >1.0 slower; <1.0 faster). */
    public void setTimeScale(double scale) {
        this.timeScale = Double.isFinite(scale) ? Math.max(0.0001, scale) : 1.0;
    }

    /** Advance one frame with wrapping (FX thread only). */
    private void tickOnceFx() {
        if (spec == null) return;

        // compute local indices within spec grid
        int localCol = frame % spec.columns;
        int localRow = frame / spec.columns;

        applyViewport(localCol, localRow);

        // advance (wrap)
        frame = (frame + 1) % spec.frames;
    }

    // --------------- Getters ---------------

    public Action getCurrentAction() { return currentAction; }
    public int getCurrentFrame()     { return frame; }

    // --------------- Optional helpers ---------------

    /** Quickly change the per-frame delay (ms) for a given action, if defined. */
    public void setActionDelayMs(Action action, int delayMs) {
        ActionSpec s = specs.get(action);
        if (s == null) return;
        define(action, new ActionSpec(s.startCol, s.startRow, s.frames, s.columns, s.frameW, s.frameH, Math.max(1, delayMs)));
    }

    /** Scale the delay for an action (e.g., slow-mo). factor > 1 = slower, < 1 = faster. */
    public void scaleActionDelay(Action action, double factor) {
        ActionSpec s = specs.get(action);
        if (s == null) return;
        int newDelay = (int) Math.max(1, Math.round(s.delayMs * factor));
        define(action, new ActionSpec(s.startCol, s.startRow, s.frames, s.columns, s.frameW, s.frameH, newDelay));
    }

    // ---------------- Internals ----------------

    private void applyAction(Action action) {
        if (action == currentAction && spec != null) return;
        ActionSpec s = specs.get(action);
        if (s == null) {
            action = Action.idle;
            s = specs.get(Action.idle);
            if (s == null) {
                // Last resort idle
                s = new ActionSpec(0, 0, 1, 1, Math.max(1, width), Math.max(1, height), 120);
                specs.put(Action.idle, s);
            }
        }
        currentAction = action;
        spec = s;
        frame = 0;
        accMs = 0;
        applyViewport(0, 0);
    }

    private void applyActionForce(Action action) {
        ActionSpec s = specs.get(action);
        if (s == null) {
            action = Action.idle;
            s = specs.get(Action.idle);
            if (s == null) s = new ActionSpec(0, 0, 1, 1, Math.max(1, width), Math.max(1, height), 120);
        }
        currentAction = action;
        spec = s;
        frame = 0;
        accMs = 0;
        applyViewport(0, 0);
    }

    /** Applies viewport using current spec and local (col,row) indices (FX thread only). */
    private void applyViewport(int localCol, int localRow) {
        if (spec == null) return;

        int col = spec.startCol + Math.max(0, localCol);
        int row = spec.startRow + Math.max(0, localRow);

        // Guard: ensure viewport stays within the sheet bounds
        double sheetW = (sheet != null ? sheet.getWidth() : spec.frameW);
        double sheetH = (sheet != null ? sheet.getHeight() : spec.frameH);

        // Clamp column/row if they overshoot the sheet (prevents disappearing)
        int maxCol = (int)Math.max(0, (sheetW / Math.max(1, spec.frameW)) - 1);
        int maxRow = (int)Math.max(0, (sheetH / Math.max(1, spec.frameH)) - 1);
        col = Math.min(col, maxCol);
        row = Math.min(row, maxRow);

        int pxX = Math.max(0, col * spec.frameW);
        int pxY = Math.max(0, row * spec.frameH);

        // Extra clamps for safety
        if (pxX + spec.frameW > sheetW) pxX = Math.max(0, (int)sheetW - spec.frameW);
        if (pxY + spec.frameH > sheetH) pxY = Math.max(0, (int)sheetH - spec.frameH);

        setViewport(new Rectangle2D(pxX, pxY, spec.frameW, spec.frameH));
    }
}

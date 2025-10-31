package advpro_game.model;

import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.EnumSet;

public class Keys {
    // Current + previous frame states
    private final EnumSet<KeyCode>     keysDown      = EnumSet.noneOf(KeyCode.class);
    private final EnumSet<KeyCode>     keysDownPrev  = EnumSet.noneOf(KeyCode.class);
    private final EnumSet<MouseButton> mouseDown     = EnumSet.noneOf(MouseButton.class);
    private final EnumSet<MouseButton> mouseDownPrev = EnumSet.noneOf(MouseButton.class);

    /** Call ONCE per frame before you read justPressed/justReleased. */
    public void beginFrame() {
        //keysDownPrev.clear();     keysDownPrev.addAll(keysDown);
        //mouseDownPrev.clear();    mouseDownPrev.addAll(mouseDown);
    }

    // ---------- Keyboard ----------
    public void add(KeyCode key)        { if (key != null) keysDown.add(key); }
    public void remove(KeyCode key)     { if (key != null) keysDown.remove(key); }
    public boolean isPressed(KeyCode k) { return k != null && keysDown.contains(k); }

    /** Rising edge this frame (was up, now down). Requires beginFrame() each frame. */
    public boolean justPressed(KeyCode k)  { return k != null &&  keysDown.contains(k) && !keysDownPrev.contains(k); }
    /** Falling edge this frame (was down, now up). Requires beginFrame() each frame. */
    public boolean justReleased(KeyCode k) { return k != null && !keysDown.contains(k) &&  keysDownPrev.contains(k); }

    /** True if any of the provided keys are currently pressed. */
    public boolean anyPressed(KeyCode... keys) {
        if (keys == null) return false;
        for (KeyCode k : keys) if (isPressed(k)) return true;
        return false;
    }

    /**
     * Returns -1, 0, or +1 based on two keys (e.g., A/D or Left/Right).
     * If both held, returns 0.
     */
    public int getAxis(KeyCode negative, KeyCode positive) {
        boolean neg = isPressed(negative);
        boolean pos = isPressed(positive);
        if (neg == pos) return 0;
        return pos ? +1 : -1;
    }

    // ---------- Mouse ----------
    public void add(MouseButton b)           { if (b != null) mouseDown.add(b); }
    public void remove(MouseButton b)        { if (b != null) mouseDown.remove(b); }

    /** For backward compatibility: this behaves like “is button currently down”. */
    public boolean isClicked(MouseButton b)  { return b != null && mouseDown.contains(b); }

    /** Rising edge this frame. Requires beginFrame() each frame. */
    public boolean justClicked(MouseButton b)   { return b != null &&  mouseDown.contains(b) && !mouseDownPrev.contains(b); }
    /** Falling edge this frame. Requires beginFrame() each frame. */
    public boolean justReleased(MouseButton b)  { return b != null && !mouseDown.contains(b) &&  mouseDownPrev.contains(b); }

    /** Clears all states (useful on scene switches). */
    public void clear() {
        keysDown.clear();
        keysDownPrev.clear();
        mouseDown.clear();
        mouseDownPrev.clear();
    }

}

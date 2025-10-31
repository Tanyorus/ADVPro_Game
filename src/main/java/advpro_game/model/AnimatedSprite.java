package advpro_game.model;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.EnumMap;

public class AnimatedSprite extends ImageView {

    public enum Action {idle, run, jump, prone, shoot, javaShoot}
    /*Per-action mapping*/
    private long lastFramePrint = 0;
    private int lastFrameIndex = -1;
    public static class ActionSpec{
        public final int startCol; //first column
        public final int startRow; //first Row
        public final int frames;
        public final int columns;
        public final int frameW, frameH;
        public final int delayMs;

        public ActionSpec(int startCol, int startRow , int frames, int columns,
                          int frameW, int frameH, int delayMs) {
            this.startCol = startCol;
            this.startRow = startRow;
            this.frames = frames;
            this.columns = columns;
            this.frameW = frameW;
            this.frameH = frameH;
            this.delayMs = delayMs;
        }
    }
    private final Image sheet;
    private final EnumMap<Action, ActionSpec> specs = new EnumMap<>(Action.class);

    private Action currentAction = Action.idle;
    private ActionSpec spec;            //curren spec
    private int frame = 0;              //current frame index within spec
    private double accMs = 0;           //time accumulator (ms)

    private int delayMs = 100;
    private double acc =0;
    int count, columns, rows, offsetX, offsetY, width, height, curIndex, curColumnIndex = 0, curRowIndex = 0;
    public AnimatedSprite(Image image, int count, int columns, int rows, int offsetX, int offsetY, int width, int height) {
        this.sheet  = image;
        setSmooth(false);
        this.setImage(image);
        this.count = count;
        this.columns = columns;
        this.rows = rows;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;

        //Define  a default IDLE action mapped to those params
        int startCol = offsetX / width;
        int startRow = offsetY / height;

        define(Action.idle, new ActionSpec(
                startCol, startRow,
                /*frames*/ Math.min(count, columns * rows),
                /*columns*/ columns,
                width, height,
                /*delayMs*/ 120
        ));
        setAction(Action.idle); // set initial viewport

        define(Action.javaShoot, new ActionSpec(
                startCol,  // startCol
                startRow,  // startRow
                Math.min(count, columns * rows),  // frames
                startCol,  // columns
                width, // width
                height, // height
                100  // delayMs (ความล่าช้าในการเปลี่ยนเฟรม)
        ));
    }
    public void defne(Action action ,ActionSpec s){
        specs.put(action,s);
        if(action == currentAction){
            spec = s;
            frame = 0;
            accMs = 0;
            setViewport(new Rectangle2D(s.startCol* s.frameW,s.startRow* s.frameH,s.frameH,s.frameH));

        }
    }
    public void setAction(Action action){
        if (action == currentAction) return;

        currentAction = action;
        spec = specs.get(action);

        if (spec == null){
            //Use idle when not define
            spec = specs.get(Action.idle);
            currentAction = Action.idle;
        }
        frame = 0;
        accMs = 0;
        setViewport( new Rectangle2D(
                spec.startCol * spec.frameW,
                spec.startRow * spec.frameH,
                spec.frameW,
                spec.frameH
        ));

        System.out.println("[DEBUG] Action set to " + currentAction);
    }

    //Add/override an action's mapping
    public void define(Action action, ActionSpec s){
        specs.put(action,s);
        if(action == currentAction){
            spec = s;
            frame = 0;
            accMs = 0;
            setViewport(new Rectangle2D(s.startCol* s.frameW,s.startRow* s.frameH,s.frameW,s.frameH));

        }
    }

    public void update(double dtMs) {
        accMs += dtMs;
        while( accMs >= spec.delayMs ) {
            tick();
            accMs -= spec.delayMs;
            debugFramestatus();
        }
    }

    public void tick() {
        if (spec == null) return;

        //Compute current (col,row) Before in
        int localCol = frame % spec.columns;
        int localRow = frame / spec.columns;

        int col = spec.startCol + localCol;
        int row = spec.startRow + localRow;

        setViewport(new Rectangle2D(
                col * spec.frameW,
                row * spec.frameH,
                spec.frameW, spec.frameH
        ));

        // Advance and wrap linearly by frames
            frame = (frame + 1) % spec.frames;

    }

    //Getters query the state
    public Action getCurrentAction() { return currentAction; }
    public int getCurrentFrame() { return frame; }

    public void debugFramestatus(){
        if (frame != lastFrameIndex) {
            long now = System.currentTimeMillis();
           // System.out.println("[ANIM DEBUG] Frame changed → " + frame +
           //         " (action=" + currentAction + ", time=" + now + ")");
            lastFrameIndex = frame;
            lastFramePrint = now;
        } else {
            long now = System.currentTimeMillis();
            if (now - lastFramePrint > 1000) { // check every second
                //System.out.println("[ANIM DEBUG] No frame change for >1s (still " + frame + ")");
                lastFramePrint = now;
            }
        }
    }
}

package advpro_game.view;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Simple on-screen score display with built-in tracking, increment, and reset.
 */
public class Score extends Pane {

    private int score = 0;
    private final Label point;

    public Score(int x, int y) {
        point = new Label("0");
        setTranslateX(x);
        setTranslateY(y);
        point.setFont(Font.font("Bit", FontWeight.BOLD, 30));
        point.setTextFill(Color.web("#FFFFFF"));
        getChildren().add(point);
    }

    /** Set score explicitly (for loading/sync) */
    public void setPoint(int score) {
        this.score = Math.max(0, score);
        this.point.setText(Integer.toString(this.score));
    }

    /** Increment the score by given delta */
    public void add(int delta) {
        this.score = Math.max(0, this.score + delta);
        this.point.setText(Integer.toString(this.score));
    }

    /** Get current score */
    public int getScore() {
        return score;
    }

    /** Reset to 0 and update label */
    public void reset() {
        this.score = 0;
        this.point.setText("0");
    }
}

package advpro_game.model;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class Explosion {
    private final Circle node;
    private final Timeline anim;

    public Explosion(double x, double y) {
        node = new Circle(4, Color.ORANGE);
        node.setTranslateX(x);
        node.setTranslateY(y);
        node.setOpacity(0.9);

        anim = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(node.radiusProperty(), 4),
                        new KeyValue(node.opacityProperty(), 0.9),
                        new KeyValue(node.fillProperty(), Color.ORANGE)
                ),
                new KeyFrame(Duration.millis(120),
                        new KeyValue(node.radiusProperty(), 10),
                        new KeyValue(node.fillProperty(), Color.GOLD)
                ),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(node.radiusProperty(), 2),
                        new KeyValue(node.opacityProperty(), 0.0)
                )
        );
        anim.setCycleCount(1);
    }

    public Node getNode() { return node; }
    public void play(Runnable onFinished) {
        anim.setOnFinished(e -> onFinished.run());
        anim.playFromStart();
    }
}

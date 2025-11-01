package advpro_game.util;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

public final class Ui {
    private Ui() {}

    public static void runFx(Runnable r) {
        if (javafx.application.Platform.isFxApplicationThread()) r.run();
        else javafx.application.Platform.runLater(r);
    }

    public static void safeAdd(Pane parent, Node n) {
        if (parent == null || n == null) return;
        runFx(() -> {
            if (!parent.getChildren().contains(n)) parent.getChildren().add(n);
        });
    }

    public static void safeRemove(Pane parent, Node n) {
        if (parent == null || n == null) return;
        runFx(() -> {
            if (n.getParent() == parent) parent.getChildren().remove(n);
        });
    }
}

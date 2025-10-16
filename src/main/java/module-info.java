module se233.advpro_game {
    requires javafx.controls;
    requires javafx.fxml;


    opens se233.advpro_game to javafx.fxml;
    exports se233.advpro_game;
}
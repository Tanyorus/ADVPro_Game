module advpro_Game {
    requires javafx.controls;
    requires javafx.fxml;

    opens advpro_game to javafx.fxml;
    exports advpro_game;
}
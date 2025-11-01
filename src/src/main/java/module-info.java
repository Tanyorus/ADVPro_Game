module avdpro_game {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.logging;
    requires javafx.media;

    opens advpro_game to javafx.fxml;
    exports advpro_game;
}
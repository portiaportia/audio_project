module com.music {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.net.http;
    requires java.desktop;
    requires com.google.gson;

    exports com.music;
    exports com.music.model;
    exports com.music.music_service;
    opens com.music.controller to javafx.fxml;
}

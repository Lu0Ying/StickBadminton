module org.example.stickbadminton {
    requires javafx.controls;
    requires javafx.fxml;

    requires com.almasb.fxgl.all;
    requires com.almasb.fxgl.core;
    requires annotations;
    requires javafx.media;
    requires javafx.graphics;
    requires javafx.base;
    requires java.desktop;

    opens org.stickbadminton to javafx.fxml;
    exports org.stickbadminton;
    exports org.stickbadminton.gamecomponent;
    opens org.stickbadminton.gamecomponent to javafx.fxml;
}
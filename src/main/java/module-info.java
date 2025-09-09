module org.example.stickbadminton {
    requires javafx.controls;
    requires javafx.fxml;

    requires com.almasb.fxgl.all;
    requires com.almasb.fxgl.core;
    requires javafx.base;
    requires annotations;
    requires javafx.graphics;

    opens org.stickbadminton to javafx.fxml;
    exports org.stickbadminton;
    exports org.stickbadminton.gamecomponent;
    opens org.stickbadminton.gamecomponent to javafx.fxml;
}
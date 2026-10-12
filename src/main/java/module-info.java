module org.example.stickbadminton {
    requires javafx.controls;

    requires com.almasb.fxgl.all;
    requires com.almasb.fxgl.core;
    requires annotations;
    requires javafx.media;
    requires javafx.graphics;
    requires javafx.base;
    requires java.desktop;
    requires com.almasb.fxgl.entity;
    requires java.net.http;

    exports org.stickbadminton;
    exports org.stickbadminton.gamecomponent;
}
module de.lebo.keyboard_bridge {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.sun.jna; //java native access
    requires com.sun.jna.platform;


    opens de.lebo.keyboard_bridge to javafx.fxml;
    exports de.lebo.keyboard_bridge;
}
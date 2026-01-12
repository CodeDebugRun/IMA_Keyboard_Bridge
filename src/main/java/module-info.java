module de.lebo.keyboard_bridge {
    requires javafx.controls;
    requires javafx.fxml;


    opens de.lebo.keyboard_bridge to javafx.fxml;
    exports de.lebo.keyboard_bridge;
}
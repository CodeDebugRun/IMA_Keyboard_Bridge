package de.lebo.keyboard_bridge;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class ImaKeyboardBridgeApp extends Application {

    private TextField barcodeInput;
    private TextArea logArea;
    private Label statusLabel;

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 4000;

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        //Status
        statusLabel = new Label("Bereit");
        statusLabel.setStyle("-fx-font-weight: bold;");

        //Barcode Area
        barcodeInput = new TextField();
        barcodeInput.setPromptText("Barcode scannen ...");
        barcodeInput.setStyle("-fx-font-size: 18px;");
        barcodeInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                sendBarcode();
            }
        });

        // Log
        logArea = new TextArea();
        logArea.setEditable(false);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        root.getChildren().addAll(
                new Label("IMA Keyboard Bridge"),
                statusLabel,
                barcodeInput,
                logArea
        );

        Scene scene = new Scene(root, 400, 300);
        stage.setTitle("IMA Keyboard Bridge");
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.show();

        barcodeInput.requestFocus();
    }

    private void sendBarcode() {
        String barcode = barcodeInput.getText().trim();
        if (barcode.isEmpty()) return;

        log("Sending: " + barcode);
        barcodeInput.clear();

        new Thread(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(barcode);
                String response = in.readLine();

                Platform.runLater(() -> {
                    log("Response: " + response);
                    statusLabel.setText("OK");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    log("Error: " + e.getMessage());
                    statusLabel.setText("Verbindungsfehler");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                });
            }
        }).start();
    }

    private void log(String msg) {
        logArea.appendText(msg + "\n");
    }

    public static void main(String[] args) {
        launch();
    }
}

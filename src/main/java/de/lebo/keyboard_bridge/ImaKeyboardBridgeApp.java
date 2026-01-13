package de.lebo.keyboard_bridge;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * IMA Keyboard Bridge Application.
 * Captures barcode scanner input via global keyboard hook and sends to IMA Print API.
 * Also provides manual Auftrag/Position ZPL generation.
 */

public class ImaKeyboardBridgeApp extends Application {

    //UI Components
    private TextArea logArea;
    private Label statusLabel;
    private Label lastBarcodeLabel;
    private TextField auftragField;
    private TextField positionField;
    private TextField startBarcodeField;
    private TextField endBarcodeField;

    //Global keyboard hook
    private GlobalKeyboardHook keyboardHook;

    //Configuration (loaded from config.properties)
    private final AppConfig config = AppConfig.getInstance();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public void start (Stage stage) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f5f5f5;");

        //Title
        Label titleLabel = new Label("IMA Tastaturbrücke");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        //Status Bar
        HBox statusBar = new HBox(10);
        statusLabel = new Label("Aktiv");
        statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        statusBar.getChildren().addAll(new Label("Status:"), statusLabel);

        //Barcode Scanner Section
        VBox barcodeSection = createSection("Barcodeleser (Globaler Hook)");
        lastBarcodeLabel = new Label("Letzter Barcode: -");
        lastBarcodeLabel.setStyle("-fx-font-size: 14px;");
        Label infoLabel = new Label("Scannen Sie mit dem Barcodeleser - kein Fokus erforderlich");
        infoLabel.setStyle("-fx-text-fill: #666;");
        ((VBox) barcodeSection).getChildren().addAll(lastBarcodeLabel, infoLabel);

        // Auftrag/Position Section
        VBox auftragSection = createSection("Auftrag / Position");

        HBox auftragRow = new HBox(10);
        auftragRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        auftragField = new TextField();
        auftragField.setPromptText("z.B. M0001444");
        auftragField.setPrefWidth(150);
        auftragRow.getChildren().addAll(new Label("Auftrag Nr:"), auftragField);

        HBox positionRow = new HBox(10);
        positionRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        positionField = new TextField();
        positionField.setPromptText("(optional)");
        positionField.setPrefWidth(150);
        positionRow.getChildren().addAll(new Label("Position:    "), positionField);

        HBox auftragButtonRow = new HBox(10);
        Button btnAuftragCsv = new Button("CSV Export");
        Button btnAuftragJson = new Button("JSON Export");
        btnAuftragCsv.setOnAction(e -> exportAuftrag("csv"));
        btnAuftragJson.setOnAction(e -> exportAuftrag("json"));
        auftragButtonRow.getChildren().addAll(btnAuftragCsv, btnAuftragJson);

        ((VBox) auftragSection).getChildren().addAll(auftragRow, positionRow, auftragButtonRow);

        // Barcode Range Section
        VBox rangeSection = createSection("Barcode Bereich");

        HBox startRow = new HBox(10);
        startRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        startBarcodeField = new TextField();
        startBarcodeField.setPromptText("z.B. 9029190001");
        startBarcodeField.setPrefWidth(150);
        startRow.getChildren().addAll(new Label("Von Barcode:"), startBarcodeField);

        HBox endRow = new HBox(10);
        endRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        endBarcodeField = new TextField();
        endBarcodeField.setPromptText("z.B. 9029190010");
        endBarcodeField.setPrefWidth(150);
        endRow.getChildren().addAll(new Label("Bis Barcode:"), endBarcodeField);

        HBox rangeButtonRow = new HBox(10);
        Button btnRangeCsv = new Button("CSV Export");
        Button btnRangeJson = new Button("JSON Export");
        btnRangeCsv.setOnAction(e -> exportBarcodeRange("csv"));
        btnRangeJson.setOnAction(e -> exportBarcodeRange("json"));
        rangeButtonRow.getChildren().addAll(btnRangeCsv, btnRangeJson);

        ((VBox) rangeSection).getChildren().addAll(startRow, endRow, rangeButtonRow);

        // Log Section
        VBox logSection = createSection("Log");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(12);
        logArea.setMinHeight(150);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        ((VBox) logSection).getChildren().add(logArea);

        root.getChildren().addAll(titleLabel, statusBar, barcodeSection, auftragSection, rangeSection, logSection);
        VBox.setVgrow(logSection, Priority.ALWAYS);

        Scene scene = new Scene(root, 520, 680);
        stage.setTitle("IMA Tastaturbrücke");
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.getIcons().add(createAppIcon());
        stage.show();

        // Remove focus from all text fields at startup
        // This prevents barcode scanner input from going into text fields
        root.requestFocus();

        // ESC key removes focus from text fields
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                root.requestFocus();
            }
        });

        // Clicking on empty area (gray background) removes focus
        root.setOnMouseClicked(event -> {
            if (event.getTarget() == root) {
                root.requestFocus();
            }
        });

        // Start global keyboard hook
        startGlobalHook();

        // Cleanup on close
        stage.setOnCloseRequest(e -> {
            if (keyboardHook != null) {
                keyboardHook.stop();
            }
        });

        log("Application started");
        log("Config: " + config.getSummary());
        log("Global keyboard hook active");
    }

    /**
     * Creates a programmatic app icon (blue square with white "I" letter).
     * Simple pixel-based approach that works before stage is shown.
     */
    private Image createAppIcon() {
        int size = 32;
        WritableImage image = new WritableImage(size, size);
        var writer = image.getPixelWriter();

        Color blue = Color.web("#1976D2");
        Color white = Color.WHITE;

        // Fill blue background
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                writer.setColor(x, y, blue);
            }
        }

        // Draw white "I" letter (simple block style)
        // Top bar
        for (int x = 8; x < 24; x++) {
            for (int y = 6; y < 10; y++) {
                writer.setColor(x, y, white);
            }
        }
        // Vertical bar
        for (int x = 13; x < 19; x++) {
            for (int y = 10; y < 22; y++) {
                writer.setColor(x, y, white);
            }
        }
        // Bottom bar
        for (int x = 8; x < 24; x++) {
            for (int y = 22; y < 26; y++) {
                writer.setColor(x, y, white);
            }
        }

        return image;
    }

    /**
     * Creates a styled section container.
     */
    private VBox createSection(String title) {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: white; -fx-background-radius: 5;");

        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        section.getChildren().add(sectionTitle);

        return section;
    }

    /**
     * Initializes and starts the global keyboard hook.
     */
    private void startGlobalHook() {
        keyboardHook = new GlobalKeyboardHook(barcode -> {
            Platform.runLater(() -> {
                lastBarcodeLabel.setText("Letzter Barcode: " + barcode);
                log("Barcode received: " + barcode);
                sendBarcodeToSocket(barcode);
            });
        });
        keyboardHook.start();
    }

    /**
     * Sends barcode to IMA Print API via TCP socket.
     */
    private void sendBarcodeToSocket(String barcode) {
        new Thread(() -> {
            try (Socket socket = new Socket(config.getServerHost(), config.getServerPort());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(barcode);
                String response = in.readLine();

                Platform.runLater(() -> {
                    if (response != null && (response.startsWith("OK") || response.contains("SUCCESS"))) {
                        log("OK - Printed: " + barcode);
                        setStatus("Erfolgreich", "green");
                    } else {
                        log("ERROR: " + response);
                        setStatus("Fehler", "red");
                    }
                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    log("ERROR - Connection failed: " + e.getMessage());
                    setStatus("Keine Verbindung", "red");
                });
            }
        }).start();
    }

    /**
     * Exports ZPL by Auftrag (and optionally Position).
     * CSV: /api/export/auftrag/{auftragsNr} or /api/export/auftrag/{auftragsNr}/pos/{posNr}
     * JSON: /api/export/auftrag/{auftragsNr}/json
     */
    private void exportAuftrag(String format) {
        String auftrag = auftragField.getText().trim();
        String position = positionField.getText().trim();

        if (auftrag.isEmpty()) {
            log("Error: Auftrag Nr is required");
            setStatus("Auftrag fehlt", "red");
            return;
        }

        String endpoint;
        if (!position.isEmpty()) {
            // Auftrag + Position
            if (format.equals("json")) {
                endpoint = String.format("/api/export/auftrag/%s/pos/%s/json", auftrag, position);
                log("Exporting: Auftrag=" + auftrag + ", Position=" + position + " [JSON]");
            } else {
                endpoint = String.format("/api/export/auftrag/%s/pos/%s", auftrag, position);
                log("Exporting: Auftrag=" + auftrag + ", Position=" + position + " [CSV]");
            }
        } else if (format.equals("json")) {
            // Auftrag only -> JSON
            endpoint = String.format("/api/export/auftrag/%s/json", auftrag);
            log("Exporting: Auftrag=" + auftrag + " [JSON]");
        } else {
            // Auftrag only -> CSV
            endpoint = String.format("/api/export/auftrag/%s", auftrag);
            log("Exporting: Auftrag=" + auftrag + " [CSV]");
        }

        callApiEndpoint(endpoint);
    }


    /**
     * Exports ZPL for a barcode range.
     * CSV:  /api/export/range?startBarcode=X&endBarcode=Y
     * JSON: /api/export/range/json?startBarcode=X&endBarcode=Y
     */
    private void exportBarcodeRange(String format) {
        String startBarcode = startBarcodeField.getText().trim();
        String endBarcode = endBarcodeField.getText().trim();

        if (startBarcode.isEmpty() || endBarcode.isEmpty()) {
            log("Error: Both start and end barcode are required");
            setStatus("Barcode fehlt", "red");
            return;
        }

        String endpoint;
        if (format.equals("json")) {
            endpoint = String.format("/api/export/range/json?startBarcode=%s&endBarcode=%s", startBarcode, endBarcode);
        } else {
            endpoint = String.format("/api/export/range?startBarcode=%s&endBarcode=%s", startBarcode, endBarcode);
        }
        log("Exporting range: " + startBarcode + " to " + endBarcode + " [" + format.toUpperCase() + "]");

        callApiEndpoint(endpoint);
    }

    /**
     * Calls the API endpoint, reads response and saves to file.
     */
    private void callApiEndpoint(String endpoint) {
        String format = endpoint.contains("/json") ? "json" : "csv";

        new Thread(() -> {
            try {
                String url = String.format("http://%s:%d%s", config.getServerHost(), config.getApiPort(), endpoint);
                java.net.URL apiUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    // Read response body
                    StringBuilder content = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                    }

                    // Save to file
                    String savedPath = saveExportToFile(content.toString(), format);

                    Platform.runLater(() -> {
                        if (savedPath != null) {
                            log("OK - Saved: " + savedPath);
                            setStatus("Export OK", "green");
                        } else {
                            log("ERROR - Could not save file");
                            setStatus("Speicherfehler", "red");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        if (responseCode == 404) {
                            log("ERROR - Data not found (404)");
                            setStatus("Nicht gefunden", "orange");
                        } else {
                            log("ERROR - API error: " + responseCode);
                            setStatus("API Fehler", "red");
                        }
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    log("ERROR: " + e.getMessage());
                    setStatus("Fehler", "red");
                });
            }
        }).start();
    }

    /**
     * Saves export content to file in EXPORT_FOLDER.
     * Creates folder if it doesn't exist.
     */
    private String saveExportToFile(String content, String format) {
        try {
            // Create export folder if not exists
            Path exportDir = Paths.get(config.getExportFolder());
            if (!Files.exists(exportDir)) {
                Files.createDirectories(exportDir);
            }

            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(FILE_TIME_FORMAT);
            String extension = format.equals("json") ? ".json" : ".csv";
            String filename = "export_" + timestamp + extension;

            // Write file
            Path filePath = exportDir.resolve(filename);
            Files.writeString(filePath, content);

            return filePath.toString();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Updates status label with text and color.
     */
    private void setStatus(String text, String color) {
        statusLabel.setText(text);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
    }

    /**
     * Appends timestamped message to log area.
     */
    private void log(String msg) {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        logArea.appendText("[" + time + "] " + msg + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    public static void main(String[] args) {
        launch();
    }
}

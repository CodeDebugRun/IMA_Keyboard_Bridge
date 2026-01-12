package de.lebo.keyboard_bridge;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Application configuration loader.
 * Loads settings from config.properties file located next to the JAR.
 * Falls back to default values if file is not found.
 */
public class AppConfig {

    private static AppConfig instance;

    // Configuration values
    private String serverHost;
    private int serverPort;
    private int apiPort;
    private String exportFolder;

    // Default values
    private static final String DEFAULT_SERVER_HOST = "localhost";
    private static final int DEFAULT_SERVER_PORT = 4000;
    private static final int DEFAULT_API_PORT = 8080;
    private static final String DEFAULT_EXPORT_FOLDER = "C:\\DOCUMENTS\\Exported_ZPL_Etiketten_Code";

    private AppConfig() {
        loadConfig();
    }

    /**
     * Gets the singleton instance of AppConfig.
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    /**
     * Loads configuration from config.properties file.
     * Searches in: 1) JAR directory, 2) Current working directory
     */
    private void loadConfig() {
        Properties props = new Properties();
        Path configPath = findConfigFile();

        if (configPath != null && Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                props.load(is);
                System.out.println("Config loaded from: " + configPath);
            } catch (IOException e) {
                System.err.println("Error loading config: " + e.getMessage());
            }
        } else {
            System.out.println("config.properties not found, using defaults");
        }

        // Load values with defaults
        serverHost = props.getProperty("server.host", DEFAULT_SERVER_HOST);
        serverPort = parseInt(props.getProperty("server.port"), DEFAULT_SERVER_PORT);
        apiPort = parseInt(props.getProperty("api.port"), DEFAULT_API_PORT);
        exportFolder = props.getProperty("export.folder", DEFAULT_EXPORT_FOLDER);
    }

    /**
     * Finds the config.properties file.
     * First checks JAR directory, then current working directory.
     */
    private Path findConfigFile() {
        // Try JAR directory first
        try {
            String jarPath = AppConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();

            // Handle Windows path (remove leading slash if present)
            if (jarPath.startsWith("/") && jarPath.contains(":")) {
                jarPath = jarPath.substring(1);
            }

            Path jarDir = Paths.get(jarPath).getParent();
            if (jarDir != null) {
                Path configInJarDir = jarDir.resolve("config.properties");
                if (Files.exists(configInJarDir)) {
                    return configInJarDir;
                }
            }
        } catch (Exception e) {
            // Ignore, try working directory
        }

        // Try current working directory
        Path configInWorkDir = Paths.get("config.properties");
        if (Files.exists(configInWorkDir)) {
            return configInWorkDir;
        }

        return null;
    }

    /**
     * Safely parses an integer with fallback to default.
     */
    private int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Getters
    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getApiPort() {
        return apiPort;
    }

    public String getExportFolder() {
        return exportFolder;
    }

    /**
     * Returns a summary of current configuration for logging.
     */
    public String getSummary() {
        return String.format("Server: %s:%d, API: %d, Export: %s",
                serverHost, serverPort, apiPort, exportFolder);
    }
}

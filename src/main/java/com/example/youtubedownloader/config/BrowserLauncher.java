package com.example.youtubedownloader.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Opens the app in the default browser once startup is complete. Uses OS commands instead of
 * {@link java.awt.Desktop} because Spring Boot runs the JVM headless by default.
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final DownloaderProperties properties;
    private final Environment environment;

    public BrowserLauncher(DownloaderProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        // local.server.port is only set when a real web server started, so tests never open a browser.
        String port = environment.getProperty("local.server.port");
        if (!properties.openBrowser() || port == null) {
            return;
        }
        String url = "http://localhost:" + port + "/";
        try {
            new ProcessBuilder(commandFor(url)).start();
            log.info("Opening {} in the default browser", url);
        } catch (IOException e) {
            log.warn("Could not open browser for {}: {}", url, e.getMessage());
        }
    }

    private List<String> commandFor(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return List.of("open", url);
        }
        if (os.contains("win")) {
            return List.of("rundll32", "url.dll,FileProtocolHandler", url);
        }
        return List.of("xdg-open", url);
    }
}

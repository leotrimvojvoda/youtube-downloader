package com.example.youtubedownloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "downloader")
public record DownloaderProperties(
        String outputDir,
        String ytDlpPath,
        String ffmpegPath,
        int maxConcurrentDownloads,
        Duration processTimeout,
        int defaultMaxHeight
) {
}

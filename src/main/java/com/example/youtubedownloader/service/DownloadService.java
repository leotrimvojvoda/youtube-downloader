package com.example.youtubedownloader.service;

import com.example.youtubedownloader.api.InvalidRequestException;
import com.example.youtubedownloader.api.dto.DownloadRequest;
import com.example.youtubedownloader.config.DownloaderProperties;
import com.example.youtubedownloader.job.DownloadJob;
import com.example.youtubedownloader.job.JobNotFoundException;
import com.example.youtubedownloader.job.JobStore;
import com.example.youtubedownloader.util.TimeParser;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Service
public class DownloadService {

    private final JobStore jobStore;
    private final YtDlpRunner runner;
    private final ThreadPoolTaskExecutor downloadExecutor;
    private final DownloaderProperties properties;

    public DownloadService(JobStore jobStore,
                           YtDlpRunner runner,
                           ThreadPoolTaskExecutor downloadExecutor,
                           DownloaderProperties properties) {
        this.jobStore = jobStore;
        this.runner = runner;
        this.downloadExecutor = downloadExecutor;
        this.properties = properties;
    }

    @PostConstruct
    void createOutputDir() {
        try {
            Files.createDirectories(Path.of(properties.outputDir()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create output directory " + properties.outputDir(), e);
        }
    }

    public DownloadJob submit(DownloadRequest request) {
        Integer startSeconds = parseTime(request.start(), "start");
        Integer endSeconds = parseTime(request.end(), "end");
        if (startSeconds != null && endSeconds != null && endSeconds <= startSeconds) {
            throw new InvalidRequestException("'end' must be greater than 'start'");
        }
        int maxHeight = request.maxHeight() != null ? request.maxHeight() : properties.defaultMaxHeight();

        DownloadJob job = new DownloadJob(UUID.randomUUID(), request.url(), startSeconds, endSeconds, maxHeight);
        jobStore.save(job);
        try {
            downloadExecutor.submit(() -> runner.execute(job));
        } catch (RejectedExecutionException e) {
            jobStore.remove(job.getId());
            throw e;
        }
        return job;
    }

    public DownloadJob getJob(UUID id) {
        return jobStore.find(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    private Integer parseTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TimeParser.parseToSeconds(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid '" + fieldName + "': " + e.getMessage());
        }
    }
}

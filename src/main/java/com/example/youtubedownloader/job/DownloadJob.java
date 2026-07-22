package com.example.youtubedownloader.job;

import java.time.Instant;
import java.util.UUID;

public class DownloadJob {

    private final UUID id;
    private final String url;
    private final Integer startSeconds;
    private final Integer endSeconds;
    private final int maxHeight;
    private final Instant createdAt;

    private volatile JobStatus status;
    private volatile String title;
    private volatile DownloadProgress progress;
    private volatile String filePath;
    private volatile String errorMessage;
    private volatile Instant finishedAt;

    public DownloadJob(UUID id, String url, Integer startSeconds, Integer endSeconds, int maxHeight) {
        this.id = id;
        this.url = url;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.maxHeight = maxHeight;
        this.createdAt = Instant.now();
        this.status = JobStatus.QUEUED;
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public Integer getStartSeconds() {
        return startSeconds;
    }

    public Integer getEndSeconds() {
        return endSeconds;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DownloadProgress getProgress() {
        return progress;
    }

    public void setProgress(DownloadProgress progress) {
        this.progress = progress;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}

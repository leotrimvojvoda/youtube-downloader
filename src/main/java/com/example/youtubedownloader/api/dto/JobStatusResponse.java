package com.example.youtubedownloader.api.dto;

import com.example.youtubedownloader.job.DownloadJob;
import com.example.youtubedownloader.job.DownloadProgress;
import com.example.youtubedownloader.job.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        String title,
        DownloadProgress progress,
        String url,
        String filePath,
        String error,
        Instant createdAt,
        Instant finishedAt
) {

    public static JobStatusResponse from(DownloadJob job) {
        return new JobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getTitle(),
                job.getProgress(),
                job.getUrl(),
                job.getFilePath(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getFinishedAt()
        );
    }
}

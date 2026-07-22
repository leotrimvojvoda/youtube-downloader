package com.example.youtubedownloader.api.dto;

import com.example.youtubedownloader.job.DownloadJob;
import com.example.youtubedownloader.job.JobStatus;

import java.util.UUID;

public record JobCreatedResponse(UUID jobId, JobStatus status, String statusUrl) {

    public static JobCreatedResponse from(DownloadJob job) {
        return new JobCreatedResponse(job.getId(), job.getStatus(), "/api/downloads/" + job.getId());
    }
}

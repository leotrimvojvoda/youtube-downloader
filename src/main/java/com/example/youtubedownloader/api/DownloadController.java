package com.example.youtubedownloader.api;

import com.example.youtubedownloader.api.dto.DownloadRequest;
import com.example.youtubedownloader.api.dto.JobCreatedResponse;
import com.example.youtubedownloader.api.dto.JobStatusResponse;
import com.example.youtubedownloader.job.DownloadJob;
import com.example.youtubedownloader.service.DownloadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/downloads")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping
    public ResponseEntity<JobCreatedResponse> submit(@Valid @RequestBody DownloadRequest request) {
        DownloadJob job = downloadService.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobCreatedResponse.from(job));
    }

    @GetMapping("/{jobId}")
    public JobStatusResponse status(@PathVariable UUID jobId) {
        return JobStatusResponse.from(downloadService.getJob(jobId));
    }
}

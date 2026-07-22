package com.example.youtubedownloader.job;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobStore {

    private final ConcurrentHashMap<UUID, DownloadJob> jobs = new ConcurrentHashMap<>();

    public void save(DownloadJob job) {
        jobs.put(job.getId(), job);
    }

    public Optional<DownloadJob> find(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public void remove(UUID id) {
        jobs.remove(id);
    }
}

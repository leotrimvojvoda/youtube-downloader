package com.example.youtubedownloader.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor downloadExecutor(DownloaderProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.maxConcurrentDownloads());
        executor.setMaxPoolSize(properties.maxConcurrentDownloads());
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("yt-dlp-");
        return executor;
    }
}

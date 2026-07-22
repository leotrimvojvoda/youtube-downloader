package com.example.youtubedownloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YoutubeDownloaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeDownloaderApplication.class, args);
    }

}

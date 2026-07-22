package com.example.youtubedownloader.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record DownloadRequest(
        @NotBlank(message = "url is required")
        @Pattern(
                regexp = "^https?://(www\\.)?(youtube\\.com/(watch\\?v=|shorts/)|youtu\\.be/)[\\w-]{11}.*$",
                message = "url must be a valid YouTube video link"
        )
        String url,
        String start,
        String end,
        @Positive(message = "maxHeight must be positive")
        Integer maxHeight
) {
}

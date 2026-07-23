package com.example.youtubedownloader.api;

import com.example.youtubedownloader.service.TimelineVideoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline-video")
public class TimelineVideoController {

    private final TimelineVideoService timelineVideoService;

    public TimelineVideoController(TimelineVideoService timelineVideoService) {
        this.timelineVideoService = timelineVideoService;
    }

    @GetMapping
    public ResponseEntity<byte[]> render(@RequestParam String duration,
                                         @RequestParam(required = false) String start,
                                         @RequestParam(required = false) String end) {
        byte[] video = timelineVideoService.render(duration, start, end);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timeline.mp4\"")
                .body(video);
    }
}

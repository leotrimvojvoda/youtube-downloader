package com.example.youtubedownloader.api;

import com.example.youtubedownloader.api.dto.DownloadRequest;
import com.example.youtubedownloader.job.DownloadJob;
import com.example.youtubedownloader.job.JobNotFoundException;
import com.example.youtubedownloader.service.DownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DownloadController.class)
class DownloadControllerTest {

    private static final String URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DownloadService downloadService;

    @Test
    void submitReturns202WithJobId() throws Exception {
        DownloadJob job = new DownloadJob(UUID.randomUUID(), URL, null, null, 1080);
        when(downloadService.submit(any(DownloadRequest.class))).thenReturn(job);

        mockMvc.perform(post("/api/downloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.statusUrl").value("/api/downloads/" + job.getId()));
    }

    @Test
    void submitRejectsMissingUrl() throws Exception {
        mockMvc.perform(post("/api/downloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void submitRejectsNonYoutubeUrl() throws Exception {
        mockMvc.perform(post("/api/downloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/video\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void submitRejectsInvalidTimeRange() throws Exception {
        when(downloadService.submit(any(DownloadRequest.class)))
                .thenThrow(new InvalidRequestException("'end' must be greater than 'start'"));

        mockMvc.perform(post("/api/downloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + URL + "\",\"start\":\"20:00\",\"end\":\"10:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("'end' must be greater than 'start'"));
    }

    @Test
    void statusReturnsJob() throws Exception {
        DownloadJob job = new DownloadJob(UUID.randomUUID(), URL, 600, 1200, 1080);
        when(downloadService.getJob(job.getId())).thenReturn(job);

        mockMvc.perform(get("/api/downloads/" + job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.url").value(URL));
    }

    @Test
    void statusReturns404ForUnknownJob() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(downloadService.getJob(unknown)).thenThrow(new JobNotFoundException(unknown));

        mockMvc.perform(get("/api/downloads/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Job not found: " + unknown));
    }
}

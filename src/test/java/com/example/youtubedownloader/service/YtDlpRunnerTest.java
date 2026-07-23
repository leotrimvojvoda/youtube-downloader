package com.example.youtubedownloader.service;

import com.example.youtubedownloader.config.DownloaderProperties;
import com.example.youtubedownloader.job.DownloadJob;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class YtDlpRunnerTest {

    private static final String URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw";

    private YtDlpRunner runner(String ffmpegPath) {
        return new YtDlpRunner(new DownloaderProperties(
                "/tmp/out", "yt-dlp", ffmpegPath, 2, Duration.ofMinutes(30), 1080, false));
    }

    private DownloadJob job(Integer startSeconds, Integer endSeconds, int maxHeight) {
        return new DownloadJob(UUID.randomUUID(), URL, startSeconds, endSeconds, maxHeight);
    }

    @Test
    void buildsDefaultCommandWithoutSections() {
        List<String> command = runner("ffmpeg").buildCommand(job(null, null, 1080));

        assertThat(command).containsExactly(
                "yt-dlp",
                "--no-playlist",
                "--newline",
                "--progress",
                "--no-quiet",
                "-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
                "--merge-output-format", "mp4",
                "-o", "/tmp/out/%(title)s [%(id)s].%(ext)s",
                "--no-simulate",
                "--print", "after_move:filepath",
                "--print", "video:TITLE::%(title)s",
                URL
        );
    }

    @Test
    void addsDownloadSectionsForClipRange() {
        List<String> command = runner("ffmpeg").buildCommand(job(600, 1200, 1080));

        assertThat(command).containsSubsequence(
                "--download-sections", "*00:10:00-00:20:00", "--force-keyframes-at-cuts");
    }

    @Test
    void openEndedRanges() {
        assertThat(runner("ffmpeg").buildCommand(job(600, null, 1080)))
                .containsSubsequence("--download-sections", "*00:10:00-inf");
        assertThat(runner("ffmpeg").buildCommand(job(null, 1200, 1080)))
                .containsSubsequence("--download-sections", "*00:00:00-00:20:00");
    }

    @Test
    void usesRequestedMaxHeight() {
        assertThat(runner("ffmpeg").buildCommand(job(null, null, 720)))
                .contains("bestvideo[height<=720]+bestaudio/best[height<=720]/best");
    }

    @Test
    void addsFfmpegLocationOnlyWhenCustom() {
        assertThat(runner("ffmpeg").buildCommand(job(null, null, 1080)))
                .doesNotContain("--ffmpeg-location");
        assertThat(runner("/opt/ffmpeg/bin/ffmpeg").buildCommand(job(null, null, 1080)))
                .containsSubsequence("--ffmpeg-location", "/opt/ffmpeg/bin/ffmpeg");
    }

    @Test
    void urlIsLastArgument() {
        List<String> command = runner("ffmpeg").buildCommand(job(600, 1200, 720));
        assertThat(command.getLast()).isEqualTo(URL);
    }
}

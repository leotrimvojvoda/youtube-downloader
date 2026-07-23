package com.example.youtubedownloader.service;

import com.example.youtubedownloader.api.InvalidRequestException;
import com.example.youtubedownloader.config.DownloaderProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimelineVideoServiceTest {

    private TimelineVideoService service(String ffmpegPath) {
        return new TimelineVideoService(new DownloaderProperties(
                "/tmp/out", "yt-dlp", ffmpegPath, 2, Duration.ofMinutes(30), 1080, false));
    }

    @Test
    void resolvesFullTimelineWhenStartAndEndOmitted() {
        var timeline = TimelineVideoService.resolve("60", null, "");

        assertThat(timeline).isEqualTo(new TimelineVideoService.Timeline(3600, 0, 3600));
    }

    @Test
    void resolvesClipRange() {
        var timeline = TimelineVideoService.resolve("60", "10:00", "15:00");

        assertThat(timeline).isEqualTo(new TimelineVideoService.Timeline(3600, 600, 900));
        assertThat(timeline.clipSeconds()).isEqualTo(300);
    }

    @Test
    void rejectsInvalidRanges() {
        assertThatThrownBy(() -> TimelineVideoService.resolve("0", null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> TimelineVideoService.resolve("60", "15:00", "10:00"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("start must be before end");
        assertThatThrownBy(() -> TimelineVideoService.resolve("60", "10:00", "2:00:00"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must not be after the total duration");
    }

    @Test
    void rejectsUnparseableTimes() {
        assertThatThrownBy(() -> TimelineVideoService.resolve("60", "abc", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid start");
    }

    @Test
    void formatsLabelsLikeThePage() {
        assertThat(TimelineVideoService.formatLabel(600, true)).isEqualTo("00:10:00");
        assertThat(TimelineVideoService.formatLabel(600, false)).isEqualTo("10:00");
    }

    @Test
    void buildsRawVideoPipeCommand() {
        List<String> command = service("/opt/bin/ffmpeg").buildCommand(Path.of("/tmp/out.mp4"));

        assertThat(command).containsExactly(
                "/opt/bin/ffmpeg",
                "-y",
                "-hide_banner",
                "-loglevel", "error",
                "-f", "rawvideo",
                "-pix_fmt", "bgr24",
                "-video_size", "1280x240",
                "-framerate", "30",
                "-i", "pipe:0",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                "/tmp/out.mp4");
    }
}

package com.example.youtubedownloader.service;

import com.example.youtubedownloader.job.DownloadProgress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YtDlpOutputParserTest {

    @Test
    void parsesPercentSpeedAndEta() {
        DownloadProgress progress = YtDlpOutputParser
                .parseProgress("[download]   1.4% of    2.16MiB at  708.16KiB/s ETA 00:03")
                .orElseThrow();

        assertThat(progress.percent()).isEqualTo(1);
        assertThat(progress.speed()).isEqualTo("708.16KiB/s");
        assertThat(progress.eta()).isEqualTo("00:03");
        assertThat(progress.postProcessing()).isFalse();
    }

    @Test
    void treatsUnknownSpeedAndEtaAsAbsent() {
        DownloadProgress progress = YtDlpOutputParser
                .parseProgress("[download]   0.0% of    2.16MiB at  Unknown B/s ETA Unknown")
                .orElseThrow();

        assertThat(progress.percent()).isZero();
        assertThat(progress.speed()).isNull();
        assertThat(progress.eta()).isNull();
    }

    @Test
    void parsesFinalLineWithoutEta() {
        DownloadProgress progress = YtDlpOutputParser
                .parseProgress("[download] 100% of    2.16MiB in 00:00:01 at 1.90MiB/s")
                .orElseThrow();

        assertThat(progress.percent()).isEqualTo(100);
        assertThat(progress.speed()).isEqualTo("1.90MiB/s");
        assertThat(progress.eta()).isNull();
    }

    @Test
    void parsesFragmentedAndEstimatedSizeLine() {
        DownloadProgress progress = YtDlpOutputParser
                .parseProgress("[download]  45.2% of ~  10.00MiB at    1.20MiB/s ETA 00:04 (frag 3/10)")
                .orElseThrow();

        assertThat(progress.percent()).isEqualTo(45);
        assertThat(progress.speed()).isEqualTo("1.20MiB/s");
        assertThat(progress.eta()).isEqualTo("00:04");
    }

    @Test
    void ignoresNonProgressLines() {
        assertThat(YtDlpOutputParser.parseProgress("[youtube] Extracting URL: https://youtu.be/abc")).isEmpty();
        assertThat(YtDlpOutputParser.parseProgress("[download] Destination: /tmp/out/video.f137.mp4")).isEmpty();
        assertThat(YtDlpOutputParser.parseProgress("/tmp/out/video.mp4")).isEmpty();
    }

    @Test
    void parsesMarkedTitleLine() {
        assertThat(YtDlpOutputParser.parseTitle("TITLE::Me at the zoo")).contains("Me at the zoo");
        assertThat(YtDlpOutputParser.parseTitle("[youtube] jNQXAC9IVRw: Downloading webpage")).isEmpty();
        assertThat(YtDlpOutputParser.parseTitle("/tmp/out/TITLE.mp4")).isEmpty();
    }

    @Test
    void detectsFfmpegStatsLines() {
        assertThat(YtDlpOutputParser.isFfmpegStats(
                "frame=   75 fps=0.0 q=-1.0 Lsize=     191KiB time=00:00:04.86 bitrate= 321.9kbits/s speed=  37x")).isTrue();
        assertThat(YtDlpOutputParser.isFfmpegStats(
                "size=     191KiB time=00:00:04.86 bitrate= 321.9kbits/s speed=  37x")).isTrue();
        assertThat(YtDlpOutputParser.isFfmpegStats("[download]  45.2% of 10MiB")).isFalse();
        assertThat(YtDlpOutputParser.isFfmpegStats("Stream mapping:")).isFalse();
    }

    @Test
    void parsesFfmpegTimePosition() {
        assertThat(YtDlpOutputParser.parseFfmpegTimeSeconds(
                "frame=  150 fps=25 q=28.0 size=    1024KiB time=00:01:30.52 bitrate= 700.1kbits/s speed=1.2x"))
                .contains(90);
        assertThat(YtDlpOutputParser.parseFfmpegTimeSeconds("size= 191KiB time=N/A bitrate=N/A")).isEmpty();
    }

    @Test
    void detectsPostProcessingLines() {
        assertThat(YtDlpOutputParser.isPostProcessing("[Merger] Merging formats into \"/tmp/out/v.mp4\"")).isTrue();
        assertThat(YtDlpOutputParser.isPostProcessing("[ffmpeg] Cutting to sections")).isTrue();
        assertThat(YtDlpOutputParser.isPostProcessing("[download] 100% of 2.16MiB")).isFalse();
    }
}

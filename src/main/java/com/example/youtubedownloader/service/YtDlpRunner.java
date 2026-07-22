package com.example.youtubedownloader.service;

import com.example.youtubedownloader.config.DownloaderProperties;
import com.example.youtubedownloader.job.DownloadJob;
import com.example.youtubedownloader.job.DownloadProgress;
import com.example.youtubedownloader.job.JobStatus;
import com.example.youtubedownloader.util.TimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class YtDlpRunner {

    private static final Logger log = LoggerFactory.getLogger(YtDlpRunner.class);
    private static final int STDERR_TAIL_LINES = 40;

    private final DownloaderProperties properties;

    public YtDlpRunner(DownloaderProperties properties) {
        this.properties = properties;
    }

    public void execute(DownloadJob job) {
        job.setStatus(JobStatus.IN_PROGRESS);
        List<String> command = buildCommand(job);
        log.info("Job {}: starting {}", job.getId(), String.join(" ", command));
        try {
            Process process = new ProcessBuilder(command).start();

            AtomicReference<String> lastStdoutLine = new AtomicReference<>();
            Deque<String> stderrTail = new ArrayDeque<>();
            Thread stdoutReader = readLines(process.getInputStream(), line -> {
                var title = YtDlpOutputParser.parseTitle(line);
                if (title.isPresent()) {
                    job.setTitle(title.get());
                    return;
                }
                if (!line.isBlank()) {
                    lastStdoutLine.set(line);
                }
                updateProgress(job, line);
            });
            Thread stderrReader = readLines(process.getErrorStream(), line -> {
                if (YtDlpOutputParser.isFfmpegStats(line)) {
                    updateClipProgress(job, line);
                    return;
                }
                synchronized (stderrTail) {
                    if (stderrTail.size() == STDERR_TAIL_LINES) {
                        stderrTail.removeFirst();
                    }
                    stderrTail.addLast(line);
                }
            });

            boolean finished = process.waitFor(properties.processTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                fail(job, "Download timed out after " + properties.processTimeout());
                return;
            }
            stdoutReader.join(5000);
            stderrReader.join(5000);

            if (process.exitValue() == 0) {
                job.setFilePath(lastStdoutLine.get());
                job.setStatus(JobStatus.COMPLETED);
                job.setFinishedAt(Instant.now());
                log.info("Job {}: completed, file at {}", job.getId(), job.getFilePath());
            } else {
                String stderr;
                synchronized (stderrTail) {
                    stderr = String.join("\n", stderrTail);
                }
                fail(job, "yt-dlp exited with code " + process.exitValue() + ":\n" + stderr);
            }
        } catch (IOException e) {
            fail(job, "Failed to start yt-dlp ('" + properties.ytDlpPath()
                    + "'): " + e.getMessage() + ". Install yt-dlp or set downloader.yt-dlp-path.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(job, "Download was interrupted");
        }
    }

    List<String> buildCommand(DownloadJob job) {
        List<String> command = new ArrayList<>();
        command.add(properties.ytDlpPath());
        command.add("--no-playlist");
        command.add("--newline");
        // --print below implies --quiet; --progress restores the download progress lines and
        // --no-quiet the post-processor logs (stdout) and ffmpeg stats (stderr) we parse for progress.
        command.add("--progress");
        command.add("--no-quiet");
        command.add("-f");
        command.add("bestvideo[height<=%1$d]+bestaudio/best[height<=%1$d]/best".formatted(job.getMaxHeight()));
        command.add("--merge-output-format");
        command.add("mp4");
        command.add("-o");
        command.add(Path.of(properties.outputDir(), "%(title)s [%(id)s].%(ext)s").toString());
        command.add("--no-simulate");
        command.add("--print");
        command.add("after_move:filepath");
        command.add("--print");
        command.add("video:" + YtDlpOutputParser.TITLE_MARKER + "%(title)s");
        if (job.getStartSeconds() != null || job.getEndSeconds() != null) {
            String start = job.getStartSeconds() != null ? TimeParser.formatHms(job.getStartSeconds()) : "00:00:00";
            String end = job.getEndSeconds() != null ? TimeParser.formatHms(job.getEndSeconds()) : "inf";
            command.add("--download-sections");
            command.add("*" + start + "-" + end);
            command.add("--force-keyframes-at-cuts");
        }
        if (!"ffmpeg".equals(properties.ffmpegPath())) {
            command.add("--ffmpeg-location");
            command.add(properties.ffmpegPath());
        }
        command.add(job.getUrl());
        return command;
    }

    private void updateProgress(DownloadJob job, String line) {
        if (YtDlpOutputParser.isPostProcessing(line)) {
            job.setProgress(DownloadProgress.POST_PROCESSING);
            return;
        }
        YtDlpOutputParser.parseProgress(line).ifPresent(job::setProgress);
    }

    /**
     * For clipped downloads ffmpeg fetches the section itself and yt-dlp emits no progress lines,
     * so the percentage is derived from ffmpeg's "time=" position against the clip duration.
     */
    private void updateClipProgress(DownloadJob job, String line) {
        if (job.getStartSeconds() == null && job.getEndSeconds() == null) {
            return;
        }
        YtDlpOutputParser.parseFfmpegTimeSeconds(line).ifPresent(position -> {
            Integer end = job.getEndSeconds();
            int start = job.getStartSeconds() != null ? job.getStartSeconds() : 0;
            if (end == null || end <= start) {
                job.setProgress(DownloadProgress.DOWNLOADING_UNKNOWN);
                return;
            }
            int percent = Math.min(100, position * 100 / (end - start));
            job.setProgress(DownloadProgress.downloading(percent, null, null));
        });
    }

    private void fail(DownloadJob job, String message) {
        job.setErrorMessage(message);
        job.setStatus(JobStatus.FAILED);
        job.setFinishedAt(Instant.now());
        log.warn("Job {}: failed: {}", job.getId(), message);
    }

    private Thread readLines(InputStream stream, java.util.function.Consumer<String> lineConsumer) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            } catch (IOException e) {
                log.debug("Process stream closed: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}

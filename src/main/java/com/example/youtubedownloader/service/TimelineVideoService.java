package com.example.youtubedownloader.service;

import com.example.youtubedownloader.api.InvalidRequestException;
import com.example.youtubedownloader.api.VideoRenderException;
import com.example.youtubedownloader.config.DownloaderProperties;
import com.example.youtubedownloader.util.TimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Renders the timeline animation (black background, white track line, moving dot,
 * current/total time labels — the same look as timeline.html) to an MP4 file.
 * Frames are drawn with Java2D and piped as raw video into ffmpeg, which only encodes;
 * this avoids depending on optional ffmpeg build features like drawtext.
 */
@Service
public class TimelineVideoService {

    private static final Logger log = LoggerFactory.getLogger(TimelineVideoService.class);
    private static final int STDERR_TAIL_LINES = 40;

    static final int WIDTH = 1280;
    static final int HEIGHT = 240;
    static final int FPS = 30;
    /** Frames appended after the end time is reached, holding the final state. */
    static final int TRAILING_FRAMES = FPS;

    private static final int LABEL_MARGIN = 32;
    private static final int TRACK_CENTER_Y = 120;
    private static final int LINE_HEIGHT = 4;
    private static final int DOT_DIAMETER = 24;
    private static final int LABEL_BASELINE_Y = 200;
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 30);

    private final DownloaderProperties properties;

    public TimelineVideoService(DownloaderProperties properties) {
        this.properties = properties;
    }

    record Timeline(int totalSeconds, int startSeconds, int endSeconds) {
        int clipSeconds() {
            return endSeconds - startSeconds;
        }
    }

    public byte[] render(String duration, String start, String end) {
        Timeline timeline = resolve(duration, start, end);
        Path output = null;
        try {
            output = Files.createTempFile("timeline-", ".mp4");
            runFfmpeg(timeline, output);
            return Files.readAllBytes(output);
        } catch (IOException e) {
            throw new VideoRenderException("Failed to render timeline video: " + e.getMessage());
        } finally {
            deleteQuietly(output);
        }
    }

    static Timeline resolve(String duration, String start, String end) {
        int totalSeconds = parseField("duration", duration);
        if (totalSeconds <= 0) {
            throw new InvalidRequestException("duration must be greater than zero");
        }
        int startSeconds = start == null || start.isBlank() ? 0 : parseField("start", start);
        int endSeconds = end == null || end.isBlank() ? totalSeconds : parseField("end", end);
        if (endSeconds > totalSeconds) {
            throw new InvalidRequestException("end must not be after the total duration");
        }
        if (startSeconds >= endSeconds) {
            throw new InvalidRequestException("start must be before end");
        }
        return new Timeline(totalSeconds, startSeconds, endSeconds);
    }

    private static int parseField(String name, String value) {
        try {
            return TimeParser.parseToSeconds(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid " + name + ": " + e.getMessage());
        }
    }

    private void runFfmpeg(Timeline timeline, Path output) {
        List<String> command = buildCommand(output);
        log.info("Rendering {}s timeline video: {}", timeline.clipSeconds(), String.join(" ", command));
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new VideoRenderException("Failed to start ffmpeg ('" + properties.ffmpegPath() + "'): "
                    + e.getMessage() + ". Install ffmpeg or set downloader.ffmpeg-path.");
        }
        Deque<String> stderrTail = new ArrayDeque<>();
        Thread stderrReader = readStderr(process, stderrTail);
        try {
            writeFrames(timeline, process.getOutputStream());
        } catch (IOException e) {
            // ffmpeg most likely exited early; the exit-code check below reports its stderr.
            log.debug("Frame pipe closed: {}", e.getMessage());
        }
        try {
            boolean finished = process.waitFor(properties.processTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new VideoRenderException("Rendering timed out after " + properties.processTimeout());
            }
            stderrReader.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new VideoRenderException("Rendering was interrupted");
        }
        if (process.exitValue() != 0) {
            String stderr;
            synchronized (stderrTail) {
                stderr = String.join("\n", stderrTail);
            }
            throw new VideoRenderException("ffmpeg exited with code " + process.exitValue() + ":\n" + stderr);
        }
    }

    List<String> buildCommand(Path output) {
        return List.of(
                properties.ffmpegPath(),
                "-y",
                "-hide_banner",
                "-loglevel", "error",
                "-f", "rawvideo",
                "-pix_fmt", "bgr24",
                "-video_size", WIDTH + "x" + HEIGHT,
                "-framerate", String.valueOf(FPS),
                "-i", "pipe:0",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                output.toString());
    }

    private void writeFrames(Timeline timeline, OutputStream processStdin) throws IOException {
        BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(LABEL_FONT);
        byte[] pixels = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();

        boolean withHours = timeline.totalSeconds() >= 3600;
        String totalLabel = formatLabel(timeline.totalSeconds(), withHours);
        long animatedFrames = (long) timeline.clipSeconds() * FPS;
        try (OutputStream out = new BufferedOutputStream(processStdin, 1 << 16)) {
            for (long i = 0; i < animatedFrames + TRAILING_FRAMES; i++) {
                double elapsed = Math.min((double) i / FPS, timeline.clipSeconds());
                drawFrame(g, timeline, elapsed, totalLabel, withHours);
                out.write(pixels);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawFrame(Graphics2D g, Timeline timeline, double elapsed, String totalLabel, boolean withHours) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(Color.WHITE);
        g.fillRect(0, TRACK_CENTER_Y - LINE_HEIGHT / 2, WIDTH, LINE_HEIGHT);
        double dotCenterX = (double) WIDTH * (timeline.startSeconds() + elapsed) / timeline.totalSeconds();
        g.fill(new Ellipse2D.Double(
                dotCenterX - DOT_DIAMETER / 2.0, TRACK_CENTER_Y - DOT_DIAMETER / 2.0, DOT_DIAMETER, DOT_DIAMETER));
        String currentLabel = formatLabel(timeline.startSeconds() + (int) elapsed, withHours);
        g.drawString(currentLabel, LABEL_MARGIN, LABEL_BASELINE_Y);
        g.drawString(totalLabel, WIDTH - LABEL_MARGIN - g.getFontMetrics().stringWidth(totalLabel), LABEL_BASELINE_Y);
    }

    /** Same convention as timeline.html: HH:MM:SS when the total is an hour or more, MM:SS otherwise. */
    static String formatLabel(int seconds, boolean withHours) {
        if (withHours) {
            return TimeParser.formatHms(seconds);
        }
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private Thread readStderr(Process process, Deque<String> tail) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (tail) {
                        if (tail.size() == STDERR_TAIL_LINES) {
                            tail.removeFirst();
                        }
                        tail.addLast(line);
                    }
                }
            } catch (IOException e) {
                log.debug("Process stream closed: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Could not delete temp file {}: {}", file, e.getMessage());
            }
        }
    }
}

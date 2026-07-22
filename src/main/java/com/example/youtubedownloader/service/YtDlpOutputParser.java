package com.example.youtubedownloader.service;

import com.example.youtubedownloader.job.DownloadProgress;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads yt-dlp's stdout lines. Progress lines arrive one per update because the command passes
 * {@code --newline}; without it yt-dlp overwrites a single line with carriage returns.
 */
final class YtDlpOutputParser {

    /** Prefix used with {@code --print} so the title line is unambiguous among other stdout output. */
    static final String TITLE_MARKER = "TITLE::";

    private static final Pattern PERCENT = Pattern.compile("^\\[download]\\s+(\\d{1,3}(?:\\.\\d+)?)%");
    private static final Pattern SPEED = Pattern.compile("\\sat\\s+(.+?/s)");
    private static final Pattern ETA = Pattern.compile("\\sETA\\s+(\\S+)");
    private static final Pattern FFMPEG_TIME = Pattern.compile("\\btime=(\\d+):(\\d{2}):(\\d{2})");

    private static final List<String> POST_PROCESSING_TAGS = List.of(
            "[Merger]", "[ffmpeg]", "[ExtractAudio]", "[VideoConvertor]",
            "[VideoRemuxer]", "[Fixup", "[Metadata]");

    private YtDlpOutputParser() {
    }

    static Optional<DownloadProgress> parseProgress(String line) {
        Matcher percent = PERCENT.matcher(line);
        if (!percent.find()) {
            return Optional.empty();
        }
        int value = Math.min((int) Double.parseDouble(percent.group(1)), 100);
        return Optional.of(DownloadProgress.downloading(value, find(SPEED, line), find(ETA, line)));
    }

    static boolean isPostProcessing(String line) {
        return POST_PROCESSING_TAGS.stream().anyMatch(line::startsWith);
    }

    static Optional<String> parseTitle(String line) {
        return line.startsWith(TITLE_MARKER)
                ? Optional.of(line.substring(TITLE_MARKER.length()))
                : Optional.empty();
    }

    /**
     * ffmpeg's periodic stats on stderr ("frame=... time=00:00:04.86 ... speed=1.2x" or
     * "size=... time=..." for audio). For clipped downloads ffmpeg does the transfer itself and
     * yt-dlp prints no progress, so these are the only progress signal.
     */
    static boolean isFfmpegStats(String line) {
        String trimmed = line.trim();
        return (trimmed.startsWith("frame=") || trimmed.startsWith("size=")) && trimmed.contains("time=");
    }

    /** Extracts the "time=HH:MM:SS" position from an ffmpeg stats line as total seconds. */
    static Optional<Integer> parseFfmpegTimeSeconds(String line) {
        Matcher matcher = FFMPEG_TIME.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(matcher.group(1)) * 3600
                + Integer.parseInt(matcher.group(2)) * 60
                + Integer.parseInt(matcher.group(3)));
    }

    private static String find(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.startsWith("Unknown") ? null : value;
    }
}

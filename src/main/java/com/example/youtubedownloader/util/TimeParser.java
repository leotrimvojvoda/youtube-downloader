package com.example.youtubedownloader.util;

public final class TimeParser {

    private TimeParser() {
    }

    /**
     * Parses a duration given as "MM", "MM:SS" or "HH:MM:SS" into total seconds.
     * A bare number is interpreted as minutes (e.g. "10" means 10:00).
     * When higher units are present, lower units must be in the range 0-59.
     */
    public static int parseToSeconds(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Time value must not be blank");
        }
        String[] parts = value.trim().split(":", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("Invalid time format '" + value + "', expected minutes, MM:SS or HH:MM:SS");
        }
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid time format '" + value + "', expected minutes, MM:SS or HH:MM:SS");
            }
            if (numbers[i] < 0) {
                throw new IllegalArgumentException("Time value '" + value + "' must not be negative");
            }
            boolean hasHigherUnit = i > 0;
            if (hasHigherUnit && numbers[i] > 59) {
                throw new IllegalArgumentException("Invalid time value '" + value + "': '" + parts[i] + "' must be between 0 and 59");
            }
        }
        if (numbers.length == 1) {
            return numbers[0] * 60;
        }
        int seconds = 0;
        for (int number : numbers) {
            seconds = seconds * 60 + number;
        }
        return seconds;
    }

    /**
     * Formats total seconds as "HH:MM:SS" (the format yt-dlp expects in --download-sections).
     */
    public static String formatHms(int totalSeconds) {
        if (totalSeconds < 0) {
            throw new IllegalArgumentException("Seconds must not be negative");
        }
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}

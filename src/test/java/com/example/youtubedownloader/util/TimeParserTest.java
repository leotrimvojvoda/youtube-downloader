package com.example.youtubedownloader.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TimeParserTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "10, 600",
            "45, 2700",
            "90, 5400",
            "0:30, 30",
            "10:00, 600",
            "59:59, 3599",
            "1:00:00, 3600",
            "01:10:05, 4205",
            "100:00:00, 360000"
    })
    void parsesValidFormats(String input, int expectedSeconds) {
        assertThat(TimeParser.parseToSeconds(input)).isEqualTo(expectedSeconds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "1:60", "1:61:00", "10:00:00:00", "-5", "1:-5", "10:", ":30", "1.5"})
    void rejectsInvalidFormats(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> TimeParser.parseToSeconds(input));
    }

    @Test
    void rejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> TimeParser.parseToSeconds(null));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 00:00:00",
            "30, 00:00:30",
            "600, 00:10:00",
            "3599, 00:59:59",
            "4205, 01:10:05",
            "360000, 100:00:00"
    })
    void formatsSecondsAsHms(int seconds, String expected) {
        assertThat(TimeParser.formatHms(seconds)).isEqualTo(expected);
    }
}

package com.example.youtubedownloader.job;

/**
 * A snapshot of a running download. {@code percent} applies to the file yt-dlp is currently
 * fetching, not the job as a whole: video and audio are downloaded as separate streams, so it
 * climbs to 100 once per stream. {@code percent}, {@code speed} and {@code eta} are null while
 * post-processing, and {@code speed}/{@code eta} are null when yt-dlp reports them as unknown.
 */
public record DownloadProgress(Integer percent, String speed, String eta, boolean postProcessing) {

    public static final DownloadProgress POST_PROCESSING = new DownloadProgress(null, null, null, true);

    /** Downloading, but the total is unknown (open-ended clip), so no percentage can be computed. */
    public static final DownloadProgress DOWNLOADING_UNKNOWN = new DownloadProgress(null, null, null, false);

    public static DownloadProgress downloading(int percent, String speed, String eta) {
        return new DownloadProgress(percent, speed, eta, false);
    }
}

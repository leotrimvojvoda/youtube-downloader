# YouTube Downloader

A self-hosted web app for downloading YouTube videos — either the full video or just a section of it (e.g. from `10:00` to `20:00`). Downloads run as background jobs with live progress (video title, percentage, speed, ETA) shown in a simple browser UI, and are also fully usable through a JSON REST API.

Under the hood the app shells out to [yt-dlp](https://github.com/yt-dlp/yt-dlp), so format selection, clipping, and merging are handled by the same battle-tested tooling you'd use on the command line.

## Features

- **Full or partial downloads** — give a start and/or end time (`SS`, `MM:SS`, or `HH:MM:SS`) to download only that section, cut precisely at keyframes.
- **Resolution cap with fallback** — defaults to the best quality at or below 1080p; if a video doesn't have that, the next-best available resolution is used. Override per request with `maxHeight`.
- **Async job model** — submitting a download returns a job ID immediately; the UI (or your own client) polls for status.
- **Live progress** — video title, percent complete, download speed, and ETA, for both full downloads and clipped sections.
- **Web UI** — a single static page at `/`: paste a link, optionally set times, press Download.
- **Concurrency limits** — a bounded worker pool (2 concurrent downloads by default, queue of 10); further requests get `503` instead of overloading the machine.

## Built with

- **Java 25** / **Spring Boot 4** (Web MVC, Bean Validation) — REST API and static UI hosting
- **Gradle** (wrapper included)
- **yt-dlp** + **ffmpeg** — the actual download, cutting, and merging (external tools, invoked as processes)
- Plain HTML/CSS/JavaScript for the UI — no frontend framework, no build step

## Prerequisites

You need these installed before running the app:

| Requirement | Why | Install (macOS) |
|---|---|---|
| **JDK 25** | The app targets Java 25 via the Gradle toolchain | `brew install temurin` or [Adoptium](https://adoptium.net) |
| **yt-dlp** | Performs the actual video download | `brew install yt-dlp` |
| **ffmpeg** | Merges video/audio streams and cuts sections | `brew install ffmpeg` |

On Linux, install `yt-dlp` and `ffmpeg` from your package manager; on Windows, `winget install yt-dlp.yt-dlp Gyan.FFmpeg` or download the binaries. `yt-dlp` and `ffmpeg` must be on the `PATH`, or their locations configured (see below).

## Running

```sh
./gradlew bootRun
```

Then open **http://localhost:8080/** in your browser.

Downloaded files are saved on the machine running the app, by default into `~/Downloads/youtube-downloader/`.

## Configuration

All settings live under `downloader.*` in `src/main/resources/application.yaml`:

| Property | Default | Description |
|---|---|---|
| `downloader.output-dir` | `~/Downloads/youtube-downloader` | Where finished files are saved |
| `downloader.yt-dlp-path` | `yt-dlp` | Path to the yt-dlp binary |
| `downloader.ffmpeg-path` | `ffmpeg` | Path to the ffmpeg binary |
| `downloader.max-concurrent-downloads` | `2` | Worker pool size |
| `downloader.process-timeout` | `30m` | Kill a download that runs longer than this |
| `downloader.default-max-height` | `1080` | Resolution cap used when a request doesn't specify one |

Override any of them on the command line, e.g. `./gradlew bootRun --args='--downloader.output-dir=/data/videos'`.

## REST API

### Submit a download

```
POST /api/downloads
Content-Type: application/json

{
  "url": "https://www.youtube.com/watch?v=jNQXAC9IVRw",
  "start": "10:00",
  "end": "20:00",
  "maxHeight": 1080
}
```

Only `url` is required (watch, shorts, and `youtu.be` links are accepted). Returns `202 Accepted`:

```json
{
  "jobId": "3f8a1c2e-…",
  "status": "QUEUED",
  "statusUrl": "/api/downloads/3f8a1c2e-…"
}
```

### Poll job status

```
GET /api/downloads/{jobId}
```

```json
{
  "jobId": "3f8a1c2e-…",
  "status": "IN_PROGRESS",
  "title": "Me at the zoo",
  "progress": { "percent": 58, "speed": "1.20MiB/s", "eta": "00:04", "postProcessing": false },
  "url": "https://www.youtube.com/watch?v=jNQXAC9IVRw",
  "filePath": null,
  "error": null,
  "createdAt": "2026-07-23T10:00:00Z",
  "finishedAt": null
}
```

`status` is `QUEUED` → `IN_PROGRESS` → `COMPLETED` (with `filePath`) or `FAILED` (with `error`).

Errors return `{"error": "…"}` with `400` (invalid URL/times), `404` (unknown job), or `503` (queue full — retry later).

## Notes

- Jobs are kept in memory only; restarting the app forgets past jobs (already-downloaded files stay on disk).
- Files are saved on the **server**, not sent to the browser. Running the app locally, that's the same machine; if you host it elsewhere, you'd need to fetch files from the output directory yourself.

## Development

```sh
./gradlew build   # compile + tests
./gradlew test    # tests only
```

## Legal

This tool is for downloading content you have the right to download (your own videos, Creative Commons material, etc.). Respect YouTube's Terms of Service and copyright law in your jurisdiction.

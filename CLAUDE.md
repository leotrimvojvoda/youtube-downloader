# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```sh
./gradlew build                                          # compile + test
./gradlew test                                           # all tests
./gradlew test --tests TimeParserTest                    # one test class
./gradlew test --tests "TimeParserTest.parsesMmSs"       # one test method
./gradlew bootRun                                        # run the app (default port 8080)
```

Requires Java 25 (Gradle toolchain). Running the app for real downloads requires `yt-dlp` and `ffmpeg` on the PATH (or configured via `downloader.yt-dlp-path` / `downloader.ffmpeg-path`).

## What this is

A Spring Boot 4 REST service that downloads YouTube videos by shelling out to `yt-dlp`. Async job model: `POST /api/downloads` validates the request, creates a job, and returns 202 with a job ID immediately; `GET /api/downloads/{jobId}` polls status.

## Architecture

Request flow: `DownloadController` → `DownloadService` → (async via `downloadExecutor`) → `YtDlpRunner`.

- **`DownloadService`** (`service/`) does request-level validation (time parsing via `TimeParser`, start < end), creates the `DownloadJob`, saves it to `JobStore`, and submits execution to the thread pool. If the pool rejects (queue full), the job is removed and the `RejectedExecutionException` propagates to the client as 503.
- **`YtDlpRunner`** builds the `yt-dlp` command line and runs the process. It never throws — all failures (bad exit code, timeout, missing binary, interrupt) are recorded on the job as `FAILED` with an error message. `buildCommand` is package-private specifically so tests can assert on the constructed command without spawning a process. The output file path is captured from yt-dlp's `--print after_move:filepath` (last non-blank stdout line). Beware: `--print` implies `--quiet`; the command passes `--progress` and `--no-quiet` to restore the output that `YtDlpOutputParser` scrapes for live progress — yt-dlp `[download] N%` lines on stdout for full downloads, ffmpeg `time=` stats on stderr for clipped ones (yt-dlp delegates section downloads to ffmpeg and prints no progress itself). The video title comes from a second `--print` with a `TITLE::` marker.
- **`JobStore`** is an in-memory `ConcurrentHashMap` — no persistence; jobs are lost on restart. `DownloadJob` is mutated from the worker thread and read from request threads, hence its `volatile` mutable fields.
- **Concurrency limits** live in `AsyncConfig`: pool size = `downloader.max-concurrent-downloads`, queue capacity 10.
- **Config** is the `downloader.*` section of `application.yaml`, bound to the `DownloaderProperties` record.
- **Errors**: throw `InvalidRequestException` (400) or `JobNotFoundException` (404); `GlobalExceptionHandler` maps everything to `{"error": "..."}` JSON.

## Notes

- Spring Boot 4 artifact names differ from Boot 3: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-webmvc-test`, and test annotations come from `org.springframework.boot.webmvc.test.autoconfigure`.
- Time inputs (`start`/`end`) accept `SS`, `MM:SS`, or `HH:MM:SS`; parsing/formatting is centralized in `util/TimeParser`.
- YouTube URL validation is a regex on `DownloadRequest.url` (watch, shorts, and youtu.be forms).

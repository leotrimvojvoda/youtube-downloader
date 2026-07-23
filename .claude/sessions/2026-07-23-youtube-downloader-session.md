# Session log — 2026-07-22/23

Summary of the Claude Code session that took this project from a bare Spring Initializr skeleton to a working, pushed app.

## What was built

A Spring Boot 4 REST service + web UI that downloads YouTube videos via `yt-dlp`:

- `POST /api/downloads` (async job model, 202 + job ID) and `GET /api/downloads/{jobId}` for polling.
- Optional `start`/`end` clipping (`SS`, `MM:SS`, `HH:MM:SS`) and `maxHeight` resolution cap (default 1080, falls back to lower resolutions automatically via the yt-dlp format selector).
- Static single-page UI at `/` (`src/main/resources/static/index.html`) — no Thymeleaf, no framework; chose plain HTML + fetch/polling since the API is JSON anyway.
- Live progress: video title, percent, speed, ETA, and a "merging/cutting" phase indicator.

## Session timeline

1. **`/init`** — wrote `CLAUDE.md` for the existing implementation.
2. **Verified prior work** — user wasn't sure a previous session finished. Everything was implemented except the plan .md; build and tests passed. Found `yt-dlp`/`ffmpeg` missing on the machine; user installed them via Homebrew themselves.
3. **Postman walkthrough** — documented request/response shapes; user's first real download failed only because yt-dlp wasn't installed yet.
4. **UI** — added the static page (text fields for URL/start/end/maxHeight, submit + 2s status polling).
5. **Progress bar** — key findings (empirically verified, see below).
6. **Bug fix** — Enter in a text field implicitly submitted the form ("changing a value starts the download"). Fixed with `type="button"` + blocked form submit + `reportValidity()`.
7. **Redesign** — "modern and enterprise" look: card layout, styled inputs/status panel/progress bar, CSS variables, system font stack. Still one self-contained file.
8. **Title + clip progress** — added video title display and a computed percentage for clipped downloads.
9. **Git** — initialized repo, first commit, pushed to `git@github.com:leotrimvojvoda/youtube-downloader.git`.
10. **README** — full README (features, stack, prerequisites, config table, API docs, legal note), committed and pushed.

## Hard-won technical findings (worth remembering)

- **`--print` implies `--quiet`** in yt-dlp: it silently suppresses all progress output. `--progress` restores download progress lines; `--no-quiet` restores post-processor logs (stdout) and ffmpeg output (stderr).
- **Clipped downloads (`--download-sections`) emit no yt-dlp progress at all** — yt-dlp delegates the transfer to ffmpeg. Solution: parse ffmpeg's `time=HH:MM:SS` stats from stderr and compute percent against the clip duration (end − start). Open-ended clips (no `end`) → indeterminate progress.
- **Title capture**: second `--print` with a `video:TITLE::%(title)s` marker template, so the title line is unambiguous among other stdout lines; parsed in `YtDlpOutputParser`.
- **ffmpeg stats lines are filtered out of the stderr error tail** so failure messages stay readable.
- Percent is non-monotonic by design (video stream, then audio, then merge each climb to 100).
- The output file path is the **last non-blank stdout line** (`--print after_move:filepath`) — still true with `--no-quiet`.
- macOS quirks hit during verification: no `timeout` command, BSD `sed` lacks `\|` alternation, `cat -A` unsupported.
- yt-dlp skips downloads if the file already exists — cleared the output dir before re-verification runs.

## Environment / user setup notes

- SSH: user's GitHub key is `~/.ssh/id_ed25519_github` (non-default name). `~/.ssh/config` was empty, so pushes failed with `Permission denied (publickey)`; fixed by adding a `Host github.com` block with `IdentityFile` + `IdentitiesOnly`.
- `.gitignore` extended with `.claude/settings.local.json`.
- App runs on 8080; verification during the session used a second instance on 8081 with a scratchpad output dir.
- Files land on the machine running the app (`~/Downloads/youtube-downloader` by default) — flagged to the user that on a remote host you'd want a `GET /api/downloads/{jobId}/file` streaming endpoint (not implemented).

## Open ideas / not done

- File-streaming endpoint to deliver finished downloads to the browser.
- Speed/ETA for clipped downloads (ffmpeg `speed=` field is parseable but currently unused).
- Job persistence (jobs are in-memory; lost on restart).

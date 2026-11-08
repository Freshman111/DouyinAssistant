"""Turn a VoiceExperiment log into real-time and backlog numbers.

Reads the app log lines written by VoiceExperiment (ready / window / overrun /
stop) and reports, per session, how much audio was processed, how long VoxLock
and Paraformer took, and how far recognition lagged behind capture.

  adb exec-out run-as com.sgffsg.douyin.assist cat files/logs/app.log > app.log
  python3 tools/summarize_experiment_log.py app.log
"""
import json
import re
import sys
from pathlib import Path

RATE = 16000
STEP = RATE // 2
SESSION = re.compile(r"session_id=(\d+)")
FIELD = re.compile(r"(\w+)=(\S+)")


def percentile(values, fraction):
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(fraction * (len(ordered) - 1))))]


def parse(path):
    sessions = {}
    for line in Path(path).read_text(errors="replace").splitlines():
        if "VoiceExperiment" not in line:
            continue
        match = SESSION.search(line)
        if not match:
            continue
        session = sessions.setdefault(match.group(1), dict(
            init_ms=None, windows=[], overrun=None, failed=None, texts=[]))
        if "init_ms=" in line:
            session["init_ms"] = int(re.search(r"init_ms=(\d+)", line).group(1))
        elif "window session_id" in line:
            row = dict((key, value) for key, value in FIELD.findall(line))
            text = line.split(" text=", 1)[1] if " text=" in line else ""
            session["windows"].append(dict(
                start=int(row["start_sample"]), end=int(row["end_sample"]),
                asr_ms=int(row["asr_ms"]), denoise_ms=int(row["denoise_ms"]),
                lag_ms=int(row["lag_ms"]), queue=int(row["queue"]),
                matched=row.get("matched") == "true", emitted=row.get("emitted") == "true",
                text=text))
        elif "overrun session_id" in line:
            session["overrun"] = int(re.search(r"input_samples=(\d+)", line).group(1))
        elif "stop session_id" in line:
            session["failed"] = "failed=true" in line
    return sessions


def summarize(session):
    windows = session["windows"]
    if not windows:
        return dict(init_ms=session["init_ms"], failed=session["failed"], windows=0)
    audio_s = windows[-1]["end"] / RATE
    denoise_ms = sum(row["denoise_ms"] for row in windows)
    asr_ms = sum(row["asr_ms"] for row in windows)
    lags = [row["lag_ms"] for row in windows]
    queues = [row["queue"] for row in windows]
    return dict(
        init_ms=session["init_ms"], failed=session["failed"], overrun=session["overrun"],
        audio_s=round(audio_s, 1), windows=len(windows),
        denoise_s=round(denoise_ms / 1000, 2), asr_s=round(asr_ms / 1000, 2),
        voxlock_rtf=round(denoise_ms / 1000 / audio_s, 3),
        combined_rtf=round((denoise_ms + asr_ms) / 1000 / audio_s, 3),
        asr_p95_ms=percentile([row["asr_ms"] for row in windows], 0.95),
        lag_p50_ms=percentile(lags, 0.5), lag_p95_ms=percentile(lags, 0.95),
        lag_max_ms=max(lags), queue_p95=percentile(queues, 0.95), queue_max=max(queues),
        emitted=sum(row["emitted"] for row in windows),
        emitted_text=[row["text"] for row in windows if row["emitted"]],
    )


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    sessions = parse(sys.argv[1])
    result = dict((sid, summarize(session)) for sid, session in sorted(sessions.items()))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print("\n session  audio_s  voxlock_rtf  combined_rtf  lag_p95_ms  queue_max  emitted")
    for sid, row in result.items():
        if not row.get("windows"):
            continue
        print(f" {sid:>8} {row['audio_s']:>8} {row['voxlock_rtf']:>12} {row['combined_rtf']:>13}"
              f" {row['lag_p95_ms']:>11} {row['queue_max']:>10} {row['emitted']:>8}")


if __name__ == "__main__":
    main()

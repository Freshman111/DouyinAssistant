"""Replay a manifest of 16 kHz PCM recordings through KWS or offline ASR."""

import argparse
import json
from pathlib import Path
import statistics
import time
import unicodedata

import numpy as np
import sherpa_onnx


RATE = 16_000
TARGET = "跳过"
KWS_DIR = (
    Path(__file__).resolve().parents[1]
    / "biz-sherpa-onnx/src/main/assets/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
)


def create_engine(kind, model_dir, threads):
    if kind == "kws":
        suffix = "epoch-13-avg-2-chunk-16-left-64.onnx"
        return sherpa_onnx.KeywordSpotter(
            encoder=str(KWS_DIR / f"encoder-{suffix}"),
            decoder=str(KWS_DIR / f"decoder-{suffix}"),
            joiner=str(KWS_DIR / f"joiner-{suffix}"),
            tokens=str(KWS_DIR / "tokens.txt"),
            keywords_file=str(KWS_DIR / "keywords.txt"),
            num_threads=threads,
            max_active_paths=4,
            keywords_score=1.5,
            keywords_threshold=0.25,
            num_trailing_blanks=2,
            provider="cpu",
        )

    weights = str(model_dir / "model.int8.onnx")
    options = dict(tokens=str(model_dir / "tokens.txt"), num_threads=threads)
    if kind == "sensevoice":
        return sherpa_onnx.OfflineRecognizer.from_sense_voice(
            weights, language="zh", use_itn=False, **options
        )
    return sherpa_onnx.OfflineRecognizer.from_paraformer(weights, **options)


def run_kws(engine, signal, flush=False):
    stream = engine.create_stream()
    if flush:
        signal = np.pad(signal, (0, round(0.8 * RATE)))
    for start in range(0, len(signal), RATE // 10):
        frame = signal[start:start + RATE // 10]
        begin = time.perf_counter()
        stream.accept_waveform(RATE, frame)
        words = []
        while engine.is_ready(stream):
            engine.decode_stream(stream)
            word = engine.get_result(stream)
            if word:
                words.append(word)
                engine.reset_stream(stream)
        yield (start + len(frame)) / RATE, words, (time.perf_counter() - begin) * 1000


def run_asr(engine, signal):
    begin = time.perf_counter()
    stream = engine.create_stream()
    stream.accept_waveform(RATE, signal)
    engine.decode_stream(stream)
    return stream.result.text, (time.perf_counter() - begin) * 1000


def command(text):
    text = "".join(
        char for char in text
        if not char.isspace() and not unicodedata.category(char).startswith("P")
    )
    return TARGET if text == TARGET else None


def clip_rows(kind, engine, signal, trials, origin):
    for trial in trials:
        start = max(origin, trial["start_s"] - 0.32)
        end = min(origin + len(signal) / RATE, trial["end_s"] + 0.32)
        clip = signal[round((start - origin) * RATE):round((end - origin) * RATE)]
        if kind == "kws":
            blocks = list(run_kws(engine, clip, flush=True))
            words = [word for _, hits, _ in blocks for word in hits]
            text, elapsed = " / ".join(words), sum(ms for _, _, ms in blocks)
            match = TARGET if TARGET in words else None
        else:
            text, elapsed = run_asr(engine, clip)
            match = command(text)
        yield dict(trial, clip_start_s=start, clip_end_s=end, text=text,
                   inference_ms=elapsed, command=match, contains_target=TARGET in text)


def continuous_rows(kind, engine, signal, origin):
    if kind == "kws":
        for end, words, elapsed in run_kws(engine, signal):
            yield dict(start_s=round(origin + end - 0.1, 3), end_s=round(origin + end, 3),
                       text=" / ".join(words), inference_ms=elapsed,
                       command=TARGET if TARGET in words else None, contains_target=TARGET in words,
                       emitted=TARGET in words, duplicate=False)
        return

    window, step = 2 * RATE, RATE // 2
    last_match = float("-inf")
    for start in range(0, len(signal), step):
        end = min(start + window, len(signal))
        text, elapsed = run_asr(engine, signal[start:end])
        match = command(text)
        end_s = origin + end / RATE
        duplicate = bool(match and end_s - last_match <= 1.0)
        if match:
            last_match = end_s
        yield dict(start_s=origin + start / RATE, end_s=end_s, text=text, inference_ms=elapsed,
                   command=match, contains_target=TARGET in text,
                   emitted=bool(match and not duplicate), duplicate=duplicate)
        if end == len(signal):
            break


def summarize(kind, rows, clips, trials):
    events = [row for row in rows if row["emitted"]]
    matched = {}
    outside = []
    repeated = []
    for event in events:
        end = event["end_s"]
        eligible = [
            trial for trial in trials
            if trial["end_s"] - 0.15 <= end <= trial["end_s"] + (1.2 if kind == "kws" else 2.0)
            and (kind == "kws" or event["start_s"] <= trial["start_s"] + 0.15)
        ]
        if not eligible:
            outside.append(end)
            continue
        trial = min(eligible, key=lambda item: abs(end - item["end_s"]))
        key = trial["id"]
        if key in matched:
            repeated.append(end)
        else:
            matched[key] = dict(event_s=end, after_trial_end_s=round(end - trial["end_s"], 3))

    timings = sorted(row["inference_ms"] for row in rows)
    return dict(
        candidates=len(trials),
        clip_exact=sum(bool(row["command"]) for row in clips),
        clip_contains=sum(row["contains_target"] for row in clips),
        windows=len(rows),
        continuous_exact_windows=sum(bool(row["command"]) for row in rows),
        continuous_contains_windows=sum(row["contains_target"] for row in rows),
        emitted=len(events),
        suppressed_overlapping_windows=sum(row["duplicate"] for row in rows),
        matched_candidates=matched,
        outside_candidates_s=outside,
        repeated_candidate_events_s=repeated,
        median_inference_ms=round(statistics.median(timings), 2),
        p95_inference_ms=round(float(np.percentile(timings, 95)), 2),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("engine", choices=["kws", "sensevoice", "paraformer"])
    parser.add_argument("output", type=Path)
    parser.add_argument("--model-dir", type=Path)
    parser.add_argument("--threads", type=int, default=1)
    args = parser.parse_args()
    if args.engine != "kws" and args.model_dir is None:
        parser.error("--model-dir is required for offline ASR")
    args.output.mkdir(parents=True, exist_ok=True)
    init = time.perf_counter()
    engine = create_engine(args.engine, args.model_dir, args.threads)
    init_ms = round((time.perf_counter() - init) * 1000, 2)
    summary = {}
    for recording in json.loads(args.manifest.read_text()):
        origin = recording.get("start_s", 0)
        signal = np.fromfile(recording["pcm"], dtype="<i2", offset=round(origin * RATE) * 2,
                             count=round((recording["end_s"] - origin) * RATE)
                             if "end_s" in recording else -1)
        signal = signal.astype(np.float32) / 32768
        trials = recording.get("trials", [])
        name = recording["name"]
        clips = list(clip_rows(args.engine, engine, signal, trials, origin))
        windows = list(continuous_rows(args.engine, engine, signal, origin))
        for kind, rows in (("clips", clips), ("windows", windows)):
            with (args.output / f"{args.engine}-{name}-{kind}.jsonl").open("w") as out:
                for row in rows:
                    out.write(json.dumps(row, ensure_ascii=False) + "\n")
        summary[name] = summarize(args.engine, windows, clips, trials)
        print(args.engine, name, json.dumps(summary[name], ensure_ascii=False), flush=True)
    (args.output / f"{args.engine}-summary.json").write_text(
        json.dumps(dict(init_ms=init_ms, threads=args.threads, recordings=summary),
                   ensure_ascii=False, indent=2) + "\n"
    )


if __name__ == "__main__":
    main()

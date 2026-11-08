"""Replay candidate-only VoxLock + offline ASR over a fixed energy gate.

The gate decides which parts of a recording are processed. Every candidate span
is denoised independently, put back on the original timeline, and then read with
overlapping ASR windows. Use this to compare input gain and enrollment samples
without retraining or touching the phone build.

Example:

  PYTHONPATH=/path/to/sherpa-onnx-python \
    python3 tools/replay_gated.py recording.pcm \
      --gate gate.json --spans-key spans_b --marks marks.json \
      --ref phone-sample.wav --ref desktop-sample.wav --gains 1,2,4,8,16 \
      --model-dir models/paraformer --enroll-dir models/voxlock --out results/
"""
import argparse
import json
from pathlib import Path

import numpy as np
import sherpa_onnx

import replay_voice

RATE = replay_voice.RATE
WINDOW = 2 * RATE
STEP = RATE // 2
SILENCE = 1e-8


def load_gate(path, delta_db, floor_db, key):
    rows = json.loads(Path(path).read_text())
    row = next(item for item in rows if item["delta_db"] == delta_db and item["floor_db"] == floor_db)
    return [(start, end) for start, end in row[key]]


def load_marks(path, key):
    if path is None:
        return []
    return json.loads(Path(path).read_text())[key]


def denoise(audio, spans, ref, enroll_dir, gain):
    model = sherpa_onnx.OnlineSpeechDenoiser(sherpa_onnx.OnlineSpeechDenoiserConfig(
        model=sherpa_onnx.OfflineSpeechDenoiserModelConfig(
            voxlock=sherpa_onnx.OfflineSpeechDenoiserVoxLockModelConfig(
                enroll_model=str(Path(enroll_dir) / "enroll.onnx"),
                chunk_model=str(Path(enroll_dir) / "chunk_int8.onnx"),
                voice_sample=str(ref)),
            num_threads=1)))
    filtered = np.zeros_like(audio)
    for start_s, end_s in spans:
        begin, end = round(start_s * RATE), round(end_s * RATE)
        clip = audio[begin:end] * gain
        model.reset()
        pieces = [model(clip[i:i + RATE // 10], RATE).samples for i in range(0, len(clip), RATE // 10)]
        pieces.append(model.flush().samples)
        output = np.concatenate(pieces)
        assert len(output) == len(clip), (len(output), len(clip))
        filtered[begin:end] = output
    return filtered


def read(filtered, engine):
    rows, hits, last_end = [], [], 0
    for i in range(0, len(filtered) - WINDOW + 1, STEP):
        window = filtered[i:i + WINDOW]
        if np.mean(window ** 2) < SILENCE:
            continue
        text, elapsed = replay_voice.run_asr(engine, window)
        row = {"start_s": i / RATE, "end_s": (i + WINDOW) / RATE, "text": text, "inference_ms": elapsed}
        if replay_voice.command(text) and i >= last_end:
            hits.append(row)
            last_end = i + WINDOW
        rows.append(row)
    return rows, hits


def align(hits, marks):
    aligned, unmatched, duplicates = {}, [], []
    for hit in hits:
        matched = [mark for mark in marks if hit["start_s"] - .5 <= mark <= hit["end_s"] + .5]
        if not matched:
            unmatched.append(hit["start_s"])
            continue
        mark = min(matched, key=lambda value: abs(value - (hit["start_s"] + 1)))
        if mark in aligned:
            duplicates.append(hit["start_s"])
        else:
            aligned[mark] = hit["start_s"]
    return aligned, unmatched, duplicates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pcm", type=Path)
    parser.add_argument("--gate", type=Path, required=True)
    parser.add_argument("--spans-key", required=True)
    parser.add_argument("--delta-db", type=float, default=6)
    parser.add_argument("--floor-db", type=float, default=-50)
    parser.add_argument("--marks", type=Path)
    parser.add_argument("--marks-key", default="user_marks_s")
    parser.add_argument("--ref", type=Path, action="append", required=True)
    parser.add_argument("--gains", default="8")
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--enroll-dir", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    audio = np.fromfile(args.pcm, dtype="<i2").astype(np.float32) / 32768
    spans = load_gate(args.gate, args.delta_db, args.floor_db, args.spans_key)
    marks = load_marks(args.marks, args.marks_key)
    engine = replay_voice.create_engine("paraformer", args.model_dir, 1)
    gains = [float(value) for value in args.gains.split(",")]

    summary = []
    for ref in args.ref:
        for gain in gains:
            filtered = denoise(audio, spans, ref, args.enroll_dir, gain)
            rows, hits = read(filtered, engine)
            aligned, unmatched, duplicates = align(hits, marks)
            key = f"{args.pcm.stem}-{ref.stem}-g{gain:g}"
            (args.out / f"{key}.jsonl").write_text(
                "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows))
            summary.append(dict(
                case=args.pcm.stem, ref=str(ref), gain=gain, marks=len(marks),
                segments=len(spans), processed_s=round(sum(e - s for s, e in spans), 2),
                recording_s=round(len(audio) / RATE, 2), decoded_windows=len(rows),
                hits=len(hits), aligned=len(aligned),
                missed=[mark for mark in marks if mark not in aligned],
                unmatched=unmatched, duplicates=duplicates))
            print(json.dumps(summary[-1], ensure_ascii=False), flush=True)
    (args.out / f"{args.pcm.stem}-gated-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()

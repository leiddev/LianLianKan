#!/usr/bin/env python3
"""生成连连看所需的 5 段短音效（自制，16-bit PCM 单声道 WAV）。

SRS NFR-7.2 要求素材为自绘/自制或可商用开源，这里全部用正弦波合成，无版权风险。

用法：
    python tools/generate_sfx.py

产物：
    app/src/main/res/raw/sfx_*.wav   （确定性输出，可重复执行）

说明：脚本刻意只用标准库（wave + math + struct），不引入任何第三方依赖。
"""

from __future__ import annotations

import math
import struct
import wave
from dataclasses import dataclass
from pathlib import Path

SAMPLE_RATE = 22050
AMPLITUDE_LIMIT = 32000
FADE_IN_SECONDS = 0.005
FADE_OUT_SECONDS = 0.015

REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "raw"


@dataclass(frozen=True)
class Note:
    """一个音符段。

    Attributes:
        start_ms: 起始时间（毫秒）。
        duration_ms: 持续时间（毫秒）。
        frequency: 频率（Hz）。
        amplitude: 振幅（0..1）。
    """

    start_ms: int
    duration_ms: int
    frequency: float
    amplitude: float


# 五类事件各一段音效，音高设计刻意拉开，保证听感可区分（SRS FR-12.1）。
SOUNDS: dict[str, list[Note]] = {
    # 轻短：每次选牌都响，不能拖沓
    "sfx_select": [Note(0, 60, 880, 0.35)],
    # 干脆的上行两音
    "sfx_eliminate": [Note(0, 70, 660, 0.45), Note(60, 90, 990, 0.45)],
    # 双音拍频，低沉刺耳，明确的「不行」
    "sfx_error": [Note(0, 160, 220, 0.40), Note(0, 160, 233, 0.25)],
    # 上行三音，庆祝感
    "sfx_win": [Note(0, 110, 523, 0.40), Note(110, 110, 659, 0.40), Note(220, 180, 784, 0.40)],
    # 下行两音，低沉收尾
    "sfx_lose": [Note(0, 150, 392, 0.40), Note(150, 250, 262, 0.40)],
}


def total_duration_ms(notes: list[Note]) -> int:
    return max(note.start_ms + note.duration_ms for note in notes)


def envelope(offset_samples: int, note_samples: int) -> float:
    """音符内部的淡入淡出包络，避免起止爆音。"""
    attack = int(SAMPLE_RATE * FADE_IN_SECONDS)
    release = int(SAMPLE_RATE * FADE_OUT_SECONDS)

    value = 1.0
    if offset_samples < attack:
        value = offset_samples / attack
    remaining = note_samples - offset_samples
    if remaining < release:
        value = min(value, remaining / release)
    return value


def render(notes: list[Note]) -> bytes:
    """把音符序列渲染成 16-bit PCM 单声道采样数据。"""
    duration_ms = total_duration_ms(notes)
    total_samples = round(duration_ms * SAMPLE_RATE / 1000)

    samples = bytearray()
    for index in range(total_samples):
        millis = index * 1000.0 / SAMPLE_RATE
        value = 0.0

        for note in notes:
            if millis < note.start_ms or millis >= note.start_ms + note.duration_ms:
                continue

            offset = index - int(note.start_ms * SAMPLE_RATE / 1000)
            note_samples = int(note.duration_ms * SAMPLE_RATE / 1000)
            value += (
                note.amplitude
                * envelope(offset, note_samples)
                * math.sin(2 * math.pi * note.frequency * offset / SAMPLE_RATE)
            )

        clamped = max(-1.0, min(1.0, value))
        samples += struct.pack("<h", round(clamped * AMPLITUDE_LIMIT))

    return bytes(samples)


def write_wav(path: Path, notes: list[Note]) -> None:
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(render(notes))


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for name, notes in SOUNDS.items():
        path = OUT_DIR / f"{name}.wav"
        write_wav(path, notes)
        print(f"{path.name:<22} {total_duration_ms(notes):>5} ms  {path.stat().st_size:>7} bytes")


if __name__ == "__main__":
    main()

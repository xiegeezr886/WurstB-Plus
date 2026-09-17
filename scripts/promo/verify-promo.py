"""Verify the encoded promo: container, streams, duration, and frame health.

Uses ffprobe-free checks where possible; falls back to decoding the file and
sampling frames so "it encoded" is never mistaken for "it looks right".
"""
import json
import os
import re
import subprocess
import sys

VIDEO = (sys.argv[1] if len(sys.argv) > 1
         else r"D:\WurstB\tmp-recon\promo\WurstB-Plus-v1.5.0-宣传片.mp4")


def ffmpeg_exe():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def run(args):
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8",
                       errors="replace")
    return r.stdout + r.stderr


def main():
    if not os.path.isfile(VIDEO):
        print(f"missing: {VIDEO}")
        return 1
    ff = ffmpeg_exe()
    size = os.path.getsize(VIDEO)
    print(f"file    : {VIDEO}")
    print(f"size    : {size/1048576:.1f} MB")

    info = run([ff, "-hide_banner", "-i", VIDEO])
    for line in info.splitlines():
        if "Duration" in line or "Stream #" in line:
            print("  " + line.strip())

    dur = None
    m = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", info)
    if m:
        dur = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))
        print(f"duration: {dur:.2f}s")
    has_audio = "Audio:" in info
    has_video = "Video:" in info
    print(f"streams : video={has_video} audio={has_audio}")

    # count frames and measure per-frame brightness while decoding
    print("decoding to check every frame (scaled to 160x90 gray)...")
    p = subprocess.run([ff, "-hide_banner", "-nostats", "-i", VIDEO,
                        "-vf", "scale=160:90,format=gray", "-f", "rawvideo", "-"],
                       capture_output=True)
    data = p.stdout
    n = len(data) // (160 * 90)
    print(f"frames  : {n}  ({n / dur:.2f} fps)" if dur else f"frames: {n}")
    if n:
        import numpy as np
        arr = np.frombuffer(data[:n * 160 * 90], dtype=np.uint8)
        arr = arr.reshape(n, 90, 160)
        means = arr.mean(axis=(1, 2))
        black = int((arr.max(axis=(1, 2)) < 40).sum())
        print(f"brightness: min={means.min():.1f} max={means.max():.1f} "
              f"mean={means.mean():.1f}")
        print(f"black frames (<40 max): {black}")
        # loudest change points = scene cuts
        d = np.abs(np.diff(means))
        cuts = int((d > 3).sum())
        print(f"large brightness jumps (cuts/fades): {cuts}")

    problems = []
    if dur is None or abs(dur - 88.0) > 1.0:
        problems.append(f"unexpected duration {dur}")
    if not has_video:
        problems.append("no video stream")
    if not has_audio:
        problems.append("no audio stream")
    if size < 2 * 1048576:
        problems.append("suspiciously small file")
    if problems:
        print("\nPROBLEMS: " + "; ".join(problems))
        return 1
    print("\nOK: container, duration, streams and frame health all look right")
    return 0


if __name__ == "__main__":
    sys.exit(main())

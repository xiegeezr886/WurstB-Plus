#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""为 WurstB+ Plus v1.5 宣传片合成一条极简电子底乐（96 BPM，无采样）。"""
import sys, os, math
import numpy as np

sys.stdout.reconfigure(encoding='utf-8')

SR = 44100
DUR = 60.0
N = int(SR * DUR)
BPM = 96.0
BEAT = 60.0 / BPM
BAR = BEAT * 4


def note(freq, dur, amp=1.0, attack=0.01, decay=None, harmonics=(1.0, 0.35, 0.14, 0.06)):
    """加性合成的单音，谐波带自然滚降，避免混叠感。"""
    n = int(dur * SR)
    if n <= 0:
        return np.zeros(0)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    for h, a in enumerate(harmonics, start=1):
        if freq * h > SR / 2.2:
            break
        sig += a * np.sin(2 * math.pi * freq * h * t + 0.3 * h)
    env = np.ones(n)
    na = max(1, int(attack * SR))
    env[:na] = np.linspace(0, 1, na) ** 1.5
    if decay:
        nd = min(n, int(decay * SR))
        env[:nd] *= np.exp(-3.2 * np.arange(nd) / nd)
        if nd < n:
            env[nd:] = 0
    else:
        rel = min(n, int(0.35 * SR))
        env[-rel:] *= np.linspace(1, 0, rel) ** 2
    return sig * env * amp


def place(buf, sig, start):
    i = int(start * SR)
    if i >= len(buf) or len(sig) == 0:
        return
    m = min(len(sig), len(buf) - i)
    buf[i:i + m] += sig[:m]


def noise_swell(dur, amp=0.12, lo=300, hi=6000):
    """带通感的噪声上扫：用多段正弦叠加近似，避免滤镜依赖。"""
    n = int(dur * SR)
    t = np.arange(n) / SR
    parts = np.zeros(n)
    rng = np.random.default_rng(7)
    bands = 26
    for b in range(bands):
        f = lo * (hi / lo) ** (b / (bands - 1))
        env = np.exp(-((t / dur - 0.55 - 0.25 * (b / bands)) ** 2) / 0.045)
        parts += np.sin(2 * math.pi * f * t + rng.uniform(0, 6.28)) * env / bands
    shape = np.linspace(0, 1, n) ** 2.2
    return parts * shape * amp * 4


def kick(amp=0.5):
    n = int(0.34 * SR)
    t = np.arange(n) / SR
    f = 118 * np.exp(-t * 22) + 44
    ph = 2 * math.pi * np.cumsum(f) / SR
    env = np.exp(-t * 11.5)
    click = np.exp(-t * 220) * np.sin(2 * math.pi * 2400 * t) * 0.08
    return (np.sin(ph) * env + click) * amp


def hat(amp=0.06, dur=0.045):
    n = int(dur * SR)
    rng = np.random.default_rng(3)
    t = np.arange(n) / SR
    sig = rng.normal(0, 1, n)
    # 一阶差分近似高通
    sig = np.diff(sig, prepend=0.0)
    return sig * np.exp(-t * 90) * amp


def reverb(x, seconds=1.5, mix=0.22):
    n_ir = int(seconds * SR)
    rng = np.random.default_rng(11)
    ir = rng.normal(0, 1, n_ir).astype(np.float32)
    ir *= np.exp(-np.arange(n_ir) / (SR * 0.42))
    ir[: int(0.012 * SR)] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    L = len(x) + n_ir
    nfft = 1 << (L - 1).bit_length()
    wet = np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(ir, nfft), nfft)[: len(x)]
    return (1 - mix) * x + mix * wet * 2.0


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'bed.wav'
    L = np.zeros(N, dtype=np.float32)

    # 和弦：Am - F - C - G，每小节一个，低频铺底
    chords = [
        (110.00, [220.00, 261.63, 329.63]),   # Am
        (87.31, [174.61, 220.00, 261.63]),    # F
        (130.81, [196.00, 261.63, 329.63]),   # C
        (98.00, [196.00, 246.94, 293.66]),    # G
    ]
    bars = int(DUR / BAR) + 1
    for b in range(bars):
        t0 = b * BAR
        root, tones = chords[b % 4]
        # pad
        for f in tones:
            place(L, note(f, BAR * 1.06, amp=0.052, attack=0.45,
                          harmonics=(1.0, 0.28, 0.1, 0.04) if b % 2 else (1.0, 0.22, 0.06)),
                  t0)
            place(L, note(f * 1.004, BAR * 1.06, amp=0.03, attack=0.6,
                          harmonics=(1.0, 0.2)), t0)
        place(L, note(root, BAR * 1.05, amp=0.055, attack=0.3,
                      harmonics=(1.0, 0.5, 0.25, 0.12)), t0)

        # 琶音：八分音符，16 分网格里隔一个
        seq = [tones[0] * 2, tones[2] * 2, tones[1] * 2, tones[2] * 2.0,
               tones[0] * 4, tones[1] * 2, tones[2] * 2, tones[1] * 2]
        for k, f in enumerate(seq):
            start = t0 + k * BEAT / 2
            if start > DUR - 1.0:
                continue
            amp = 0.035 if b < 4 else 0.05
            place(L, note(f, 0.42, amp=amp, attack=0.004, decay=0.42,
                          harmonics=(1.0, 0.22, 0.08)), start)

        # 鼓组从第 4 小节进入
        if b >= 3:
            for beat in range(4):
                if beat in (0, 2) or b % 2 == 1 and beat == 3:
                    place(L, kick(0.34 if b < 8 else 0.42), t0 + beat * BEAT)
                place(L, hat(0.05 if beat % 2 else 0.07), t0 + beat * BEAT + BEAT / 2)

    # 转场噪声 swell
    for st in (7.9, 16.4, 23.4, 30.4, 39.2, 47.2, 53.4):
        place(L, noise_swell(1.6, amp=0.11), st)

    # 结尾重击 + 长音
    place(L, kick(0.55), 54.0)
    for f in (110.0, 220.0, 329.63, 440.0):
        place(L, note(f, 6.0, amp=0.05, attack=0.02, decay=None,
                      harmonics=(1.0, 0.3, 0.12, 0.05)), 54.0)

    # 母线：轻压缩 + 总淡入淡出 + 立体声微展宽
    L = reverb(L.astype(np.float32), 1.6, 0.25)
    L = np.tanh(L * 1.35) * 0.72
    fade_in = int(0.6 * SR)
    L[:fade_in] *= np.linspace(0, 1, fade_in) ** 1.5
    tail = int(2.0 * SR)
    L[-tail:] *= np.linspace(1, 0, tail) ** 1.6

    d = int(0.011 * SR)
    R = np.concatenate([np.zeros(d, dtype=np.float32), L[:-d]])
    stereo = np.stack([L * 0.98, R * 0.98], axis=1)
    peak = np.max(np.abs(stereo))
    if peak > 0:
        stereo *= 0.89 / peak
    pcm = (np.clip(stereo, -1, 1) * 32767).astype('<i2')
    import wave
    with wave.open(out, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print('wrote', out)


if __name__ == '__main__':
    main()

# Minimal FFmpeg for the video-background feature

A size-reduced, **LGPL-only, decode-only** build of FFmpeg for Windows x86_64,
plus a small JNI bridge (`vf_ffmpeg.dll`) that lets the mod decode
4K H.264 / HEVC / VP9 / AV1 wallpapers, optionally on the GPU via D3D11VA.

This exists because the pure-Java JCodec path decodes the reference 4K file at
**3.8 fps**, which is unusable. Through this shim the same file decodes at
**~190 fps in software**, so the bottleneck moves entirely off the decoder.

> **Status:** the natives **are wired into `BackgroundVideo`** and ship inside the
> mod jar. JCodec (the previous pure-Java decoder) has been removed entirely.
> Application-side behaviour — size policy, degrade paths, tests — is documented
> in [`../../docs/title-background.md`](../../docs/title-background.md) §6.

---

## 1. What is in this directory

| Path | What it is |
| --- | --- |
| `dll/` | The build products. 7 DLLs ship with the mod (**7,605,466 bytes = 7.25 MiB**) |
| `dll/vf_ffmpeg_test.exe` | Standalone C harness: measures decode fps without Minecraft. Dev tool, does **not** ship |
| `src/vf_ffmpeg_jni.c` | The JNI shim **and** the harness (one file, `-DVF_WITH_MAIN`) |
| `build-ffmpeg.sh` | Full reproducible build: FFmpeg + shim |
| `build-shim.sh` | Rebuilds just the shim against an existing FFmpeg install |
| `COPYING.LGPLv2.1`, `COPYING.LGPLv3`, `LICENSE.md` | FFmpeg's licence texts (see §6) |
| `COPYING.GPLv2`, `COPYING.GPLv3` | Shipped for completeness only — **no GPL component is enabled** |

Java side lives in the mod source tree:

- `src/main/java/net/wurstclient/background/FfmpegVideoDecoder.java`
- `src/main/java/net/wurstclient/background/FfmpegNatives.java`
- `src/main/java/net/wurstclient/background/FfmpegVideoDecoderTest.java`

For the mod to actually use these, the **7 DLLs** (not the `.exe`) are also copied
to `src/main/resources/assets/wurst/ffmpeg/` — that is where `FfmpegNatives`
extracts them from at runtime. **That copy exists** (7,605,466 bytes, plus the
LGPL texts renamed to valid lowercase resource paths:
`copying-lgplv2.1.txt` and `license-ffmpeg.txt`), and it is kept in sync by
`FfmpegNativesTest`.

---

## 2. Versions and toolchain

| Component | Version |
| --- | --- |
| FFmpeg | **7.1.1** (`ffmpeg-7.1.1.tar.xz`, from https://ffmpeg.org/releases/) |
| tarball SHA-256 | `733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1` (11,019,500 bytes) |
| MSYS2 | installer `20260611`, installed with `winget install MSYS2.MSYS2` to `C:\msys64` |
| Toolchain | MSYS2 **MINGW64**, gcc **16.2.0**, binutils 2.47, nasm **3.02**, GNU Make 4.4.1, pkgconf 3.0.7 |
| JNI headers | JDK 17 (`C:\Program Files\Java\jdk-17\include`) |
| Verified on | JDK 17.0.12 and JDK 25.0.4 |
| Target | Windows x86_64, `--target-os=mingw32`, 64-bit DLLs |

Install the toolchain (`winget` put MSYS2 in `C:\msys64`; it needs **no admin**
because the installer grants the invoking user ownership of that directory):

```bash
pacman -S --needed mingw-w64-x86_64-toolchain mingw-w64-x86_64-nasm \
                  make diffutils pkgconf
```

Two `pacman` traps hit during this build, both worth knowing:

- **Stale partial downloads** in `var/cache/pacman/pkg` make the whole
  transaction fail with `416 Requested Range Not Satisfiable` / `403` against
  *every* mirror. Clear the cache (`rm var/cache/pacman/pkg/*.part`) and retry.
- `--disable-download-timeout` can hang forever if a mirror silently drops the
  connection. Leave the default timeout on and retry instead.

Then **from a MINGW64 shell** (`C:\msys64\mingw64.exe` — the build script
refuses to run under plain MSYS or UCRT64, because `gcc`/`nasm` are on the
MINGW64 PATH):

```bash
cd native/ffmpeg
SCRATCH=/c/tmp/ffmpeg-build ./build-ffmpeg.sh
```

`SCRATCH` defaults to `${TMPDIR:-/tmp}/ffmpeg-build`. Keep it **outside the
repo** so the ~1 GB of build objects never shows up as untracked files.

---

## 3. The exact configure line

```bash
./configure --prefix=$PWD/out --arch=x86_64 --target-os=mingw32 \
  --enable-shared --disable-static --disable-everything \
  --enable-decoder=h264 --enable-decoder=hevc --enable-decoder=vp9 \
  --enable-decoder=av1 --enable-decoder=mpeg4 --enable-decoder=mjpeg \
  --enable-demuxer=mov --enable-demuxer=matroska --enable-demuxer=avi \
  --enable-demuxer=image2 --enable-demuxer=mjpeg \
  --enable-parser=h264 --enable-parser=hevc --enable-parser=vp9 \
  --enable-parser=av1 --enable-parser=mpeg4video --enable-parser=mjpeg \
  --enable-hwaccel=h264_d3d11va --enable-hwaccel=h264_d3d11va2 \
  --enable-hwaccel=h264_dxva2 \
  --enable-hwaccel=hevc_d3d11va --enable-hwaccel=hevc_d3d11va2 \
  --enable-hwaccel=hevc_dxva2 \
  --enable-hwaccel=vp9_d3d11va --enable-hwaccel=vp9_d3d11va2 \
  --enable-hwaccel=av1_d3d11va --enable-hwaccel=av1_d3d11va2 \
  --enable-dxva2 --enable-d3d11va \
  --enable-swscale --enable-protocol=file --enable-zlib \
  --disable-encoders --disable-muxers --disable-filters --disable-programs \
  --disable-doc --disable-network --disable-autodetect --disable-debug
```

`configure` reports `License: LGPL version 2.1 or later`.

### Why the non-obvious flags

- **`--disable-everything` also drops the parsers.** They must be re-enabled
  explicitly or H.264 in MP4 will not decode.
- **`av1` is not optional in 7.1.** `libavcodec/h2645_sei.c` references
  `ff_aom_uninit_film_grain_params`, so linking avcodec without the AV1 decoder
  fails with an undefined reference. We want AV1 anyway.
- **`*_d3d11va` and `*_d3d11va2` are different hwaccels with different pixel
  formats** (`AV_PIX_FMT_D3D11VA_VLD` vs `AV_PIX_FMT_D3D11`). Enabling only the
  first is a trap: the decoder then offers `D3D11`, no hwaccel matches it, the
  hwaccel ends up with no `alloc_frame`, and *every frame* fails with
  `get_buffer() failed`. Enable both; the shim prefers `D3D11`.
- **`--disable-autodetect`** stops configure from linking whatever else MSYS2
  happens to have (lzma, bz2, iconv, …), each of which would be one more DLL to
  ship.
- **`--enable-zlib`** *is* needed: Matroska/MP4 use it. It does pull in
  `zlib1.dll`; see §4.
- `--disable-network` means no sockets, no URL protocols — `file` only.
- `--disable-debug` keeps the DLLs small; `--disable-programs` removes
  `ffmpeg.exe`/`ffprobe.exe` entirely (we only need the libraries).

---

## 4. What ships, and how big it is

Measured sizes of `native/ffmpeg/dll/`:

| File | Bytes | Why |
| --- | --- | --- |
| `avcodec-61.dll` | 5,013,504 | the decoders |
| `avutil-59.dll` | 1,058,304 | core helpers |
| `swscale-8.dll` | 665,088 | YUV → RGBA conversion |
| `avformat-61.dll` | 587,264 | MP4 / MKV / AVI demuxing |
| `zlib1.dll` | 128,488 | avformat's only external library |
| `vf_ffmpeg.dll` | 86,485 | the JNI shim (ours) |
| `libwinpthread-1.dll` | 66,333 | avutil's threading runtime |
| **Total (7 DLLs)** | **7,605,466 (~7.25 MiB)** | |

`vf_ffmpeg_test.exe` (~98 KB) sits in the same directory but is a development
tool and **does not ship** — the mod only needs the 7 DLLs.

Two dependencies had to be shipped rather than linked away, so the payload is
**7 DLLs, not 5**:

- **`zlib1.dll`** — `--enable-zlib` is required for Matroska/MP4. An attempt to
  link it statically via `--pkg-config-flags=--static` plus
  `--extra-ldflags=-Wl,-Bstatic -lz -Wl,-Bdynamic` did **not** take effect
  (avformat still imported `zlib1.dll`), so the DLL ships instead.
- **`libwinpthread-1.dll`** — avutil's `w32threads` needs it.

Both are permissively licensed (zlib licence; winpthreads is MIT/BSD-style), so
neither affects the LGPL story in §6. If the extra files are unwelcome, the
clean fix is to build zlib as a static `.a` into a private prefix and point
`PKG_CONFIG_PATH` at it — not attempted here.

The DLL set was verified to need **nothing** beyond Windows system DLLs plus the
files above (`objdump -p` on each):

```
avcodec-61.dll   -> avutil-59.dll, KERNEL32, msvcrt, ole32
avformat-61.dll  -> avcodec-61.dll, avutil-59.dll, KERNEL32, msvcrt, zlib1.dll
avutil-59.dll    -> KERNEL32, msvcrt, bcrypt, USER32, libwinpthread-1.dll
swscale-8.dll    -> avutil-59.dll, KERNEL32, msvcrt
vf_ffmpeg.dll    -> avcodec-61, avformat-61, avutil-59, swscale-8, KERNEL32, msvcrt
```

**Load order matters, and the two runtime DLLs must be loaded first.**
Windows resolves a DLL's own imports from the process search path (application
directory, `System32`, `PATH`) — *not* from the directory that DLL lives in. So
loading `avutil-59.dll` by absolute path still fails with *"Can't find dependent
libraries"* unless `libwinpthread-1.dll` is already loaded, and
`avformat-61.dll` likewise needs `zlib1.dll` first. `FfmpegNatives.DLLS` encodes
exactly that order (`libwinpthread-1`, `zlib1`, `avutil-59`, `swscale-8`,
`avcodec-61`, `avformat-61`, `vf_ffmpeg`). This was verified on JDK 17 and
JDK 25.

---

## 5. Measured performance

Reference file (`run/wurst/backgrounds/ayaka-sakura-spirit/media.mp4`):
**3840×2160, H.264 High L5.1, yuv420p, 16 fps, 369 frames, 23.06 s, 39 Mbps.**

Harness: `dll/vf_ffmpeg_test.exe <file> 1 <hw>`. It decodes every frame and
converts to tightly packed RGBA, i.e. it measures the *same* work the mod does,
including the colour conversion — not just a `-f null` decode.

| Path | Output | Frames | Elapsed | **fps** |
| --- | --- | --- | --- | --- |
| JCodec (pure Java, previous state) | 1280×720 | — | 263 ms/frame | **3.8** |
| ffmpeg CLI `-f null` (reference, software) | — | 369 | 2.13 s | ~174 |
| **Shim, software** | 3840×2160 RGBA | 369 | 1.94–2.06 s | **184–190** |
| **Shim, software, scaled to 1280×720** | 1280×720 RGBA | 369 | 1.85 s | **199–205** |
| **Shim, hardware d3d11va** | 3840×2160 RGBA | 369 | 7.2 s | **50.3–51.4** |
| **Shim, hardware d3d11va, scaled to 1280×720** | 1280×720 RGBA | 369 | 3.28 s | **112.6–116.1** |

Both paths clear the ≥30 fps acceptance bar: software by >6×, hardware by ~1.7×.

The same numbers hold through the **JNI path** (Java → `vf_ffmpeg.dll` → libavcodec
→ libswscale → Java `byte[]`), so this is not a CLI-only result:
software 4K **179.3 fps**, software 720p **199.0 fps**, hardware 4K **51.4 fps**,
hardware 720p **112.6 fps**.

Software is faster than hardware here, which is expected and worth understanding:
the source is only 16 fps of easy-to-decode 4K H.264, so 16 CPU threads beat a
GPU→CPU readback of every frame. Hardware decode is kept because it offloads the
CPU (useful when the game is *also* running) and because it is the only way to
decode AV1 at all — see §5.1.

### Other codecs and containers (both paths)

Generated from the same reference clip, 24–30 frames each:

| File | Codec | Software | Hardware |
| --- | --- | --- | --- |
| `hd1080.mp4` | H.264 1080p | 593 fps | 164 fps |
| `small360.mkv` | H.264 in Matroska | 2912 fps | 541 fps |
| `hevc.mp4` | HEVC 720p | 1061 fps | 269 fps |
| `vp9.webm` | VP9 in WebM | 565 fps | 249 fps |
| `av1.mp4` | AV1 720p | **fails (see below)** | 243 fps |

MP4/MOV, Matroska/WebM and AVI containers all demux correctly, and `seekToStart`
works: a 2-pass run decoded 738 frames and reported 205 fps.

### 5.1 AV1 cannot be decoded in software by this build

AV1 works **only** on the hardware path. This is an upstream FFmpeg 7.1
limitation, not a configuration mistake: `libavcodec/av1dec.c` contains

```c
/* Since now the av1 decoder doesn't support native decode ... */
if (!avctx->hwaccel) {
    av_log(avctx, AV_LOG_ERROR, "Your platform doesn't support"
           " hardware accelerated AV1 decoding.\n");
    avctx->pix_fmt = AV_PIX_FMT_NONE;
    return AVERROR(ENOSYS);
}
```

There is no native software AV1 decoder in FFmpeg; software AV1 requires the
external **libdav1d**, which is a separate build dependency that was not added.
So an AV1 wallpaper needs a GPU with AV1 decode support, otherwise it is
rejected as `UNSUPPORTED_CODEC` ("this codec cannot be played by this build")
and the card in the picker names the codec. **H.264, HEVC and VP9 all have real
software decoders and are unaffected.**

### 5.2 Out in the mod (measured through the Java path)

The same reference file, decoded frame by frame through
`FfmpegVideoDecoder` and then written pixel by pixel into the `NativeImage` the
render thread uploads (i.e. exactly the work the mod does), 369 frames per run,
measured with the repository's own `FfmpegVideoDecoderTest` (Java path, not the
ffmpeg CLI):

| Output | Software | Hardware (d3d11va) | Java copy | End to end (sw / hw) |
| --- | ---: | ---: | ---: | ---: |
| 1280x720 | 196.7 fps | 113.3 fps | 1.3 / 0.7 ms | 157 / 105 fps |
| 1920x1080 | 137.1 fps | 88.6 fps | 2.3 / 1.7 ms | 104 / 77 fps |
| **2560x1440 (the ceiling the mod uses)** | **87.5 fps** | **65.9 fps** | **3.7 / 3.1 ms** | **66 / 55 fps** |
| 3840x2160 (native) | 224.8 fps | 53.5 fps | 8.5 / 7.1 ms | 77 / 39 fps |

Repeat runs on this machine vary by 5–10% (earlier runs saw 235 fps for software
720p and 95.5 fps for software 1440p); the table is one consistent run, and even
the worst run keeps more than 2x headroom over the mod's 30 fps cap. Every frame
reports exactly `width*height*4` bytes, alpha is 255 everywhere, all three
channels span 0..255, and software and hardware agree on the mean (R=137.4
G=94.1 B=105.5 at 1440p) — the frames are neither blank nor garbage.

Two counter-intuitive results worth keeping in mind:

- **Native 4K is *faster* than scaling down to 1440p** (252 vs 95.5 fps):
  `swscale` does a pure SIMD format conversion when the size matches, but as soon
  as it has to rescale, `SWS_FAST_BILINEAR` runs multi-tap filtering, which costs
  more than the conversion itself at non-integer ratios like 2160→1440.
- **Hardware decode gets linearly slower with output size** (GPU→CPU readback is
  per output pixel), which is why 4K hardware is only 53 fps.
- The claim "a larger upload size is nearly free" is therefore **false** as
  measured; the mod caps at 2560x1440 to keep ≥1.8x headroom over its 30 fps cap
  while staying 1:1 on QHD screens.

Hardware decode is optional, and the fallback is not theoretical: the tiny
320x240 baseline H.264 test fixture fails on D3D11VA with
`AVERROR_INVALIDDATA` (-1094995529) on its very first frame (precisely: the first
`avcodec_send_packet`, see §8.1) while decoding perfectly in software. Both the
probe and the player therefore retry in software
(`BackgroundVideo.decodeFirstFrame` / `recoverFromDecodeError`), and the test
suite covers that path.

### Pixel correctness

The harness samples every 97th pixel of every frame and reports per-channel
range and mean. For the 4K file:

- software, native: `R[0..255] G[0..255] B[0..255]`, mean `R=138.4 G=93.9 B=106.2`
- hardware, native: `R[0..255] G[0..255] B[0..255]`, mean `R=137.8 G=94.0 B=105.9`
- **both, scaled to 720p: mean `R=137.8 G=93.9 B=105.9` over exactly the same
  3,506,238 samples**

Full 0–255 range and a non-degenerate mean, consistent with the pink/anime
scene — frames are not blank, not all-black and not one flat colour. The two
independent decode paths agreeing to within 0.1 on the same sampled pixels is
the strongest evidence the RGBA conversion is right. Through the JNI path the
alpha channel additionally reads `A=255.0` everywhere, confirming a correctly
filled RGBA buffer rather than uninitialised memory.

---

## 6. Licence: LGPL-2.1-or-later, and what that obliges you to do

**This build is LGPL v2.1-or-later. No GPL and no nonfree component is
enabled.** `configure` prints `License: LGPL version 2.1 or later`, and
`build-ffmpeg.sh` fails the build if `CONFIG_GPL=yes` or `CONFIG_NONFREE=yes`
ever appears in `ffbuild/config.mak`. Verified from the configure line:

- `--enable-gpl` — **absent**
- `--enable-nonfree` — **absent**
- `--enable-version3` — **absent** (so it is LGPLv2.1, not v3 → you may convey
  it under LGPLv2.1-or-later)
- no `--enable-libx264`, `--enable-libx265`, or any other GPL external library
  (external libraries are limited to `zlib`, which is permissive)

Nothing here is GPL, so shipping these DLLs does **not** put the mod under the
GPL. The obligations that *do* apply, because FFmpeg is LGPL and we ship it as
**shared libraries** (which is the LGPL-friendly route):

1. **Ship the licence text.** Done, twice over: `COPYING.LGPLv2.1` and
   `LICENSE.md` are in this directory **and** copied next to the DLLs in
   `src/main/resources/assets/wurst/ffmpeg/` (renamed to
   `copying-lgplv2.1.txt` / `license-ffmpeg.txt` because Minecraft resource paths
   must be lowercase), so they travel inside the mod jar.
2. **Say that FFmpeg is used and that it is LGPL.** Put a line in the mod's
   credits/about and in the jar metadata.
3. **Provide the corresponding source.** Either ship the unmodified
   `ffmpeg-7.1.1.tar.xz`, or — better — ship this directory, which contains
   `build-ffmpeg.sh` plus the configure line that regenerates the exact
   binaries from the pristine upstream tarball. A written offer valid for three
   years is also acceptable, but the script is less work for everyone.
4. **Do not prevent replacement.** Because we link FFmpeg as DLLs and do not
   statically link or modify it, a user can drop in a rebuilt `avcodec-61.dll`
   and the mod will use it. Keep it that way — do not convert this to a
   statically linked `vf_ffmpeg.dll`, because that would trigger the LGPL's
   relinking requirement.
5. **Mark modifications.** `vf_ffmpeg_jni.c` is our own file, not a modified
   FFmpeg file, so there are no FFmpeg sources to mark. We patch nothing
   upstream.

`build-ffmpeg.sh` also checks the licence at build time:

```
=== licence guard ===
CONFIG_GPL=no
CONFIG_NONFREE=no
OK: no GPL, no nonfree.
```

### Caveats I am not a lawyer about

- The two extra runtime DLLs (`zlib1.dll`, `libwinpthread-1.dll`) are
  permissive but are **third-party** code; their notices should ideally travel
  with them. I did not add their licence texts.
- The **`zlib` option is compiled into avformat**, so avformat as shipped is
  LGPL code linked against a permissive library — fine, but if you ever link a
  GPL zlib replacement the analysis changes.
- `--enable-version3` being absent means the LGPLv2.1 applies; FFmpeg as a
  whole is LGPLv2.1-or-later, and some individual files are LGPLv3. Files under
  LGPLv3 are still LGPLv3 when compiled in, even without `--enable-version3`.
  If strict LGPLv2.1-only conformance matters, that needs a lawyer's read, not
  mine.

---

## 7. How it is wired up now

The natives are loaded by `net.wurstclient.background.FfmpegNatives`, which
mirrors the existing `SkikoNatives` approach: DLLs are shipped as plain mod
resources (deliberately **not** jarJar, which rewrites resource paths and would
break the DLLs' filename-based interdependencies) and extracted on first use to
`gameDir/ffmpeg/`.

What the mod does with them (all of it in
[`../../docs/title-background.md`](../../docs/title-background.md) §6):

1. The 7 DLLs live in `src/main/resources/assets/wurst/ffmpeg/` (plus
   `copying-lgplv2.1.txt` and `license-ffmpeg.txt`, the LGPL texts which the
   licence requires shipping).
2. `BackgroundVideo` decodes through `FfmpegVideoDecoder` and falls back from
   hardware to software both at open time and mid-stream.
3. `VideoPacing` still owns frame dropping — the decoder has no pacing of its own.
4. The upload size is chosen from the **drawn size** (the window's framebuffer),
   capped by the source resolution and by 2560x1440, and re-applied through
   `setOutputSize` when the window is resized.

For development without Minecraft, point the loader at a directory and run the
harness:

```
java -Dwurst.ffmpeg.dir=<abs path to native/ffmpeg/dll> -cp <classes> \
     net.wurstclient.background.FfmpegVideoDecoderTest <file> 1 1
```

Gradle's `test` task does exactly this, so `FfmpegVideoDecoderNativeTest` decodes
a real clip headlessly on every build.

---

## 8. JNI shim API

Java class: `net.wurstclient.background.FfmpegVideoDecoder`.
Every native symbol is named
`Java_net_wurstclient_background_FfmpegVideoDecoder_<method>`, so the class must
keep its **package and name** — renaming either breaks the binding.

| Java method (exact) | Returns |
| --- | --- |
| `static FfmpegVideoDecoder open(String path, boolean wantHardware)` | decoder, or `null` |
| `static FfmpegVideoDecoder openHardware(String path)` | same, tries D3D11VA first |
| `static FfmpegVideoDecoder openSoftware(String path)` | same, software only |
| `int setOutputSize(int outW, int outH)` | bytes/frame; `0/0` = native size; `<0` invalid |
| `int nextFrame()` | bytes written / `0` EOF / `<0` AVERROR |
| `int seekToStart()` | `0` ok / `<0` failed |
| `void close()` | idempotent |
| `boolean isOpen()` | handle still live |
| `int width()`, `int height()` | **native decoded** size |
| `int outputWidth()`, `int outputHeight()` | size actually written to the buffer |
| `int bufferSize()` | required buffer length incl. padding |
| `byte[] frameBuffer()` | reusable buffer, valid until the next call |
| `double frameRate()` | container fps, `0` if unknown |
| `long durationMs()` | `0` if unknown |
| `boolean isHardware()` | did hardware actually engage? |
| `static boolean isAvailable()` | natives loaded OK |
| `static String lastLibraryError()` | why loading failed, `""` if fine |
| `String lastError()` | why the **last `nextFrame` / `setOutputSize` / `seekToStart` on this instance** failed, `""` if none. Read it after a negative return |
| `static String lastOpenError()` | why the **last `open` on this thread** failed, `""` on success. Survives the handle, which is what makes it usable at open time |
| `static String ffmpegVersion()` | e.g. `"7.1.1"` |

### Conventions

- **Pixel format:** tightly packed 8-bit **RGBA, byte order R,G,B,A in memory**
  (`AV_PIX_FMT_RGBA`). `stride == width * 4`, rows **top-down**.
  That is exactly Minecraft's `NativeImage.Format.RGBA` byte order, so the
  buffer can go straight to `NativeImage` with no channel swizzle. It is *not*
  the packed `int` that `BufferedImage.getRGB()` returns (that is ARGB), which
  is why the JCodec path needs `argbToAbgr()` and this path does not.
- **`nextFrame` returns bytes written** (`outWidth*outHeight*4`), **`0` at end of
  stream** — looping is the caller's job, matching how `BackgroundVideo` already
  works — or a **negative FFmpeg `AVERROR`** on error. Because errors are
  negative and EOF is exactly `0`, the two are always distinguishable.
- **Buffer contract:** the Java buffer must be `outWidth*outHeight*4 + 64`
  bytes. The extra 64 (`AV_INPUT_BUFFER_PADDING_SIZE`) is not slack for the
  caller — it exists because `nextFrame` uses
  `GetPrimitiveArrayCritical`, which may hand libswscale the Java array itself,
  and the scaler is allowed to write with SIMD over-run. It must not run off the
  end. `FfmpegVideoDecoder` allocates this for you, growing the buffer if
  `setOutputSize` asks for a bigger one; `bufferSize()` reports it.
- **Hardware is strictly optional.** `openHardware()` falls back to software
  automatically if `av_hwdevice_ctx_create` fails; `isHardware()` says which
  path is live. If hardware fails mid-stream, `nextFrame` returns a negative
  error so the caller can reopen in software.
- **Threading:** not thread-safe, one handle per thread. The shim has no
  threads, queues or pacing of its own. Software decode uses libavcodec frame
  threading (all cores); hardware decode runs single-threaded because D3D11VA's
  frames context does not survive frame threading (see §9).
- **Decode resolution is caller-controlled** via `setOutputSize`. Default is the
  native size. Asking for 1280×720 makes `swscale` produce 720p directly, which
  collapses the per-frame buffer from **33 MB to 3.7 MB**. The mod asks for the
  window's framebuffer size instead, capped by the source resolution and by
  2560x1440 (`BackgroundVideo.chooseSize`) — see §5.2 for what each size costs.

### 8.1 Failure reasons (`lastError` / `lastOpenError`)

Every failure path in the shim records **which stage failed, the numeric
`AVERROR`, `av_strerror()`'s text for it, and which decode path was being
attempted**. Without that, an in-game failure is undiagnosable: the mod's only
evidence is one log line,

```
[Background] 视频背景 <id> 不能播放：DECODE_FAILED / avc1 / <reason>
```

and a missing `<reason>` ("`... avc1 / `") says nothing at all. The format is
fixed so it can be grepped and asserted on:

```
<stage>: AVERROR <n> (<av_strerror text>) path=<hardware|software>
         hw=<device-created|device-unavailable|not-attempted-yet>
         [file=<path>] [<stage-specific context>] [<note>]
```

`hw=` has three states on purpose: hardware can be requested and *not yet
attempted* (the file could not even be opened), requested and *unavailable*
(`av_hwdevice_ctx_create` failed, so software is a fallback, and the note spells
out why), or *created* (hardware really is live). Those diagnose differently, so
they must not be conflated.

Stages covered: `avformat_open_input`, `avformat_find_stream_info`,
`av_find_best_stream(video)`, `avcodec_alloc_context3`,
`avcodec_parameters_to_context`, `av_hwdevice_ctx_create(d3d11va)` (note, not
fatal), `get_hw_format`, `get_sw_format`, `avcodec_open2`, frame-size validation,
`av_frame_alloc/av_packet_alloc`, `setOutputSize`, `sws_getContext`,
`frame_to_rgba`, `first/explicit av_read_frame`, `avcodec_send_packet`,
`avcodec_receive_frame`, `av_hwframe_transfer_data`, `av_seek_frame`,
`nativeNextFrame` (buffer size), and the Java-side native load
(`FfmpegVideoDecoder.lastLibraryError()`).

Real strings, measured (harness, this machine):

```
# missing file, software
avformat_open_input: AVERROR -2 (No such file or directory) path=software
hw=not-requested file=...\missing.mp4

# garbage bytes named .mp4, hardware requested
avformat_open_input: AVERROR -1094995529 (Invalid data found when processing
input) path=hardware(requested) hw=not-attempted-yet file=...\garbage.mp4

# 320x240 H.264 fixture on D3D11VA: the device is created, the first packet dies
first avcodec_send_packet: AVERROR -1094995529 (Invalid data found when
processing input) path=hardware hw=device-created codec=h264 frame=320x240
already_out=0

# absurd upscale request, software
setOutputSize: AVERROR -22 (Invalid argument) path=software hw=not-requested
refusing an absurd upscale: requested=100000x100000 source=3840x2160
```

`nativeLastOpenError()` (static, no handle) holds the open-stage reason so it
outlives a handle that never opened; `nativeLastError(handle)` holds the
per-handle reason. The Java wrapper captures the open-stage one into a
`ThreadLocal` — the picker probes several wallpapers concurrently, and a shared
field would let one file's success erase another file's reason, which is exactly
the bug this channel exists to fix.

---

## 9. Things worth knowing if you touch this

Four non-obvious things cost real debugging time; all four are fixed in the
current code, and they are listed here so nobody re-introduces them.

1. **`get_format` is not optional.** With plain libavcodec defaults, the H.264
   decoder initialises the D3D11VA hwaccel even when the caller never created a
   device context, and then calls a NULL callback inside
   `avcodec_get_hw_frames_parameters` → SIGSEGV inside `avcodec_send_packet`.
   The shim therefore always installs an explicit `get_format`: `get_sw_format`
   (refuses every `AV_PIX_FMT_FLAG_HWACCEL` format) when software was requested,
   and `get_hw_format` when a device really exists.
2. **`hw_device_ctx` ownership is a transfer, not a borrow.** After
   `avcodec_open2()`, the codec context owns the reference from
   `av_hwdevice_ctx_create()`; unrefing it again is a double free. It surfaced
   as `STATUS_HEAP_CORRUPTION` inside `avcodec_free_context`, several calls away
   from the mistake, and it also made *hardware decode itself* fail — the
   corruption hit `get_buffer()` before it hit the teardown. Measured evidence:
   refcount was 1 right after `avcodec_open2` and 0 at teardown. The device is
   therefore not stored in `VFHandle` at all; `avcodec_free_context()` releases
   it.
3. **`*_d3d11va` ≠ `*_d3d11va2`.** They are different hwaccels with different
   pixel formats (`AV_PIX_FMT_D3D11VA_VLD` vs `AV_PIX_FMT_D3D11`), declared
   separately in `dxva2_*.c`. Enable both (as `build-ffmpeg.sh` does) and have
   `get_hw_format` prefer `D3D11`. Enabling only `*_d3d11va` while asking for
   `D3D11` yields a hwaccel with no `alloc_frame`, so every frame fails with
   `get_buffer() failed`.
4. **Hardware decode runs single-threaded on purpose.** The D3D11VA hwaccel
   keeps its frames context on the decoder context, and under
   `FF_THREAD_FRAME` the per-thread copies do not get a usable one. Software
   decode keeps libavcodec frame threading (all cores) — that is what makes 4K
   fast. `VF_THREADS=<n>` overrides the software thread count for experiments.

Also: libavcodec 7.1 revises the real frame size during decode (`Reinit context
to 3840x2160`), so `width`/`height` are read from the decoder after
`avcodec_open2`, and the swscale context is rebuilt whenever the source
format/size changes.

---

## 10. Reproducing the check

The C harness lives next to the DLLs (it needs them on the search path):

```bash
cd native/ffmpeg/dll
./vf_ffmpeg_test.exe "$VAP/run/wurst/backgrounds/ayaka-sakura-spirit/media.mp4" 1 0        # software, native 4K
./vf_ffmpeg_test.exe "$VAP/run/wurst/backgrounds/ayaka-sakura-spirit/media.mp4" 1 1        # hardware, native 4K
./vf_ffmpeg_test.exe "$VAP/run/wurst/backgrounds/ayaka-sakura-spirit/media.mp4" 1 0 1280 720  # software, 720p
./vf_ffmpeg_test.exe "$VAP/run/wurst/backgrounds/ayaka-sakura-spirit/media.mp4" 2 0 1280 720  # 2 passes (seekToStart)
```

Arguments: `<file> [loops] [hardware] [outW outH]`. `hardware=1` tries D3D11VA
and falls back to software. `loops>1` exercises `seekToStart` between passes.
Set `VF_DEBUG=1` for libav* logging and teardown tracing on stderr.

Failures print their reason (§8.1) on stderr, so the harness is also how the
error channel is checked without a game:

```bash
./vf_ffmpeg_test.exe fixture-colours.mp4 1 1 320 240   # hw failure, frame one
# decode failed: first avcodec_send_packet: AVERROR -1094995529 (Invalid data
# found when processing input) path=hardware hw=device-created codec=h264
# frame=320x240 already_out=0

./vf_ffmpeg_test.exe missing.mp4 1 0                    # open failure
# open failed: avformat_open_input: AVERROR -2 (No such file or directory)
# path=software hw=not-requested file=...\missing.mp4
```

The same thing through the JNI path, without Minecraft, on JDK 17:

```bash
javac --release 17 -d /tmp/jc \
  src/main/java/net/wurstclient/background/FfmpegVideoDecoder.java \
  src/main/java/net/wurstclient/background/FfmpegNatives.java
java -Dwurst.ffmpeg.dir=$PWD/native/ffmpeg/dll -cp /tmp/jc \
  net.wurstclient.background.FfmpegVideoDecoderTest <file> 1 1
```

(`FfmpegVideoDecoderTest` lives in the mod tree next to `BackgroundVideo` and
uses `wurst.ffmpeg.dir` to bypass Minecraft's resource manager; compile it with
the rest of the mod's classpath, or use the C harness above, which needs no
JVM at all.)

Expected output for the reference file, software, native size:

```
ffmpeg 7.1.1
decoded   : 3840x2160
output    : 3840x2160 RGBA (33177600 bytes/frame)
fps (meta): 16.000
duration  : 23062 ms
hardware  : software (no hw)
frames    : 369
elapsed   : ~2.0 s
decode fps: ~180-190
channels  : R[0..255] G[0..255] B[0..255]
mean      : R=138.4 G=93.9 B=106.2 over 31553190 samples
```

---

## 11. What was NOT verified

Being explicit about the limits of the evidence above:

- **No Minecraft session has been run.** Everything above, and everything in
  `docs/title-background.md` §6, comes from headless runs (the C harness, the
  Java harness, and JUnit). Nothing has drawn a decoded frame on screen: the
  title screen's look, the motion overlay, a resource reload, the teardown when
  switching wallpapers, the texture swap on a window resize and the CPU cost
  while playing are all **unverified in game**.
- **The failure reasons in §8.1 were verified headlessly** (harness + JUnit),
  not inside a game process. The stage names, `AVERROR` values and
  `av_strerror()` texts come from an FFmpeg 7.1.1 build on this machine; a
  different build could word the same error differently. What the mod does with
  the string — put it in `Probe.detail` and log it — is covered by
  `BackgroundVideoTest.failedProbeCarriesTheNativeReason`.
- **Row order and channel order are settled, though.** The same mp4 was decoded
  with the old JCodec path and with this shim and compared pixel by pixel:
  straight comparison differs by 2.6 on average (summed over the three
  channels), while a vertical flip is 450.8, a horizontal flip 461.6 and an R/B
  swap 217.7. A four-quadrant fixture (red / green / blue / white) is asserted
  per quadrant in `BackgroundVideoTest`. So the shim's output goes into
  `NativeImage` **without** a flip or a channel swap.
- **Only this one machine's GPU was tested.** D3D11VA was verified on this box.
  Other drivers/GPUs may fail `av_hwdevice_ctx_create` (which falls back to
  software cleanly) or may fail mid-stream (which returns a negative error, and
  the mod reopens in software). One measured example of the latter: the 320x240
  baseline H.264 test fixture fails on D3D11VA with `AVERROR_INVALIDDATA` on
  frame one and decodes fine in software. AV1 in particular needs a GPU with AV1
  decode support.
- **HEVC/VP9/AV1 were tested with tiny re-encodes** of the reference clip
  (24–30 frames, 720p), not with real 4K files of those codecs. The 4K
  measurement is H.264 only.
- **10-bit / HDR content was not tested.** The reference clip is 8-bit
  `yuv420p`. The conversion path (`swscale` → `AV_PIX_FMT_RGBA`) is 8-bit;
  10-bit sources will go through a lossy downconversion that has not been
  checked for correctness or colour shifts.
- **Rotated (phone) videos are no longer rotated.** The mov demuxer exposes the
  display matrix as side data, but this shim does not apply it and the build has
  no filters, so a 90°/270° clip now shows sideways. The JCodec path this
  replaced did rotate. See `docs/title-background.md` §7.
- **AVI demuxing is enabled but was not exercised** (no suitable test file was
  produced).
- **No soak/leak testing.** The loop test ran two passes; there is no
  hours-long stability run, and no measurement of whether repeated
  open/close cycles leak. The mod's own buffer pool does get churn (a window
  resize swaps all three `NativeImage`s), but that path is only covered by a
  unit test, not by hours of gameplay.
- **Licence texts for `zlib1.dll` and `libwinpthread-1.dll` are not bundled.**
  See the caveats in §6.

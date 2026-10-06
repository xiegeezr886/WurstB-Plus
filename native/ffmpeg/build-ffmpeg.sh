#!/usr/bin/env bash
#
# build-ffmpeg.sh - build a size-reduced *shared* LGPL FFmpeg for Windows x86_64
# plus the JNI shim used by the Wurst "video background" feature.
#
# Run this from an MSYS2 **MINGW64** shell (not MSYS, not UCRT64):
#
#     C:\msys64\mingw64.exe
#     cd /c/Users/<you>/Documents/trae_projects/VAP/native/ffmpeg
#     ./build-ffmpeg.sh
#
# Prerequisites (see README.md for the exact pacman command):
#     mingw-w64-x86_64-toolchain mingw-w64-x86_64-nasm make diffutils pkgconf
#
# Output (staged next to this script in native/ffmpeg/dll/):
#     avutil-59.dll  swscale-8.dll  avcodec-61.dll  avformat-61.dll
#     vf_ffmpeg.dll                     <- the JNI shim
#     vf_ffmpeg_test.exe                <- standalone C fps harness
#
# LICENCE: this build is LGPL-2.1-or-later ONLY. Do NOT add --enable-gpl or
# --enable-nonfree to CONFIGURE_FLAGS; the whole point of this exercise is that
# the result can ship with the mod. See README.md.
#
set -euo pipefail

FFMPEG_VERSION="${FFMPEG_VERSION:-7.1.1}"
FFMPEG_TARBALL="ffmpeg-${FFMPEG_VERSION}.tar.xz"
FFMPEG_URL="https://ffmpeg.org/releases/${FFMPEG_TARBALL}"
# ffmpeg-7.1.1.tar.xz, verified 2025 by size against the release directory
# listing and by a full re-download. Update deliberately, not silently.
#
# Scratch space: kept OUTSIDE the git repo so the 100+ MB of objects never show
# up as untracked files.
SCRATCH="${SCRATCH:-${TMPDIR:-/tmp}/ffmpeg-build}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DLL="$HERE/dll"
SRC="$SCRATCH/ffmpeg-${FFMPEG_VERSION}"
PREFIX="$SCRATCH/out-${FFMPEG_VERSION}"

# ---------------------------------------------------------------------------
# 0. sanity: are we actually in a MinGW-w64 shell?
# ---------------------------------------------------------------------------
case "${MSYSTEM:-}" in
MINGW64) ;;
*)
	echo "ERROR: MSYSTEM='${MSYSTEM:-unset}', expected MINGW64." >&2
	echo "       Start C:\\msys64\\mingw64.exe and run this script there." >&2
	exit 1
	;;
esac

for tool in gcc nasm make; do
	command -v "$tool" >/dev/null 2>&1 || {
		echo "ERROR: '$tool' not found on PATH. Install the toolchain first:" >&2
		echo "  pacman -S --needed mingw-w64-x86_64-toolchain mingw-w64-x86_64-nasm make diffutils pkgconf" >&2
		exit 1
	}
done

echo "=== toolchain ==="
gcc --version | head -1
nasm --version
make --version | head -1
echo "MSYSTEM=$MSYSTEM"

# ---------------------------------------------------------------------------
# 1. fetch + unpack
# ---------------------------------------------------------------------------
mkdir -p "$SCRATCH"
cd "$SCRATCH"

if [ ! -f "$FFMPEG_TARBALL" ]; then
	echo "=== downloading $FFMPEG_URL ==="
	# -C - resumes a partial download; ffmpeg.org does throttle. -sS keeps the
	# progress meter out of the log; errors still surface on stderr.
	curl -sS -L --retry 5 --retry-all-errors -C - -o "$FFMPEG_TARBALL" \
		"$FFMPEG_URL"
fi
echo "=== tarball: $(stat -c '%s' "$FFMPEG_TARBALL") bytes ==="

if [ ! -d "$SRC" ]; then
	echo "=== unpacking ==="
	tar -xf "$FFMPEG_TARBALL"
fi

# ---------------------------------------------------------------------------
# 2. configure
# ---------------------------------------------------------------------------
#
# Why these flags:
#   --disable-everything + --enable-<thing>  -> only what the wallpaper feature
#       needs. This is what takes the DLLs from ~90 MB down to the numbers in
#       README.md. Note that --disable-everything also drops the *parsers*, so
#       they must be re-enabled explicitly or H.264 in MP4 will not decode.
#   --enable-shared --disable-static         -> DLLs, so the mod ships 4 small
#       files instead of one 200 MB exe. Also keeps FFmpeg under the LGPL
#       "shared library" route rather than requiring static-link relinking.
#   --enable-hwaccel=*_d3d11va               -> hardware decode hooks.
#   --enable-dxva2 --enable-d3d11va          -> the hwcontext implementations.
#   --disable-autodetect                     -> do not silently link zlib/bzlib/
#       lzma/iconv just because MSYS2 happens to have them; a zlib dependency
#       would add an extra DLL that has to ship next to ours.
#   --enable-zlib                            -> ...but Matroska/MP4 do want it,
#       and Windows has no zlib DLL to fall back on, so it is statically linked
#       by default here (mingw-w64-zlib is a static lib for our purposes).
#   --disable-network                        -> no sockets/protocols at all.
#   --disable-{encoders,muxers,filters,programs,doc} -> not needed.
#
# Deliberately ABSENT: --enable-gpl, --enable-nonfree, --enable-version3.
#
CONFIGURE_FLAGS=(
	--prefix="$PREFIX"
	--arch=x86_64
	--target-os=mingw32
	--enable-shared
	--disable-static
	--disable-everything

	# decoders
	--enable-decoder=h264
	--enable-decoder=hevc
	--enable-decoder=vp9
	--enable-decoder=av1
	--enable-decoder=mpeg4
	--enable-decoder=mjpeg

	# demuxers (mp4/mov share the 'mov' demuxer; mkv/webm use matroska)
	--enable-demuxer=mov
	--enable-demuxer=matroska
	--enable-demuxer=avi
	--enable-demuxer=image2
	--enable-demuxer=mjpeg

	# parsers - required for low-delay frame output
	--enable-parser=h264
	--enable-parser=hevc
	--enable-parser=vp9
	--enable-parser=av1
	--enable-parser=mpeg4video
	--enable-parser=mjpeg

	# hardware decode
	#
	# NOTE: *_d3d11va and *_d3d11va2 are different hwaccels with different
	# pixel formats (D3D11VA_VLD vs D3D11). The shim prefers D3D11 but falls
	# back, so enable both sets. Enabling only the first is the classic trap:
	# you get a hwaccel that never allocates a frame.
	--enable-hwaccel=h264_d3d11va
	--enable-hwaccel=h264_d3d11va2
	--enable-hwaccel=h264_dxva2
	--enable-hwaccel=hevc_d3d11va
	--enable-hwaccel=hevc_d3d11va2
	--enable-hwaccel=hevc_dxva2
	--enable-hwaccel=vp9_d3d11va
	--enable-hwaccel=vp9_d3d11va2
	--enable-hwaccel=av1_d3d11va
	--enable-hwaccel=av1_d3d11va2
	--enable-dxva2
	--enable-d3d11va

	# libraries / misc
	--enable-swscale
	--enable-protocol=file
	--enable-zlib
	--disable-encoders
	--disable-muxers
	--disable-filters
	--disable-programs
	--disable-doc
	--disable-network
	--disable-autodetect
	--disable-debug

	# Make the DLLs self-contained: pull zlib and libwinpthread in
	# *statically*. Without this the payload needs zlib1.dll (or whatever
	# avformat was built against) and libwinpthread-1.dll next to it, i.e.
	# four more files for the user to keep together. Linked statically they
	# add ~200 KB to avformat/avutil instead and the DLL set stays at 4+1.
	#
	# -Wl,-Bstatic must be undone with -Wl,-Bdynamic *after* the libraries,
	# otherwise libavformat itself would be emitted as a static archive and
	# --enable-shared would produce nothing.
	--pkg-config-flags=--static
	--extra-ldflags=-static-libgcc\ -Wl,-Bstatic\ -lz\ -lwinpthread\ -Wl,-Bdynamic
)

cd "$SRC"
echo "=== configure ==="
./configure "${CONFIGURE_FLAGS[@]}" 2>&1 | tee "$SCRATCH/configure.log"

echo "=== licence guard ==="
# Fail loudly if a GPL/nonfree component ever creeps in. config.mak is the
# authoritative record of what configure actually enabled.
grep -E '^CONFIG_(GPL|NONFREE|VERSION3)=' ffbuild/config.mak || true
if grep -qE '^CONFIG_GPL=yes|^CONFIG_NONFREE=yes' ffbuild/config.mak; then
	echo "ERROR: GPL or nonfree component enabled. Refusing to build." >&2
	echo "       Did someone add --enable-gpl / --enable-nonfree?" >&2
	exit 1
fi
echo "OK: no GPL, no nonfree."

echo "=== decoder/demuxer/parser actually enabled ==="
grep -E '^CONFIG_(H264|HEVC|VP9|AV1|MPEG4)_DECODER=' ffbuild/config.mak || true
grep -E '^CONFIG_(MOV|MATROSKA|AVI)_DEMUXER=' ffbuild/config.mak || true
grep -E '^CONFIG_(H264_D3D11VA|HEVC_D3D11VA|VP9_D3D11VA|AV1_D3D11VA)_HWACCEL=' ffbuild/config.mak || true

# ---------------------------------------------------------------------------
# 3. build + install
# ---------------------------------------------------------------------------
echo "=== build (this takes a few minutes) ==="
make -j"$(nproc)"
make install

echo "=== produced DLLs ==="
ls -l "$PREFIX"/bin/*.dll

# ---------------------------------------------------------------------------
# 4. JNI shim + standalone test harness
# ---------------------------------------------------------------------------
SHIM_SRC="$HERE/src/vf_ffmpeg_jni.c"
echo "=== building JNI shim ==="

# JNIEXPORT/JNICALL come from jni.h, which lives in the JDK, not in MSYS2.
# Resolve it from JAVA_HOME so this stays reproducible.
if [ -n "${JAVA_HOME:-}" ] && [ -f "$JAVA_HOME/include/jni.h" ]; then
	JNI_INC="$JAVA_HOME/include"
elif [ -f "/c/Program Files/Java/jdk-17/include/jni.h" ]; then
	JNI_INC="/c/Program Files/Java/jdk-17/include"
else
	echo "ERROR: cannot find jni.h. Set JAVA_HOME to a JDK." >&2
	exit 1
fi
echo "jni.h from: $JNI_INC"
# The two headers below are a MSYS2 <-> Windows path bridge (cpp -I wants the
# native form, and the JDK ships win32/ under include/).
JNI_MD="$JNI_INC/win32"

CFLAGS_COMMON=(
	-O2
	-fno-strict-aliasing
	-I"$PREFIX/include"
	-I"$JNI_INC"
	-I"$JNI_MD"
)

mkdir -p "$OUT_DLL"
cp "$PREFIX"/bin/avutil-*.dll "$PREFIX"/bin/swscale-*.dll \
	"$PREFIX"/bin/avcodec-*.dll "$PREFIX"/bin/avformat-*.dll "$OUT_DLL/"

gcc "${CFLAGS_COMMON[@]}" -shared -o "$OUT_DLL/vf_ffmpeg.dll" "$SHIM_SRC" \
	-L"$PREFIX/lib" -lavformat -lavcodec -lswscale -lavutil

gcc "${CFLAGS_COMMON[@]}" -DVF_WITH_MAIN -o "$OUT_DLL/vf_ffmpeg_test.exe" \
	"$SHIM_SRC" -L"$PREFIX/lib" -lavformat -lavcodec -lswscale -lavutil

echo "=== staged into $OUT_DLL ==="
ls -l "$OUT_DLL"
echo "=== total bytes ==="
du -cb "$OUT_DLL"/*.dll | tail -1

echo
echo "Done. DLLs in $OUT_DLL"
echo "Next: copy them to src/main/resources/assets/wurst/ffmpeg/ (see README.md)."

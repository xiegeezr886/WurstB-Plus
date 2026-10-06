#!/usr/bin/env bash
#
# build-shim.sh - compile just the JNI shim (and the standalone C harness)
# against an FFmpeg that build-ffmpeg.sh has already installed.
#
# This exists so you can iterate on vf_ffmpeg_jni.c without paying for a full
# FFmpeg rebuild. Run from an MSYS2 MINGW64 shell:
#
#     C:\msys64\mingw64.exe
#     cd /c/Users/<you>/Documents/trae_projects/VAP/native/ffmpeg
#     ./build-shim.sh
#
set -euo pipefail

FFMPEG_VERSION="${FFMPEG_VERSION:-7.1.1}"
SCRATCH="${SCRATCH:-${TMPDIR:-/tmp}/ffmpeg-build}"
PREFIX="${PREFIX:-$SCRATCH/out-${FFMPEG_VERSION}}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHIM_SRC="$HERE/src/vf_ffmpeg_jni.c"
OUT_DLL="$HERE/dll"

case "${MSYSTEM:-}" in
MINGW64) ;;
*)
	echo "ERROR: MSYSTEM='${MSYSTEM:-unset}', expected MINGW64." >&2
	echo "       Start C:\\msys64\\mingw64.exe and run this script there." >&2
	exit 1
	;;
esac

if [ ! -d "$PREFIX/include" ]; then
	echo "ERROR: no FFmpeg install at $PREFIX" >&2
	echo "       Run ./build-ffmpeg.sh first." >&2
	exit 1
fi

# JNIEXPORT/JNICALL live in the JDK's jni.h, not in MSYS2.
if [ -n "${JAVA_HOME:-}" ] && [ -f "$JAVA_HOME/include/jni.h" ]; then
	JNI_INC="$JAVA_HOME/include"
elif [ -f "/c/Program Files/Java/jdk-17/include/jni.h" ]; then
	JNI_INC="/c/Program Files/Java/jdk-17/include"
else
	echo "ERROR: cannot find jni.h. Set JAVA_HOME to a JDK." >&2
	exit 1
fi

CFLAGS_COMMON=(
	-O2
	-fno-strict-aliasing
	-Wall
	-I"$PREFIX/include"
	-I"$JNI_INC"
	-I"$JNI_INC/win32"
)

LIBS=(-L"$PREFIX/lib" -lavformat -lavcodec -lswscale -lavutil)

mkdir -p "$OUT_DLL"

echo "=== JNI shim -> vf_ffmpeg.dll ==="
gcc "${CFLAGS_COMMON[@]}" -shared -o "$OUT_DLL/vf_ffmpeg.dll" "$SHIM_SRC" \
	"${LIBS[@]}"

echo "=== standalone harness -> vf_ffmpeg_test.exe ==="
gcc "${CFLAGS_COMMON[@]}" -DVF_WITH_MAIN -o "$OUT_DLL/vf_ffmpeg_test.exe" \
	"$SHIM_SRC" "${LIBS[@]}"

echo "=== staged into $OUT_DLL ==="
ls -l "$OUT_DLL"
echo "=== total DLL bytes ==="
du -cb "$OUT_DLL"/*.dll | tail -1

/*
 * vf_ffmpeg_jni.c - minimal JNI bridge from Minecraft (Wurst "video background")
 * to a size-reduced LGPL FFmpeg shared build.
 *
 * Design rules (see native/ffmpeg/README.md):
 *   - Decode only. No encoding, no muxing, no filters, no network.
 *   - One frame in, one tightly packed RGBA buffer out. No threads, no queues,
 *     no pacing: the caller (Java) owns timing and looping. This mirrors the
 *     existing JCodec path, where BackgroundVideo owns a worker thread and
 *     VideoPacing decides which decoded frame is actually shown.
 *   - Hardware (d3d11va) is strictly OPTIONAL. If anything about the hw path
 *     fails we log once and continue in software.
 *
 * Pixel format written to the caller's buffer: 8-bit RGBA, byte order in
 * memory is R,G,B,A (AV_PIX_FMT_RGBA), tightly packed, stride == width * 4,
 * rows in top-down (natural image) order.
 *
 * That is exactly Minecraft's com.mojang.blaze3d.platform.NativeImage
 * Format.RGBA byte order, so a caller can hand the buffer straight to
 * NativeImage.setPixelRGBA / upload without any channel swizzle. Note this is
 * NOT the same as the packed int that BufferedImage.getRGB() returns (that is
 * ARGB), which is why the existing JCodec path has to run argbToAbgr().
 *
 * Build: see native/ffmpeg/build-ffmpeg.sh (section "JNI shim").
 */

#include <jni.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/buffer.h>
#include <libavutil/error.h>
#include <libavutil/hwcontext.h>
#include <libavutil/hwcontext_d3d11va.h>
#include <libavutil/imgutils.h>
#include <libavutil/time.h>   /* av_gettime_relative(), used by the CLI harness */
#include <libswscale/swscale.h>

/* Java class this shim belongs to. */
#define VF_CLASS "net/wurstclient/background/FfmpegVideoDecoder"

/* Set to 1 to allow dxva2 as a second hardware fallback. dxva2 needs a D3D9
 * device plus a window handle in some drivers, so it is off by default and
 * d3d11va is the only hardware path we advertise. */
#ifndef VF_ENABLE_DXVA2
#define VF_ENABLE_DXVA2 0
#endif

/* Longest reason string Java can get back. Generous on purpose: a reason is
 * the difference between "the wallpaper does not play" and a fix. */
#define VF_ERR_MAX 900
#define VF_NOTE_MAX 320
#define VF_FILE_MAX 240

typedef struct {
	AVFormatContext *fmt;
	AVCodecContext *dec;   /* owns hw_device_ctx; see hw_try_open_d3d11va */
	AVFrame *frame;
	AVFrame *swframe;      /* only allocated when hw -> sw transfer is needed */
	AVPacket *pkt;
	struct SwsContext *sws;
	int stream;
	int ok;                /* 1 only after vf_open() fully succeeded */
	int wantHardware;      /* what the caller asked for, not what was achieved */
	int width;             /* native decoded size */
	int height;
	int outW;              /* size we convert to; == native until caller asks */
	int outH;
	int hwActive;
	int hwTried;           /* av_hwdevice_ctx_create was actually called */
	int draining;          /* NULL packet has been sent, no more packets */
	int flushed;           /* avcodec_receive_frame returned EOF */
	int srcFmt;            /* sws source format, re-checked every frame */
	int srcW;              /* sws source width,  re-checked every frame */
	int srcH;              /* sws source height, re-checked every frame */
	int64_t framesOut;     /* frames converted; 0 = the next one is the first */
	char file[VF_FILE_MAX]; /* the path, so reasons can name the file */
	char note[VF_NOTE_MAX]; /* non-fatal: why a fallback happened (e.g. no hw) */
	char err[VF_ERR_MAX];
} VFHandle;

/* ------------------------------------------------------------------ utils */

static void vf_log_cb(void *avcl, int level, const char *fmt, va_list ap);

/* ----------------------------------------------------------------- errors */

/*
 * Every failure path records WHY it failed, and Java can read it back. This is
 * not decoration: the mod turns it into exactly one log line
 *
 *     [Background] 视频背景 <id> 不能播放：DECODE_FAILED / avc1 / <this text>
 *
 * and an in-game failure with an empty text ("... avc1 / ") is undiagnosable
 * from a distance - there is no debugger attached to a player's game.
 *
 * The shape is fixed so it can be grepped and asserted on:
 *
 *   <stage>: AVERROR <n> (<av_strerror text>) path=<...> hw=<...>
 *            [file=<path>] [<stage-specific context>] [<note>]
 *
 * e.g.
 *
 *   avformat_open_input: AVERROR -2 (No such file or directory)
 *     path=software hw=not-requested file=C:\...\missing.mp4
 *
 *   first avcodec_receive_frame: AVERROR -1094995529
 *     (Invalid data found when processing input) path=hardware
 *     hw=device-created codec=h264 frame=320x240
 *
 * "path=" is what was actually attempted (hardware can be requested and then
 * fail to initialise, which changes everything about the diagnosis), "hw=" says
 * how far the hardware path got: "device-created" (hardware decode really is
 * live), "device-unavailable" (tried, failed, software is a fallback) or
 * "not-attempted-yet" (the failure happened before the GPU was ever touched).
 *
 * Two sinks, because the moment that matters differs:
 *   - per handle (h->err) - read by Java while the handle is still alive, which
 *     is how a failing nextFrame() explains itself;
 *   - static (g_last_open_error) - survives the handle, because
 *     FfmpegVideoDecoder.open() closes a handle that never opened. That is
 *     precisely the open-time case the mod hits, and the reason the old code
 *     could show an empty string: the handle was already gone by then.
 */

typedef enum {
	VF_OPEN = 0,  /* failure is part of opening the file/decoder */
	VF_DECODE = 1 /* failure happened while decoding frames */
} VFStage;

/* For failures that never reached a handle (calloc). nativeLastError(0) reads
 * this; nativeLastOpenError() reads the open-stage variant. */
static char g_last_error[VF_ERR_MAX] = "";
static char g_last_open_error[VF_ERR_MAX] = "";

static const char *vf_last_open_error(void)
{
	return g_last_open_error;
}

static void vf_store(VFHandle *h, VFStage where, const char *line)
{
	snprintf(g_last_error, sizeof(g_last_error), "%s", line);

	if(h)
		snprintf(h->err, sizeof(h->err), "%s", line);

	/* Java frees a handle whose open failed right after reading the reason,
	 * so the open-stage reason has to outlive the handle. */
	if(where == VF_OPEN)
		snprintf(g_last_open_error, sizeof(g_last_open_error), "%s", line);
}

/* "path=hardware hw=device-created": what was asked for vs. what came up.
 *
 * Three states, not two, because they diagnose differently:
 *   - device created: the hardware path really is live;
 *   - device creation FAILED: software is a fallback, and the reason for that
 *     is in the note (so it is printed too);
 *   - not attempted yet: the open died before av_hwdevice_ctx_create was ever
 *     called (a bad path cannot reach the GPU), which says nothing at all
 *     about whether hardware works on this machine.
 */
static void vf_path_text(const VFHandle *h, char *out, size_t n)
{
	if(!h)
		snprintf(out, n, "path=unknown");
	else if(!h->wantHardware)
		snprintf(out, n, "path=software hw=not-requested");
	else if(h->hwActive)
		snprintf(out, n, "path=hardware hw=device-created");
	else if(h->hwTried)
		snprintf(out, n, "path=software(fallback) hw=device-unavailable");
	else
		snprintf(out, n, "path=hardware(requested) hw=not-attempted-yet");
}

/*
 * Record a failure: stage name, AVERROR value, av_strerror() text, the decode
 * path that was attempted, and any stage-specific context. Call this on every
 * path that makes an operation fail - never return an error without it.
 */
static void vf_fail(VFHandle *h, VFStage where, const char *stage, int err,
	const char *context)
{
	char text[192];
	char path[192];
	char line[VF_ERR_MAX];
	int used;

	av_strerror(err, text, sizeof(text));
	vf_path_text(h, path, sizeof(path));

	used = snprintf(line, sizeof(line), "%s: AVERROR %d (%s) %s", stage, err,
		text, path);

	if(where == VF_OPEN && h && h->file[0] && used > 0
		&& used < (int)sizeof(line))
		used += snprintf(line + used, sizeof(line) - (size_t)used,
			" file=%s", h->file);

	if(context && *context && used > 0 && used < (int)sizeof(line))
		used += snprintf(line + used, sizeof(line) - (size_t)used, " %s",
			context);

	if(h && h->note[0] && used > 0 && used < (int)sizeof(line))
		snprintf(line + used, sizeof(line) - (size_t)used, " [%s]", h->note);

	vf_store(h, where, line);

	if(getenv("VF_DEBUG"))
		fprintf(stderr, "[vf] fail: %s\n", line);
}

/*
 * Record something that did NOT fail the operation but explains which path is
 * live - today only "d3d11va was asked for and could not be created". It must
 * not become the failure reason by itself (software decode may work fine), but
 * it belongs in the reason if software then fails too.
 */
static void vf_note(VFHandle *h, const char *stage, int err, const char *context)
{
	char text[192];

	if(!h)
		return;

	av_strerror(err, text, sizeof(text));
	snprintf(h->note, sizeof(h->note), "%s: AVERROR %d (%s)%s%s", stage, err,
		text, (context && *context) ? " " : "",
		(context && *context) ? context : "");

	if(getenv("VF_DEBUG"))
		fprintf(stderr, "[vf] note: %s\n", h->note);
}

/* ------------------------------------------------------------------- open */

/*
 * Drop the hw state. Safe to call after a reopen: the device may already be
 * gone (avcodec_free_context does not clear our flag, but a mid-stream failure
 * can leave a stale hwaccel).
 */
static void hw_close(VFHandle *h)
{
	h->hwActive = 0;
}

/*
 * Ask for a D3D11VA device and hand it to the decoder. Returns 1 on success, 0
 * on any failure - never fatal.
 *
 * OWNERSHIP (measured, and the reason this is not a two-line function):
 * the reference returned by av_hwdevice_ctx_create() is the ONLY one; after
 * avcodec_open2() the codec context holds it and it must NOT be unreffed again
 * by us. Doing so was a double free that showed up as STATUS_HEAP_CORRUPTION
 * inside avcodec_free_context / av_buffer_unref. So the device is deliberately
 * not stored anywhere: h->hwActive is the flag, h->dec->hw_device_ctx is the
 * owner, and avcodec_free_context() releases it.
 */
static int hw_try_open_d3d11va(VFHandle *h)
{
	AVBufferRef *dev = NULL;
	int err;

	h->hwTried = 1;
	err = av_hwdevice_ctx_create(&dev, AV_HWDEVICE_TYPE_D3D11VA, NULL, NULL, 0);
	if(err < 0)
	{
		/* Not fatal: software decode is the fallback and usually works. It is
		 * still recorded, because "hardware was requested and could not even
		 * create a device" is the first thing anyone reading the log needs. */
		vf_note(h, "av_hwdevice_ctx_create(d3d11va)", err,
			"hardware unavailable, staying on software decode");
		return 0;
	}

	h->dec->hw_device_ctx = dev; /* ownership passes to the codec context */
	h->hwActive = 1;

	if(getenv("VF_DEBUG"))
		fprintf(stderr, "[vf] d3d11va device created, refcount=%d\n",
			av_buffer_get_ref_count(dev));

	return 1;
}

static void hw_disable_after_failure(VFHandle *h)
{
	/* Called when get_format / receive_frame hit a hw problem: tear the hw
	 * state down so the caller can retry the same file in software. The
	 * codec context is about to be thrown away by the caller, so all we do
	 * is stop claiming hardware. */
	hw_close(h);
	if(h->swframe)
	{
		av_frame_free(&h->swframe);
		h->swframe = NULL;
	}
}

/*
 * Pick the hardware pixel format.
 *
 * There are TWO D3D11 hwaccels and they are NOT interchangeable - each one
 * declares a different pixel format, and picking the wrong one leaves a
 * hwaccel with no alloc_frame, so every frame dies with "get_buffer() failed":
 *
 *     h264_d3d11va   -> AV_PIX_FMT_D3D11VA_VLD  (the older *_vld style)
 *     h264_d3d11va2  -> AV_PIX_FMT_D3D11        (the modern one)
 *
 * Prefer D3D11 and fall back to D3D11VA_VLD, so the shim works with either
 * configured set (build-ffmpeg.sh enables both).
 */
static enum AVPixelFormat get_hw_format(AVCodecContext *ctx,
	const enum AVPixelFormat *pixfmts)
{
	const enum AVPixelFormat *p;
	VFHandle *h = (VFHandle *)ctx->opaque;
	int haveVld = 0;

	for(p = pixfmts; *p != AV_PIX_FMT_NONE; p++)
	{
		if(*p == AV_PIX_FMT_D3D11)
		{
			if(getenv("VF_DEBUG"))
				fprintf(stderr, "[vf] get_hw_format -> D3D11\n");
			return *p;
		}
		if(*p == AV_PIX_FMT_D3D11VA_VLD)
			haveVld = 1;
	}

	if(haveVld)
	{
		if(getenv("VF_DEBUG"))
			fprintf(stderr, "[vf] get_hw_format -> D3D11VA_VLD\n");
		return AV_PIX_FMT_D3D11VA_VLD;
	}

	vf_fail(h, VF_OPEN, "get_hw_format", AVERROR(ENOSYS),
		"the decoder offered no D3D11/D3D11VA_VLD pixel format");
	return AV_PIX_FMT_NONE;
}

/*
 * Software-only format negotiation: refuse every hardware format and take the
 * first plain one.
 *
 * This is not just belt-and-braces. With plain libavcodec defaults the H.264
 * decoder can end up initialising the D3D11VA hwaccel even when the caller
 * never created a device context (observed as a NULL call inside
 * avcodec_get_hw_frames_parameters on a frame-threading worker). An explicit
 * get_format that only ever returns a software format makes that path
 * unreachable, which is exactly the guarantee a wallpaper decoder wants.
 */
/*
 * Software-only format negotiation: refuse every hardware format and take the
 * first plain one.
 *
 * This is not just belt-and-braces. With plain libavcodec defaults the H.264
 * decoder can end up initialising the D3D11VA hwaccel even when the caller
 * never created a device context (observed as a NULL call inside
 * avcodec_get_hw_frames_parameters on a frame-threading worker). An explicit
 * get_format that only ever returns a software format makes that path
 * unreachable, which is exactly the guarantee a wallpaper decoder wants.
 *
 * Note this cannot make AV1 work in software: FFmpeg 7.1's av1 decoder has no
 * native software path at all and returns AVERROR(ENOSYS) unless a hwaccel
 * initialised (see av1dec.c, "the av1 decoder doesn't support native decode").
 * Software AV1 would need libdav1d, which is a separate build dependency.
 */
static enum AVPixelFormat get_sw_format(AVCodecContext *ctx,
	const enum AVPixelFormat *pixfmts)
{
	const enum AVPixelFormat *p;
	VFHandle *h = (VFHandle *)ctx->opaque;

	for(p = pixfmts; *p != AV_PIX_FMT_NONE; p++)
	{
		const AVPixFmtDescriptor *d = av_pix_fmt_desc_get(*p);
		if(!d || (d->flags & AV_PIX_FMT_FLAG_HWACCEL))
			continue;
		return *p;
	}

	/* Happens for a codec this build has no software decoder for at all -
	 * AV1 is the real one (av1dec.c returns ENOSYS without a hwaccel), and
	 * without this line the log would only say "avcodec_open2 failed". */
	vf_fail(h, VF_OPEN, "get_sw_format", AVERROR(ENOSYS),
		"the decoder offered no software pixel format "
		"(this build may only decode this codec on the GPU)");
	return AV_PIX_FMT_NONE;
}

static VFHandle *vf_open(const char *path, int wantHardware)
{
	VFHandle *h;
	const AVCodec *codec = NULL;
	const AVStream *st;
	AVDictionary *opts = NULL;
	int err;
	char ctx[192];
	char fourcc[AV_FOURCC_MAX_STRING_SIZE];

	/* A new open starts a new story: drop the previous open failure so a
	 * successful open cannot be reported with a stale reason. */
	g_last_open_error[0] = '\0';
	g_last_error[0] = '\0';

	h = (VFHandle *)calloc(1, sizeof(*h));
	if(!h)
	{
		vf_fail(NULL, VF_OPEN, "vf_open", AVERROR(ENOMEM),
			"cannot allocate the decoder handle");
		return NULL;
	}

	if(getenv("VF_DEBUG"))
	{
		av_log_set_callback(vf_log_cb);
		av_log_set_level(AV_LOG_DEBUG);
	}

	h->stream = -1;
	h->srcFmt = AV_PIX_FMT_NONE;
	h->wantHardware = wantHardware;
	snprintf(h->file, sizeof(h->file), "%s", path ? path : "(null)");

	err = avformat_open_input(&h->fmt, path, NULL, NULL);
	if(err < 0)
	{
		/* The path itself is part of the reason (see vf_fail): "cannot open"
		 * and "cannot open THIS file" are different bug reports. */
		vf_fail(h, VF_OPEN, "avformat_open_input", err, NULL);
		return h; /* non-NULL so Java can read vf_last_error */
	}

	err = avformat_find_stream_info(h->fmt, NULL);
	if(err < 0)
	{
		vf_fail(h, VF_OPEN, "avformat_find_stream_info", err,
			"the container was opened but its stream headers are unusable");
		return h;
	}

	err = av_find_best_stream(h->fmt, AVMEDIA_TYPE_VIDEO, -1, -1, &codec, 0);
	if(err < 0 || !codec)
	{
		vf_fail(h, VF_OPEN, "av_find_best_stream(video)",
			err < 0 ? err : AVERROR_STREAM_NOT_FOUND,
			codec ? NULL
				  : "the container has a video stream but no decoder for "
					"its codec is compiled into this build");
		return h;
	}
	h->stream = err;
	st = h->fmt->streams[h->stream];
	h->width = st->codecpar->width;
	h->height = st->codecpar->height;

	snprintf(ctx, sizeof(ctx), "codec=%s fourcc=%s %dx%d",
		avcodec_get_name(st->codecpar->codec_id),
		av_fourcc_make_string(fourcc, st->codecpar->codec_tag),
		h->width, h->height);

	h->dec = avcodec_alloc_context3(codec);
	if(!h->dec)
	{
		vf_fail(h, VF_OPEN, "avcodec_alloc_context3", AVERROR(ENOMEM), ctx);
		return h;
	}
	h->dec->opaque = h;

	err = avcodec_parameters_to_context(h->dec, st->codecpar);
	if(err < 0)
	{
		vf_fail(h, VF_OPEN, "avcodec_parameters_to_context", err, ctx);
		return h;
	}

	/* The decoder owns timing and looping? No - the caller does. We never set
	 * AV_CODEC_FLAG_LOW_DELAY: it was tried and it interacts badly with the
	 * hwaccel selection below. See the threading note further down. */

	if(wantHardware)
	{
		/* Create the device first; only advertise get_format once we know a
		 * hw device really exists, otherwise libavcodec would pick D3D11 and
		 * then fail with no software fallback. */
		if(hw_try_open_d3d11va(h))
			h->dec->get_format = get_hw_format;
		else
			h->dec->get_format = get_sw_format;
	}
	else
	{
		h->hwActive = 0;
		h->dec->get_format = get_sw_format;
		h->dec->hw_device_ctx = NULL;
		h->dec->hw_frames_ctx = NULL;
	}

	/*
	 * Threading (must come after the hw device decision, which sets hwActive).
	 *
	 * Software: frame threading is what makes 4K H.264 fast, so let libavcodec
	 * pick (16 threads on this box).
	 *
	 * Hardware: frame threading must be OFF. The D3D11VA hwaccel keeps its
	 * frames context on the decoder context, and under FF_THREAD_FRAME the
	 * per-thread copies never get a usable one, so every frame dies with
	 * "get_buffer() failed / thread_get_buffer() failed" and nothing decodes.
	 * The GPU already parallelises internally, so there is little to lose.
	 */
	if(wantHardware && h->hwActive)
	{
		h->dec->thread_count = 1;
		h->dec->thread_type &= ~FF_THREAD_FRAME;
	}
	else
		h->dec->thread_count = getenv("VF_THREADS")
			? atoi(getenv("VF_THREADS")) : 0;

	if(getenv("VF_DEBUG"))
		fprintf(stderr,
			"[vf] wantHw=%d hwActive=%d get_format=%p "
			"hw_device_ctx=%p hw_frames_ctx=%p\n",
			wantHardware, h->hwActive,
			(void *)(intptr_t)h->dec->get_format,
			(void *)h->dec->hw_device_ctx,
			(void *)h->dec->hw_frames_ctx);

	err = avcodec_open2(h->dec, codec, &opts);
	av_dict_free(&opts);
	if(err < 0)
	{
		snprintf(ctx, sizeof(ctx), "codec=%s %s threads=%d", codec->name,
			h->hwActive ? "d3d11va" : "software", h->dec->thread_count);
		vf_fail(h, VF_OPEN, "avcodec_open2", err, ctx);
		return h;
	}

	/* Some codecs (e.g. VP9/AV1 in certain containers) only report the real
	 * size after the first frame; use the decoder's view when it is set. */
	if(h->dec->width > 0 && h->dec->height > 0)
	{
		h->width = h->dec->width;
		h->height = h->dec->height;
	}

	if(h->width <= 0 || h->height <= 0)
	{
		snprintf(ctx, sizeof(ctx), "codec=%s width=%d height=%d", codec->name,
			h->width, h->height);
		vf_fail(h, VF_OPEN, "frame size after avcodec_open2", AVERROR(EINVAL),
			ctx);
		return h;
	}

	h->frame = av_frame_alloc();
	h->pkt = av_packet_alloc();
	if(!h->frame || !h->pkt)
	{
		vf_fail(h, VF_OPEN, "av_frame_alloc/av_packet_alloc",
			AVERROR(ENOMEM), NULL);
		return h;
	}

	h->outW = h->width;
	h->outH = h->height;
	h->err[0] = '\0';
	h->ok = 1;

	if(getenv("VF_DEBUG"))
		fprintf(stderr,
			"[vf] opened %dx%d codec=%s get_format=%p hw_device_ctx=%p\n",
			h->width, h->height, codec->name,
			(void *)(intptr_t)h->dec->get_format,
			(void *)h->dec->hw_device_ctx);

	if(getenv("VF_DEBUG"))
		fprintf(stderr,
			"[vf] post-open: hwaccel=%s pix_fmt=%d sw_pix_fmt=%d "
			"hw_frames_ctx=%p active_threads=%d thread_count=%d "
			"dev_refcount=%d\n",
			h->dec->hwaccel ? h->dec->hwaccel->name : "(none)",
			(int)h->dec->pix_fmt, (int)h->dec->sw_pix_fmt,
			(void *)h->dec->hw_frames_ctx,
			(int)h->dec->active_thread_type, h->dec->thread_count,
			h->dec->hw_device_ctx
				? av_buffer_get_ref_count(h->dec->hw_device_ctx) : -1);

	return h;
}

static int vf_is_open(VFHandle *h)
{
	return h && h->ok && h->fmt && h->dec && h->frame && h->pkt
		&& h->width > 0 && h->height > 0;
}

static void vf_close(VFHandle *h)
{
	if(!h)
		return;

#define VFCLOSE_STEP(name, expr)                                           \
	do                                                                     \
	{                                                                      \
		if(getenv("VF_DEBUG"))                                             \
			fprintf(stderr, "[vf] close: " name "\n");                     \
		expr;                                                              \
	} while(0)

	/*
	 * Order matters.
	 *
	 * The decoder context owns the D3D11VA device, so it must be freed first
	 * (see hw_try_open_d3d11va for the ownership story). Everything else is
	 * independent, but the swscale context and the frames are torn down after
	 * the decoder because a sws context built for a hw-transferred frame can
	 * reference frame data.
	 */
	VFCLOSE_STEP("dec", if(h->dec) avcodec_free_context(&h->dec));
	VFCLOSE_STEP("sws", if(h->sws) sws_freeContext(h->sws));
	VFCLOSE_STEP("swframe", if(h->swframe) av_frame_free(&h->swframe));
	VFCLOSE_STEP("frame", if(h->frame) av_frame_free(&h->frame));
	VFCLOSE_STEP("pkt", if(h->pkt) av_packet_free(&h->pkt));
	VFCLOSE_STEP("fmt", if(h->fmt) avformat_close_input(&h->fmt));
	VFCLOSE_STEP("handle", free(h));

#undef VFCLOSE_STEP
}

/* -------------------------------------------------------------- conversion */

/*
 * Convert the decoded frame into tightly packed RGBA at dst, scaling to
 * h->outW x h->outH. dst must have room for outW*outH*4 bytes plus
 * AV_INPUT_BUFFER_PADDING_SIZE of slack. Returns the number of bytes written,
 * or a negative AVERROR.
 */
static int frame_to_rgba(VFHandle *h, AVFrame *src, uint8_t *dst, int cap)
{
	int needed = h->outW * h->outH * 4;
	uint8_t *dstData[4] = {dst, NULL, NULL, NULL};
	int dstStride[4] = {h->outW * 4, 0, 0, 0};
	int srcFmt = src->format;
	char ctx[192];

	if(cap < needed + AV_INPUT_BUFFER_PADDING_SIZE)
	{
		snprintf(ctx, sizeof(ctx),
			"the caller's buffer is too small: have=%d need=%d", cap,
			needed + AV_INPUT_BUFFER_PADDING_SIZE);
		vf_fail(h, VF_DECODE, "frame_to_rgba", AVERROR(EINVAL), ctx);
		return AVERROR(EINVAL);
	}

	if(!h->sws || h->srcFmt != srcFmt || h->srcW != src->width
		|| h->srcH != src->height)
	{
		if(h->sws)
			sws_freeContext(h->sws);
		h->sws = sws_getContext(src->width, src->height,
			(enum AVPixelFormat)srcFmt, h->outW, h->outH, AV_PIX_FMT_RGBA,
			SWS_FAST_BILINEAR, NULL, NULL, NULL);
		if(!h->sws)
		{
			/* sws_getContext returns NULL (not an AVERROR) when it cannot
			 * build the scaler: unsupported source format, or no memory. */
			snprintf(ctx, sizeof(ctx),
				"sws_getContext(%dx%d fmt=%d -> %dx%d RGBA) returned NULL",
				src->width, src->height, srcFmt, h->outW, h->outH);
			vf_fail(h, VF_DECODE, "sws_getContext", AVERROR(ENOMEM), ctx);
			return AVERROR(ENOMEM);
		}
		h->srcFmt = srcFmt;
		h->srcW = src->width;
		h->srcH = src->height;
	}

	sws_scale(h->sws, (const uint8_t *const *)src->data, src->linesize, 0,
		src->height, dstData, dstStride);

	/* Scaler is allowed to write with SIMD over-run at row ends, and the
	 * caller may hand us a JNI critical-section view of a Java array. Zero
	 * the slack so no uninitialised byte is ever visible to Java. */
	memset(dst + needed, 0, AV_INPUT_BUFFER_PADDING_SIZE);

	return needed;
}

/*
 * Point subsequent conversions at outW x outH. Pass 0/0 for native size.
 * Returns the resulting byte count per frame.
 */
static int vf_set_output_size(VFHandle *h, int outW, int outH)
{
	char ctx[192];

	if(!vf_is_open(h))
	{
		vf_fail(h, VF_DECODE, "setOutputSize", AVERROR(EINVAL),
			"the decoder is not open (open failed, or it was already closed)");
		return AVERROR(EINVAL);
	}

	if(outW <= 0 || outH <= 0)
	{
		outW = h->width;
		outH = h->height;
	}

	if(outW > h->width * 8 || outH > h->height * 8)
	{
		snprintf(ctx, sizeof(ctx),
			"refusing an absurd upscale: requested=%dx%d source=%dx%d", outW,
			outH, h->width, h->height);
		vf_fail(h, VF_DECODE, "setOutputSize", AVERROR(EINVAL), ctx);
		return AVERROR(EINVAL); /* refuse absurd upscales */
	}

	/*
	 * The cached scaler was built for the previous output size, and
	 * frame_to_rgba only rebuilds it when the SOURCE changes. It has to be
	 * dropped here: keeping it makes sws_scale write the old number of pixels
	 * with the NEW stride, which lays the rows out wrong (a torn, smeared
	 * picture - the output looks like two different frames mixed together),
	 * and when the output shrank it writes straight past the end of the
	 * caller's buffer. The byte count this function returns is computed, not
	 * measured, so the Java side cannot notice either failure: it checks
	 * "bytes == outW*outH*4" and that always matches.
	 */
	if(h->outW != outW || h->outH != outH)
	{
		if(h->sws)
		{
			sws_freeContext(h->sws);
			h->sws = NULL;
		}
	}

	h->outW = outW;
	h->outH = outH;
	return outW * outH * 4;
}

/*
 * Pull exactly one frame out of the decoder, running the demux loop as needed.
 * Returns:
 *    1  a decoded frame is in h->frame
 *    0  end of stream reached
 *   <0  error (AVERROR_EOF means "end of stream" as well)
 */
static int pull_frame(VFHandle *h)
{
	int err;
	char ctx[192];

	snprintf(ctx, sizeof(ctx), "codec=%s frame=%dx%d already_out=%lld",
		h->dec->codec ? h->dec->codec->name : "(none)", h->width, h->height,
		(long long)h->framesOut);

	for(;;)
	{
		err = avcodec_receive_frame(h->dec, h->frame);
		if(err == 0)
			return 1;
		if(err == AVERROR_EOF)
			return 0;
		if(err != AVERROR(EAGAIN))
		{
			/* The interesting one: a decoder that refuses the very first
			 * frame. That is what "the video does not play" usually is. */
			vf_fail(h, VF_DECODE,
				h->framesOut == 0 ? "first avcodec_receive_frame"
								  : "avcodec_receive_frame",
				err, ctx);
			return err;
		}

		/* Decoder needs more input. */
		if(h->draining)
			return 0;

		for(;;)
		{
			err = av_read_frame(h->fmt, h->pkt);
			if(err < 0)
			{
				/* End of file (or a read error): flush the decoder. A read
				 * error is recorded rather than swallowed - a truncated file
				 * otherwise ends exactly like a normal end of stream, and
				 * "it plays nothing and says nothing" is the bug this error
				 * channel exists for. */
				if(err != AVERROR_EOF)
					vf_fail(h, VF_DECODE,
						h->framesOut == 0 ? "first av_read_frame"
										  : "av_read_frame",
						err,
						"treating it as end of stream and flushing the "
						"decoder");

				avcodec_send_packet(h->dec, NULL);
				h->draining = 1;
				break;
			}

			if(h->pkt->stream_index != h->stream)
			{
				av_packet_unref(h->pkt);
				continue;
			}

			err = avcodec_send_packet(h->dec, h->pkt);
			av_packet_unref(h->pkt);
			if(err < 0 && err != AVERROR(EAGAIN))
			{
				vf_fail(h, VF_DECODE,
					h->framesOut == 0 ? "first avcodec_send_packet"
									  : "avcodec_send_packet",
					err, ctx);
				return err;
			}
			break;
		}
	}
}

static int vf_next_frame(VFHandle *h, uint8_t *dst, int cap)
{
	AVFrame *out;
	int err;

	if(!vf_is_open(h))
	{
		vf_fail(h, VF_DECODE, "nextFrame", AVERROR(EINVAL),
			"the decoder is not open (open failed, or it was already closed)");
		return AVERROR(EINVAL);
	}

	if(h->flushed)
		return 0;

	err = pull_frame(h);
	if(err <= 0)
	{
		if(err == 0)
			h->flushed = 1;
		return err;
	}

	out = h->frame;

	if(out->format == AV_PIX_FMT_D3D11
		|| out->format == AV_PIX_FMT_D3D11VA_VLD)
	{
		if(!h->swframe)
		{
			h->swframe = av_frame_alloc();
			if(!h->swframe)
			{
				vf_fail(h, VF_DECODE, "av_frame_alloc(hw->sw transfer)",
					AVERROR(ENOMEM), NULL);
				return AVERROR(ENOMEM);
			}
		}

		err = av_hwframe_transfer_data(h->swframe, out, 0);
		if(err < 0)
		{
			char ctx[192];

			/* Hardware decode broke mid-stream. Do not fail the caller:
			 * report it so Java can reopen in software. */
			snprintf(ctx, sizeof(ctx),
				"GPU -> CPU frame transfer failed at output frame %lld; "
				"hardware decode has to be dropped, reopen in software",
				(long long)h->framesOut);
			vf_fail(h, VF_DECODE, "av_hwframe_transfer_data", err, ctx);
			hw_disable_after_failure(h);
			return err;
		}
		out = h->swframe;
	}

	err = frame_to_rgba(h, out, dst, cap);
	av_frame_unref(h->frame);
	if(h->swframe)
		av_frame_unref(h->swframe);

	if(err > 0)
		h->framesOut++;

	return err;
}

static int vf_seek_to_start(VFHandle *h)
{
	int err;

	if(!h || !h->fmt || !h->dec)
	{
		vf_fail(h, VF_DECODE, "seekToStart", AVERROR(EINVAL),
			"the decoder is not open (open failed, or it was already closed)");
		return AVERROR(EINVAL);
	}

	err = av_seek_frame(h->fmt, h->stream, 0, AVSEEK_FLAG_BACKWARD);
	if(err < 0)
	{
		vf_fail(h, VF_DECODE, "av_seek_frame(to the start)", err,
			"the file cannot be rewound, so looping would stall");
		return err;
	}

	avformat_flush(h->fmt);
	avcodec_flush_buffers(h->dec);
	av_packet_unref(h->pkt);
	if(h->frame)
		av_frame_unref(h->frame);
	if(h->swframe)
		av_frame_unref(h->swframe);
	h->draining = 0;
	h->flushed = 0;
	h->err[0] = '\0';
	return 0;
}

static double vf_frame_rate(VFHandle *h)
{
	AVRational r;
	double fps;

	if(!h || !h->fmt || h->stream < 0)
		return 0.0;

	r = av_guess_frame_rate(h->fmt, h->fmt->streams[h->stream], NULL);
	if(r.num > 0 && r.den > 0)
	{
		fps = av_q2d(r);
		if(fps > 0.0)
			return fps;
	}

	r = h->fmt->streams[h->stream]->avg_frame_rate;
	if(r.num > 0 && r.den > 0)
		return av_q2d(r);
	return 0.0;
}

static int64_t vf_duration_ms(VFHandle *h)
{
	int64_t d;

	if(!h || !h->fmt)
		return 0;

	d = h->fmt->duration;
	if(d <= 0 || d == AV_NOPTS_VALUE)
		return 0;
	return d / (AV_TIME_BASE / 1000);
}

/* -------------------------------------------------------------------- JNI */

static VFHandle *handle_from(jlong ptr)
{
	return (VFHandle *)(intptr_t)ptr;
}

/* Route libav* diagnostics to stderr when VF_DEBUG is set; invaluable for
 * working out which hwaccel / pixel format libavcodec actually negotiated. */
static void vf_log_cb(void *avcl, int level, const char *fmt, va_list ap)
{
	(void)avcl;
	if(level > AV_LOG_DEBUG)
		return;
	fprintf(stderr, "[ffmpeg] ");
	vfprintf(stderr, fmt, ap);
}

JNIEXPORT jlong JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeOpen(
	JNIEnv *env, jclass cls, jstring jpath, jboolean wantHardware)
{
	const char *path;
	VFHandle *h;
	(void)cls;

	if(!jpath)
	{
		vf_fail(NULL, VF_OPEN, "nativeOpen", AVERROR(EINVAL),
			"Java passed a null path");
		return 0;
	}

	path = (*env)->GetStringUTFChars(env, jpath, NULL);
	if(!path)
	{
		vf_fail(NULL, VF_OPEN, "nativeOpen", AVERROR(ENOMEM),
			"GetStringUTFChars returned NULL (out of memory)");
		return 0;
	}

	h = vf_open(path, wantHardware ? 1 : 0);
	(*env)->ReleaseStringUTFChars(env, jpath, path);

	if(!h)
		return 0;

	if(!vf_is_open(h))
	{
		/* Keep the handle alive until Java has read the error, then free. */
		return (jlong)(intptr_t)h;
	}
	return (jlong)(intptr_t)h;
}

JNIEXPORT jboolean JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeIsOpen(
	JNIEnv *env, jclass cls, jlong ptr)
{
	(void)env;
	(void)cls;
	return vf_is_open(handle_from(ptr)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeLastError(
	JNIEnv *env, jclass cls, jlong ptr)
{
	VFHandle *h = handle_from(ptr);
	(void)cls;

	if(!h)
		return (*env)->NewStringUTF(env, g_last_error);
	return (*env)->NewStringUTF(env, h->err);
}

/*
 * The reason the last open attempt failed, without needing a handle. The Java
 * side has to free a handle that never opened, so this is the only channel
 * that survives into the mod's log line for that case.
 */
JNIEXPORT jstring JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeLastOpenError(
	JNIEnv *env, jclass cls)
{
	(void)cls;
	return (*env)->NewStringUTF(env, vf_last_open_error());
}

JNIEXPORT jint JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeWidth(
	JNIEnv *env, jclass cls, jlong ptr)
{
	VFHandle *h = handle_from(ptr);
	(void)env;
	(void)cls;
	return h ? h->width : 0;
}

JNIEXPORT jint JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeHeight(
	JNIEnv *env, jclass cls, jlong ptr)
{
	VFHandle *h = handle_from(ptr);
	(void)env;
	(void)cls;
	return h ? h->height : 0;
}

JNIEXPORT jdouble JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeFrameRate(
	JNIEnv *env, jclass cls, jlong ptr)
{
	(void)env;
	(void)cls;
	return vf_frame_rate(handle_from(ptr));
}

JNIEXPORT jlong JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeDurationMs(
	JNIEnv *env, jclass cls, jlong ptr)
{
	(void)env;
	(void)cls;
	return (jlong)vf_duration_ms(handle_from(ptr));
}

JNIEXPORT jboolean JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeIsHardware(
	JNIEnv *env, jclass cls, jlong ptr)
{
	VFHandle *h = handle_from(ptr);
	(void)env;
	(void)cls;
	return (h && h->hwActive) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeSetOutputSize(
	JNIEnv *env, jclass cls, jlong ptr, jint outW, jint outH)
{
	(void)env;
	(void)cls;
	return (jint)vf_set_output_size(handle_from(ptr), outW, outH);
}

JNIEXPORT jint JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeOutputWidth(
	JNIEnv *env, jclass cls, jlong ptr)
{
	VFHandle *h = handle_from(ptr);
	(void)env;
	(void)cls;
	return h ? h->outW : 0;
}

JNIEXPORT jint JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeOutputHeight(
	JNIEnv *env, jclass cls, jlong ptr)
{
	VFHandle *h = handle_from(ptr);
	(void)env;
	(void)cls;
	return h ? h->outH : 0;
}

JNIEXPORT jint JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeNextFrame(
	JNIEnv *env, jclass cls, jlong ptr, jbyteArray jdst, jint capacity)
{
	VFHandle *h = handle_from(ptr);
	jsize len;
	jbyte *buf;
	int rc, needed;
	char ctx[192];
	(void)cls;

	if(!h || !jdst)
	{
		vf_fail(h, VF_DECODE, "nativeNextFrame", AVERROR(EINVAL),
			"no handle or no destination buffer");
		return -1;
	}

	needed = h->outW * h->outH * 4;

	/* GetPrimitiveArrayCritical may hand us the array itself, so never let
	 * libswscale write past the end: require the padding slack up front. */
	if(h->outW <= 0 || h->outH <= 0)
	{
		vf_fail(h, VF_DECODE, "nativeNextFrame", AVERROR(EINVAL),
			"the output size is not set (setOutputSize was never called)");
		return -1;
	}

	len = (*env)->GetArrayLength(env, jdst);
	if(capacity > len)
		capacity = len;
	if(capacity < needed + AV_INPUT_BUFFER_PADDING_SIZE)
	{
		snprintf(ctx, sizeof(ctx),
			"Java buffer too small: capacity=%d need=%d (%dx%d RGBA + %d)",
			(int)capacity, needed + AV_INPUT_BUFFER_PADDING_SIZE, h->outW,
			h->outH, AV_INPUT_BUFFER_PADDING_SIZE);
		vf_fail(h, VF_DECODE, "nativeNextFrame", AVERROR(EINVAL), ctx);
		return -1;
	}

	buf = (*env)->GetPrimitiveArrayCritical(env, jdst, NULL);
	if(!buf)
	{
		vf_fail(h, VF_DECODE, "nativeNextFrame", AVERROR(ENOMEM),
			"GetPrimitiveArrayCritical failed");
		return -1;
	}

	rc = vf_next_frame(h, (uint8_t *)buf, capacity);

	(*env)->ReleasePrimitiveArrayCritical(env, jdst, buf, 0);
	return (jint)rc;
}

JNIEXPORT jint JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeSeekToStart(
	JNIEnv *env, jclass cls, jlong ptr)
{
	(void)env;
	(void)cls;
	return (jint)vf_seek_to_start(handle_from(ptr));
}

JNIEXPORT void JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeClose(
	JNIEnv *env, jclass cls, jlong ptr)
{
	(void)env;
	(void)cls;
	vf_close(handle_from(ptr));
}

JNIEXPORT jstring JNICALL Java_net_wurstclient_background_FfmpegVideoDecoder_nativeVersion(
	JNIEnv *env, jclass cls)
{
	(void)cls;
	return (*env)->NewStringUTF(env, av_version_info());
}

/* --------------------------------------------------------- CLI self-test */

/*
 * Not part of the JNI surface: a plain C harness so the numbers in
 * native/ffmpeg/README.md can be reproduced without Minecraft or a JVM.
 * Built as vf_ffmpeg_test.exe by build-ffmpeg.sh.
 */
#ifdef VF_WITH_MAIN

static double now_sec(void)
{
	return (double)av_gettime_relative() / 1000000.0;
}

int main(int argc, char **argv)
{
	const char *path;
	VFHandle *vh;
	int fw, fh, ow, oh, cap, frames, i, useHw, rc, loops;
	uint8_t *buf;
	double t0, t1;
	int lastRc;
	unsigned long long sumR = 0, sumG = 0, sumB = 0, sampled = 0;
	unsigned char minR = 255, maxR = 0, minG = 255, maxG = 0;
	unsigned char minB = 255, maxB = 0;
	int wantW, wantH;

	if(argc < 2)
	{
		fprintf(stderr,
			"usage: %s <file> [loops] [hardware] [outW outH]\n"
			"  loops    : how many times to play through (default 1)\n"
			"  hardware : 1 = try d3d11va, 0 = software only (default 0)\n"
			"  outW/H   : scale the RGBA output to this size; omit for native\n",
			argv[0]);
		return 2;
	}

	path = argv[1];
	loops = argc > 2 ? atoi(argv[2]) : 1;
	useHw = argc > 3 ? atoi(argv[3]) : 0;
	wantW = argc > 4 ? atoi(argv[4]) : 0;
	wantH = argc > 5 ? atoi(argv[5]) : 0;
	if(loops < 1)
		loops = 1;

	printf("ffmpeg %s\n", av_version_info());

	vh = vf_open(path, useHw);
	if(!vh || !vf_is_open(vh))
	{
		fprintf(stderr, "open failed: %s\n",
			vh && vh->err[0] ? vh->err : vf_last_open_error());
		if(vh)
			vf_close(vh);
		return 1;
	}

	if(wantW > 0 && wantH > 0 && vf_set_output_size(vh, wantW, wantH) < 0)
	{
		fprintf(stderr, "cannot set output size %dx%d: %s\n", wantW, wantH,
			vh->err[0] ? vh->err : "(no reason recorded)");
		vf_close(vh);
		return 1;
	}

	fw = vh->width;
	fh = vh->height;
	ow = vh->outW;
	oh = vh->outH;
	cap = ow * oh * 4 + AV_INPUT_BUFFER_PADDING_SIZE;
	buf = (uint8_t *)malloc(cap);
	if(!buf)
	{
		vf_close(vh);
		return 1;
	}

	printf("file      : %s\n", path);
	printf("decoded   : %dx%d\n", fw, fh);
	printf("output    : %dx%d RGBA (%d bytes/frame)\n", ow, oh, ow * oh * 4);
	printf("fps (meta): %.3f\n", vf_frame_rate(vh));
	printf("duration  : %lld ms\n", (long long)vf_duration_ms(vh));
	printf("hardware  : %s\n",
		vh->hwActive ? "d3d11va (ACTIVE)" : "software (no hw)");

	frames = 0;
	lastRc = 0;
	t0 = now_sec();
	for(i = 0; i < loops; i++)
	{
		for(;;)
		{
			rc = vf_next_frame(vh, buf, cap);
			lastRc = rc;
			if(rc <= 0)
			{
				/* A mid-stream failure must print WHY as well: this harness
				 * is how the reason strings are checked without a game. */
				if(rc < 0)
					fprintf(stderr, "decode failed: %s\n",
						vh->err[0] ? vh->err : "(no reason recorded)");
				break;
			}
			frames++;

			/* Cheap pixel sanity: sample every 97th pixel of every frame
			 * and keep per-channel ranges to prove frames are non-blank. */
			{
				int p;
				for(p = 0; p < ow * oh; p += 97)
				{
					unsigned char r = buf[p * 4 + 0];
					unsigned char g = buf[p * 4 + 1];
					unsigned char b = buf[p * 4 + 2];
					sumR += r;
					sumG += g;
					sumB += b;
					sampled++;
					if(r < minR) minR = r;
					if(r > maxR) maxR = r;
					if(g < minG) minG = g;
					if(g > maxG) maxG = g;
					if(b < minB) minB = b;
					if(b > maxB) maxB = b;
				}
			}
		}
		if(i + 1 < loops)
			vf_seek_to_start(vh);
	}
	t1 = now_sec();

	printf("frames    : %d\n", frames);
	printf("elapsed   : %.3f s\n", t1 - t0);
	printf("decode fps: %.1f\n", (t1 - t0) > 0 ? frames / (t1 - t0) : 0.0);
	printf("channels  : R[%u..%u] G[%u..%u] B[%u..%u]\n",
		minR, maxR, minG, maxG, minB, maxB);
	printf("mean      : R=%.1f G=%.1f B=%.1f over %llu samples\n",
		sampled ? (double)sumR / sampled : 0.0,
		sampled ? (double)sumG / sampled : 0.0,
		sampled ? (double)sumB / sampled : 0.0, sampled);

	free(buf);

	if(getenv("VF_DEBUG"))
		fprintf(stderr, "[vf] main: about to vf_close (frames=%d)\n",
			frames);

	vf_close(vh);

	if(getenv("VF_DEBUG"))
		fprintf(stderr, "[vf] closed cleanly\n");

	if(lastRc < 0)
		/* Ended on an error rather than on end of stream: the exit code has
		 * always said "not clean", now the reason is on stderr too. */
		return 1;

	return frames > 0 ? 0 : 1;
}

#endif /* VF_WITH_MAIN */

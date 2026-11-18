package net.wurstclient.hud2.target;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.wurstclient.hud2.nanovg.NanoVGContext;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.system.MemoryStack;

/** Small NanoVG primitives with fixed gradients and texture-color modulation. */
public final class TargetHudDrawing implements AutoCloseable
{
	private final Map<Integer, Integer> portraits = new HashMap<>();
	private long context;

	public static void gradient(NanoVGContext nvg, float x, float y, float width,
		float height, float radius, Color top, Color bottom)
	{
		if(width <= 0) return;
		long vg = nvg.getHandle();
		try(MemoryStack stack = MemoryStack.stackPush())
		{
			NVGPaint paint = NVGPaint.malloc(stack);
			NanoVG.nvgLinearGradient(vg, x, y, x, y + height,
				nvg.getColor(top), nvg.getColor(bottom), paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);
		}
	}

	public static void shadow(NanoVGContext nvg, float x, float y, float width,
		float height, float radius, float feather, Color color)
	{
		long vg = nvg.getHandle();
		try(MemoryStack stack = MemoryStack.stackPush())
		{
			NVGPaint paint = NVGPaint.malloc(stack);
			NanoVG.nvgBoxGradient(vg, x, y, width, height, radius, feather,
				nvg.getColor(color), nvg.getColor(0), paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRect(vg, x - feather, y - feather,
				width + feather * 2, height + feather * 2);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgPathWinding(vg, NanoVG.NVG_HOLE);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);
		}
	}

	public void face(NanoVGContext nvg, int texture, float textureWidth,
		float textureHeight, float u, float v, float regionWidth, float regionHeight,
		float x, float y, float size, float radius, float hurt)
	{
		if(texture <= 0) return;
		if(context != nvg.getHandle()) {portraits.clear(); context = nvg.getHandle();}
		// One portrait per card/frame, so old handles can be retired before
		// queueing the next one. Bound the cache even on busy multiplayer servers.
		if(!portraits.containsKey(texture) && portraits.size() >= 32) close();
		int image = portraits.computeIfAbsent(texture, id ->
			NanoVGGL3.nvglCreateImageFromHandle(context, id, (int)textureWidth,
				(int)textureHeight, NanoVGGL3.NVG_IMAGE_NODELETE | NanoVG.NVG_IMAGE_NEAREST));
		if(image == 0) return;
		float sx = size / regionWidth, sy = size / regionHeight;
		try(MemoryStack stack = MemoryStack.stackPush())
		{
			NVGPaint paint = NVGPaint.malloc(stack);
			NanoVG.nvgImagePattern(context, x - u * sx, y - v * sy,
				textureWidth * sx, textureHeight * sy, 0, image, 1, paint);
			float greenBlue = 1 - Math.max(0, Math.min(1, hurt / 9));
			paint.innerColor().r(1).g(greenBlue).b(greenBlue).a(1);
			paint.outerColor().set(paint.innerColor());
			NanoVG.nvgBeginPath(context);
			NanoVG.nvgRoundedRect(context, x, y, size, size, radius);
			NanoVG.nvgFillPaint(context, paint);
			NanoVG.nvgFill(context);
		}
	}

	@Override public void close()
	{
		if(NanoVGContext.get().getHandle() == context)
			for(int image : portraits.values()) NanoVG.nvgDeleteImage(context, image);
		portraits.clear();
	}
}

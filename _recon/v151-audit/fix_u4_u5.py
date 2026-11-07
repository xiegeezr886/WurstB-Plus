"""Fix U4 (shared blit texture) and U5 (lost item translation) in the 33
NanoVG builds (1.21.6-26.3 x 3 loaders)."""
import os
import sys

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
BLITTER = os.path.join(PKG, "hud2", "nanovg", "NanoVgBlitter.java")
NANOVGUI = os.path.join(PKG, "hud2", "nanovg", "NanoVGUI.java")


def roots():
    result = []
    for base in ("versions", os.path.join("fabric", "versions"),
                 os.path.join("neoforge", "versions")):
        path = os.path.join(ROOT, base)
        if os.path.isdir(path):
            for name in sorted(os.listdir(path)):
                result.append(os.path.join(path, name, "src"))
    return [r for r in result if os.path.isdir(os.path.join(r, PKG))]


def read(path):
    data = open(path, "rb").read()
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n").decode("utf-8"), eol, bom


def write(path, text, eol, bom):
    out = text.replace("\n", "\r\n").encode("utf-8") if eol == b"\r\n" \
        else text.encode("utf-8")
    if bom:
        out = b"\xef\xbb\xbf" + out
    open(path, "wb").write(out)


def extract(text, signature):
    i = text.find(signature)
    if i < 0:
        return None
    j = text.find("{", i)
    depth = 0
    for k in range(j, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return text[i:k + 1]
    return None


def replace_method(text, signature, replacement):
    old = extract(text, signature)
    if old is None:
        raise SystemExit("method not found: " + signature)
    return text.replace(old, replacement, 1)


def blitter_variant(text):
    if "graphics.getRenderState()" in text and "submitBlitToCurrentLayer" in text:
        return "1.21.10" if "singleTexture(textureView)" in text else "1.21.11"
    if "addBlitToCurrentLayer" in text:
        return "26.2" if "GpuFormat" in text else "26.1"
    if "addGuiElement" in text:
        return "26.3"
    raise SystemExit("unknown blitter variant")


NEW_FIELDS = """	private static final int MAX_SLOTS = 8;

	private static final java.util.List<Slot> slots = new java.util.ArrayList<>();
%SAMPLER_FIELD%	private static GuiRenderState currentState;
	private static int nextSlot;"""

QUEUE = """	public static synchronized void queueBlit(GuiGraphicsExtractor graphics,
		NanoVGContext ctx)
	{
		if(graphics == null || ctx == null)
			return;

		GuiRenderState state = %STATE%;
		if(state == null)
			return;

		// A new GuiRenderState means a new frame. Passes inside one frame must
		// not share a texture: a later copy would overwrite the image that an
		// earlier queued blit still references.
		if(state != currentState)
		{
			currentState = state;
			nextSlot = 0;
		}

		Slot slot = ensureSlot(nextSlot, ctx);
		if(slot == null)
			return;
		nextSlot = (nextSlot + 1) %% MAX_SLOTS;

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
%SUBMIT%
	}"""

ENSURE = """	private static Slot ensureSlot(int index, NanoVGContext ctx)
	{
		while(slots.size() <= index)
			slots.add(new Slot());

		int w = ctx.getFramebufferWidth();
		int h = ctx.getFramebufferHeight();
		int fbo = ctx.getFramebufferFbo();
		if(w <= 0 || h <= 0 || fbo == 0)
			return null;

		Slot slot = slots.get(index);
		try
		{
			if(slot.texture == null || slot.width != w || slot.height != h)
			{
				slot.close();
				slot.texture = RenderSystem.getDevice().createTexture(
					"wurst-nanovg-" + index,
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					%FORMAT%, w, h, 1, 1);
				slot.view =
					RenderSystem.getDevice().createTextureView(slot.texture);
%CONFIG%				slot.width = w;
				slot.height = h;
			}
%SAMPLER%
			GlTexture glTexture = (GlTexture)slot.texture;
			try(GlStateGuard ignored = GlStateGuard.capture())
			{
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture.glId());
				GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
				GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
				GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
					w, h);
			}
			return slot;
		}catch(Throwable t)
		{
			System.out.println("[HUD] NanoVG blit setup failed: " + t);
			t.printStackTrace();
			return null;
		}
	}

	private static final class Slot
	{
		private GpuTexture texture;
		private GpuTextureView view;
		private int width;
		private int height;

		private void close()
		{
			if(view != null)
			{
				view.close();
				view = null;
			}
			if(texture != null)
			{
				texture.close();
				texture = null;
			}
			width = 0;
			height = 0;
		}
	}"""


def build_blitter(version):
    has_sampler = version != "1.21.10"
    sampler = ""
    if has_sampler:
        sampler = """			if(sampler == null)
				sampler = RenderSystem.getDevice().createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.LINEAR, FilterMode.LINEAR, 1,
					OptionalDouble.empty());
"""
    config = ""
    if version == "1.21.10":
        config = """				slot.texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
				slot.texture.setTextureFilter(FilterMode.LINEAR, false);
"""
    texture_arg = ", sampler" if has_sampler else ""
    if version in ("1.21.10", "1.21.11"):
        state_expr = "graphics.getRenderState()"
        submit = f"""		state.submitBlitToCurrentLayer(new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(slot.view{texture_arg}),
			new Matrix3x2f(), 0, 0, guiWidth, guiHeight, 0F, 1F, 1F, 0F,
			0xFFFFFFFF, null));"""
    elif version in ("26.1", "26.2"):
        state_expr = "getState(graphics)"
        submit = f"""		state.addBlitToCurrentLayer(new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(slot.view{texture_arg}),
			new Matrix3x2f(), 0, 0, guiWidth, guiHeight, 0F, 1F, 1F, 0F,
			0xFFFFFFFF, null));"""
    else:
        state_expr = "getState(graphics)"
        submit = f"""		BlitRenderState blit = new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(slot.view{texture_arg}),
			new Matrix3x2f(), 0, 0, guiWidth, guiHeight, 0F, 1F, 1F, 0F,
			0xFFFFFFFF, null);
		state.addGuiElement(blit);"""

    fmt = "TextureFormat.RGBA8" if version in ("1.21.10", "1.21.11", "26.1") \
        else "GpuFormat.RGBA8_UNORM"

    queue = QUEUE.replace("%STATE%", state_expr).replace("%SUBMIT%", submit)
    queue = queue.replace("%%", "%")
    ensure = ENSURE.replace("%FORMAT%", fmt).replace("%CONFIG%", config) \
        .replace("%SAMPLER%", sampler)
    return queue, ensure


def patch_blitter(path, version):
    text, eol, bom = read(path)
    if "MAX_SLOTS" in text:
        return False
    if "import java.util.OptionalDouble;" not in text and version != "1.21.10":
        text = text.replace("import org.joml.Matrix3x2f;",
                            "import java.util.OptionalDouble;\n\nimport org.joml.Matrix3x2f;", 1)

    start = text.find("	private static GpuTexture texture;")
    end_marker = "	private static int textureHeight;"
    end = text.find(end_marker)
    if start < 0 or end < 0:
        raise SystemExit("field block not found in " + path)
    end += len(end_marker)
    sampler_field = "" if version == "1.21.10" else "	private static GpuSampler sampler;\n"
    text = text[:start] + NEW_FIELDS.replace("%SAMPLER_FIELD%", sampler_field) \
        + text[end:]

    queue, ensure = build_blitter(version)
    text = replace_method(text, "	public static synchronized void queueBlit", queue)
    text = replace_method(text, "	private static boolean ensureTexture", ensure)
    write(path, text, eol, bom)
    return True


def patch_nanovgui(path):
    text, eol, bom = read(path)
    if "translateX()" in text and "DeferredItem(stack, x, y, large, scissor,\n" in text:
        return False
    text = text.replace(
        "ITEMS.add(new DeferredItem(stack, x, y, large, scissor));",
        "ITEMS.add(new DeferredItem(stack, x, y, large, scissor,\n"
        "\t\t\ttranslateX, translateY));", 1)
    text = text.replace(
        "	private record DeferredItem(ItemStack stack, int x, int y, boolean large,\n"
        "\t\tfloat[] scissor)",
        "	private record DeferredItem(ItemStack stack, int x, int y, boolean large,\n"
        "		float[] scissor, float translateX, float translateY)", 1)
    old_draw = ("			RenderUtils.drawItem(graphics, item.stack(), item.x(), item.y(),\n"
                "				item.large());")
    new_draw = """			boolean translated = item.translateX() != 0
				|| item.translateY() != 0;
			if(translated)
			{
				graphics.pose().pushMatrix();
				graphics.pose().translate(item.translateX(),
					item.translateY());
			}
			try
			{
				RenderUtils.drawItem(graphics, item.stack(), item.x(),
					item.y(), item.large());
			}finally
			{
				if(translated)
					graphics.pose().popMatrix();
			}"""
    if old_draw not in text:
        raise SystemExit("drawItem block not found in " + path)
    text = text.replace(old_draw, new_draw, 1)
    write(path, text, eol, bom)
    return True


def main():
    apply = "--apply" in sys.argv
    blit = gui = 0
    for root in roots():
        blitter = os.path.join(root, BLITTER)
        if os.path.isfile(blitter):
            text, _, _ = read(blitter)
            version = blitter_variant(text)
            if apply:
                if patch_blitter(blitter, version):
                    blit += 1
            else:
                blit += 1

        nanovgui = os.path.join(root, NANOVGUI)
        if os.path.isfile(nanovgui):
            text, _, _ = read(nanovgui)
            if "item(GuiGraphicsExtractor" in text:
                if apply:
                    if patch_nanovgui(nanovgui):
                        gui += 1
                else:
                    gui += 1
    print(f"NanoVgBlitter {'patched' if apply else 'to patch'}: {blit}")
    print(f"NanoVGUI {'patched' if apply else 'to patch'}: {gui}")


if __name__ == "__main__":
    main()

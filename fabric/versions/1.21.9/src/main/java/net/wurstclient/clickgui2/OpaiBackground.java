package net.wurstclient.clickgui2;

import java.io.IOException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Blurs the freshly rendered background before any ClickGUI panels are drawn. */
public final class OpaiBackground implements AutoCloseable
{
    private net.wurstclient.util.render.V152BackdropBlur blur;
    private RenderTarget reduced;
    private int width, height;
    private boolean failed;
    private long refreshedAt;

    public void render(GuiGraphicsExtractor graphics, float partialTicks)
    {
        if(failed) return;
        var mc = WurstClient.MC;
        if(mc.level == null) return;
        var target = mc.getMainRenderTarget();
        graphics.flush();
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try(GlStateGuard ignored = GlStateGuard.capture())
        {
            float ratio = Math.min(.5F, Math.min(640F / target.width, 360F / target.height));
            int reducedWidth = Math.max(1, Math.round(target.width * ratio));
            int reducedHeight = Math.max(1, Math.round(target.height * ratio));
            if(reduced == null)
                reduced = new TextureTarget("WurstB v1.5.2", reducedWidth, reducedHeight, false);
            if(width != target.width || height != target.height)
            {
                width = target.width; height = target.height;
                refreshedAt = 0;
                if(reduced.width != reducedWidth || reduced.height != reducedHeight)
                    reduced.resize(reducedWidth, reducedHeight);
            }
            if(blur == null)
            {
                blur = new net.wurstclient.util.render.V152BackdropBlur();
            }
            com.mojang.blaze3d.opengl.GlStateManager._disableScissorTest();
            long now = System.nanoTime();
            if(now - refreshedAt >= 33_333_333L)
            {
                copy(target, reduced);
                blur.process(reduced);
                refreshedAt = now;
            }
            copy(reduced, target);
        }catch(IOException | RuntimeException e)
        {
            failed = true;
            close();
            System.err.println("[Opai ClickGUI] Background blur unavailable: " + e);
        }finally
        {
            modelView.popMatrix();
            
            RenderSystem.restoreProjectionMatrix();
            net.wurstclient.util.render.V152Gpu.bind(target, false);
        }
    }

    private static void copy(RenderTarget from, RenderTarget to)
    {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.wurstclient.util.render.V152Gpu.fbo(from));
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, net.wurstclient.util.render.V152Gpu.fbo(to));
        GL30.glBlitFramebuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }

    @Override
    public void close()
    {
        if(blur != null) { blur.close(); blur = null; }
        if(reduced != null) { reduced.destroyBuffers(); reduced = null; }
        width = height = 0;
        refreshedAt = 0;
    }
}

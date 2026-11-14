package net.wurstclient.clickgui2;

import java.io.IOException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Blurs the freshly rendered background before any ClickGUI panels are drawn. */
final class OpaiBackground implements AutoCloseable
{
    private PostChain blur;
    private RenderTarget reduced;
    private int width, height;
    private boolean failed;

    void render(GuiGraphics graphics, float partialTicks)
    {
        if(failed) return;
        var mc = WurstClient.MC;
        if(mc.level == null) return;
        var target = mc.getMainRenderTarget();
        graphics.flush();
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try(GlStateGuard ignored = GlStateGuard.capture())
        {
            int reducedWidth = Math.max(1, (target.width + 1) / 2);
            int reducedHeight = Math.max(1, (target.height + 1) / 2);
            if(reduced == null)
                reduced = new TextureTarget(reducedWidth, reducedHeight, false, false);
            if(width != target.width || height != target.height)
            {
                width = target.width; height = target.height;
                if(reduced.width != reducedWidth || reduced.height != reducedHeight)
                    reduced.resize(reducedWidth, reducedHeight, false);
                if(blur != null) blur.resize(reducedWidth, reducedHeight);
            }
            if(blur == null)
            {
                blur = new PostChain(mc.getTextureManager(), mc.getResourceManager(), reduced,
                    new ResourceLocation("wurstpenguin", "shaders/post/opai_background.json"));
                blur.resize(reducedWidth, reducedHeight);
            }
            RenderSystem.disableScissor();
            copy(target, reduced);
            blur.process(partialTicks);
            copy(reduced, target);
        }catch(IOException | RuntimeException e)
        {
            failed = true;
            close();
            System.err.println("[Opai ClickGUI] Background blur unavailable: " + e);
        }finally
        {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.bindWrite(false);
        }
    }

    private static void copy(RenderTarget from, RenderTarget to)
    {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }

    @Override
    public void close()
    {
        if(blur != null) { blur.close(); blur = null; }
        if(reduced != null) { reduced.destroyBuffers(); reduced = null; }
        width = height = 0;
    }
}

package net.wurstclient.hacks;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.render.PostEffectQueue.Effect;

public final class TargetShaderHack extends Hack implements RenderListener
{
	private final EnumSetting<Effect> effect = new EnumSetting<>("Effect",
		"Visual effect applied to the current combat target.", Effect.values(),
		Effect.OUTLINE);
	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Shows the target effect through blocks.", true);

	public TargetShaderHack()
	{
		super("TargetShader");
		setCategory(Category.RENDER);
		addSetting(effect);
		addSetting(throughWalls);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onRender(PoseStack matrices, float partialTicks)
	{
		Entity target = resolveTarget();
		if(target == null || target.isRemoved() || !target.isAlive())
			return;
		AABB box = EntityUtils.getLerpedBox(target, partialTicks).inflate(0.03);
		WURST.getPostEffectQueue().queue(effect.getSelected(),
			(taskMatrices, taskPartialTicks) -> drawMask(taskMatrices, box));
	}

    private void drawMask(PoseStack matrices, AABB box)
    {
        if(throughWalls.isChecked()) com.mojang.renderpearl.backend.opengl.GlStateManager._disableDepthTest();
        else com.mojang.renderpearl.backend.opengl.GlStateManager._enableDepthTest();
        com.mojang.renderpearl.backend.opengl.GlStateManager._disableCull();
        com.mojang.renderpearl.backend.opengl.GlStateManager._disableBlend(0);
        try(var vertices = new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096)) {
        var buffer = new com.mojang.blaze3d.vertex.BufferBuilder(vertices, 
            com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS,
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        RenderUtils.drawSolidBox(matrices, buffer, box.move(RenderUtils.getCameraPos().reverse()), Color.WHITE.getRGB());
        net.wurstclient.util.render.V152Gpu.draw(buffer.buildOrThrow());
        }
    }

	private Entity resolveTarget()
	{
		Entity target = WURST.getHax().killauraHack.getCurrentTarget();
		if(isUsable(target))
			return target;
		target = WURST.getHax().multiAuraHack.getCurrentTarget();
		if(isUsable(target))
			return target;
		if(MC.hitResult instanceof EntityHitResult hitResult
			&& isUsable(hitResult.getEntity()))
			return hitResult.getEntity();
		return null;
	}

	private boolean isUsable(Entity entity)
	{
		return entity != null && entity != MC.player && entity.level() == MC.level
			&& !entity.isRemoved() && entity.isAlive();
	}
}

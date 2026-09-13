package net.wurstclient.util.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;

public final class PostEffectQueue
{
	private final EnumMap<Effect, List<RenderTask>> tasks =
		new EnumMap<>(Effect.class);
	private final EnumSet<Effect> failedEffects = EnumSet.noneOf(Effect.class);

	public PostEffectQueue()
	{
		for(Effect effect : Effect.values())
			tasks.put(effect, new ArrayList<>());
	}

	public void queue(Effect effect, RenderTask task)
	{
		RenderSystem.assertOnRenderThread();
		tasks.get(effect).add(task);
	}

	public void flush(PoseStack matrices, float partialTicks)
	{
		RenderSystem.assertOnRenderThread();
		if(isEmpty())
			return;

		for(List<RenderTask> effectTasks : tasks.values())
			effectTasks.clear();
	}

	private boolean isEmpty()
	{
		for(List<RenderTask> effectTasks : tasks.values())
			if(!effectTasks.isEmpty())
				return false;
		return true;
	}

	public int getQueuedTaskCount()
	{
		int count = 0;
		for(List<RenderTask> effectTasks : tasks.values())
			count += effectTasks.size();
		return count;
	}

	@FunctionalInterface
	public interface RenderTask
	{
		void render(PoseStack matrices, float partialTicks);
	}

	public enum Effect
	{
		OUTLINE("outline"),
		PULSE("pulse"),
		GRADIENT("gradient"),
		SMOKE("smoke");

		private final String fileName;

		Effect(String fileName)
		{
			this.fileName = fileName;
		}

		@Override
		public String toString()
		{
			return Character.toUpperCase(fileName.charAt(0))
				+ fileName.substring(1);
		}
	}
}

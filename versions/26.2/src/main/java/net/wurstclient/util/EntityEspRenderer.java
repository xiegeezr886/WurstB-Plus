package net.wurstclient.util;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.util.RenderUtils.ColoredBox;
import net.wurstclient.util.RenderUtils.ColoredPoint;

public enum EntityEspRenderer
{
	;

	public static <E extends Entity> void render(PoseStack matrixStack,
		float partialTicks, List<E> entities, EspStyleSetting style,
		double extraSize, double fillOpacity, double outlineOpacity,
		double nearFadeDistance, boolean depthTest,
		ToIntFunction<E> colorProvider)
	{
		if(entities.isEmpty())
			return;

		int fillAlpha = toAlpha(fillOpacity);
		int outlineAlpha = toAlpha(outlineOpacity);
		ArrayList<ColoredBox> filledBoxes = style.hasBoxes() && fillAlpha > 0
			? new ArrayList<>(entities.size()) : null;
		ArrayList<ColoredBox> outlinedBoxes =
			style.hasBoxes() && outlineAlpha > 0
				? new ArrayList<>(entities.size()) : null;
		ArrayList<ColoredPoint> tracerEnds =
			style.hasLines() && outlineAlpha > 0
				? new ArrayList<>(entities.size()) : null;

		double expansion = extraSize / 2;
		for(E entity : entities)
		{
			AABB lerpedBox = EntityUtils.getLerpedBox(entity, partialTicks);
			AABB renderBox = lerpedBox.move(0, expansion, 0)
				.inflate(expansion);
			int color = colorProvider.applyAsInt(entity);
			float fade = getNearFade(entity, nearFadeDistance);
			int entityFillAlpha = Math.round(fillAlpha * fade);
			int entityOutlineAlpha = Math.round(outlineAlpha * fade);

			if(filledBoxes != null && entityFillAlpha > 0)
				filledBoxes.add(
					new ColoredBox(renderBox, withAlpha(color, entityFillAlpha)));
			if(outlinedBoxes != null && entityOutlineAlpha > 0)
				outlinedBoxes.add(
					new ColoredBox(renderBox, withAlpha(color, entityOutlineAlpha)));
			if(tracerEnds != null && entityOutlineAlpha > 0)
				tracerEnds.add(new ColoredPoint(lerpedBox.getCenter(),
					withAlpha(color, entityOutlineAlpha)));
		}

		if(filledBoxes != null)
			RenderUtils.drawSolidBoxes(matrixStack, filledBoxes, depthTest);
		if(outlinedBoxes != null)
			RenderUtils.drawOutlinedBoxes(matrixStack, outlinedBoxes, depthTest);
		if(tracerEnds != null)
			RenderUtils.drawTracers(matrixStack, partialTicks, tracerEnds,
				depthTest);
	}

	private static float getNearFade(Entity entity, double fadeDistance)
	{
		if(fadeDistance <= 0)
			return 1;

		double distance = WurstClient.MC.gameRenderer.mainCamera().position()
			.distanceTo(entity.getBoundingBox().getCenter());
		float progress = Mth.clamp((float)(distance / fadeDistance), 0, 1);
		return progress * progress * (3 - 2 * progress);
	}

	public static int getColor(Entity entity, ColorMode mode, int customColor,
		double distanceRange)
	{
		return switch(mode)
		{
			case DISTANCE -> gradientColor(1 - Mth.clamp(
				WurstClient.MC.player.distanceTo(entity) / (float)distanceRange,
				0, 1));
			case HEALTH -> entity instanceof LivingEntity living
				&& living.getMaxHealth() > 1e-5
					? gradientColor(1 - Mth.clamp(
						living.getHealth() / living.getMaxHealth(), 0, 1))
					: customColor;
			case CUSTOM -> customColor;
		};
	}

	private static int gradientColor(float danger)
	{
		float red = Mth.clamp(danger * 2, 0, 1);
		float green = Mth.clamp(2 - danger * 2, 0, 1);
		return RenderUtils.toIntColor(new float[]{red, green, 0}, 1);
	}

	private static int toAlpha(double opacity)
	{
		return (int)(Mth.clamp(opacity, 0, 1) * 255);
	}

	private static int withAlpha(int color, int alpha)
	{
		return color & 0x00FFFFFF | alpha << 24;
	}

	public enum ColorMode
	{
		DISTANCE("Distance"),
		HEALTH("Health"),
		CUSTOM("Custom");

		private final String name;

		private ColorMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

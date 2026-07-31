package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.EntityOcclusionCuller;

@SearchTags({"entity culling", "occlusion query", "AFEC", "performance"})
public final class EntityCullingHack extends Hack
	implements WorldChangeListener
{
	private final CheckboxSetting players = new CheckboxSetting("Players",
		"Uses GPU occlusion queries for other players.", true);
	private final CheckboxSetting otherEntities = new CheckboxSetting(
		"Other entities", "Uses GPU occlusion queries for non-player entities.",
		true);
	private final CheckboxSetting targets = new CheckboxSetting("Cull targets",
		"Selects which entity groups may be culled.", true)
			.withChildren(players, otherEntities);
	private final SliderSetting visibleDelay = new SliderSetting("Visible delay",
		"Delay before retesting a visible entity.", 50, 0, 500, 10,
		ValueDisplay.INTEGER.withSuffix(" ms"));
	private final SliderSetting hiddenDelay = new SliderSetting("Hidden delay",
		"Delay before retesting a hidden entity.", 250, 20, 1000, 10,
		ValueDisplay.INTEGER.withSuffix(" ms"));
	private final CheckboxSetting queryTiming = new CheckboxSetting(
		"Query timing", "Controls asynchronous query refresh intervals.", true)
			.withChildren(visibleDelay, hiddenDelay);
	private final SliderSetting minimumDistance = new SliderSetting(
		"Minimum distance", "Nearby entities are always rendered.", 4, 0, 16,
		0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private EntityOcclusionCuller culler;

	public EntityCullingHack()
	{
		super("EntityCulling");
		setCategory(Category.RENDER);
		addSetting(targets);
		addSetting(queryTiming);
		addSetting(minimumDistance);
	}

	@Override
	protected void onEnable()
	{
		culler = new EntityOcclusionCuller();
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(WorldChangeListener.class, this);
		if(culler != null)
		{
			culler.close();
			culler = null;
		}
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		if(culler != null)
			culler.close();
		culler = world == null ? null : new EntityOcclusionCuller();
	}

	public boolean shouldCull(Entity entity, double cameraX, double cameraY,
		double cameraZ, float partialTicks, PoseStack poseStack)
	{
		if(culler == null || MC.player == null || entity == MC.player
			|| entity == MC.getCameraEntity() || entity.isCurrentlyGlowing()
			|| !targets.isChecked())
			return false;
		if(entity instanceof Player ? !players.isChecked()
			: !otherEntities.isChecked())
			return false;
		if(entity.distanceToSqr(MC.player) < minimumDistance.getValueSq())
			return false;

		long visibleMillis = queryTiming.isChecked()
			? Math.round(visibleDelay.getValue()) : 50;
		long hiddenMillis = queryTiming.isChecked()
			? Math.round(hiddenDelay.getValue()) : 250;
		return culler.isOccluded(entity, cameraX, cameraY, cameraZ, partialTicks,
			poseStack, visibleMillis, hiddenMillis);
	}
}

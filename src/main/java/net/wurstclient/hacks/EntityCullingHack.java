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

	/**
	 * 关功能 / panic 都走这里。查询对象和单位立方体 VBO 都由
	 * {@link EntityOcclusionCuller#close()} 释放；它不在渲染线程上时会自己排回渲染
	 * 线程，所以这里不用关心调用线程。
	 *
	 * <p>{@code WurstClient.shutdown()} 故意不关 culler：那是 JVM 关服钩子的线程，
	 * 在那里发 GL 调用不安全。功能还开着就关服时，残留的 query / VBO 会随着 GL
	 * 上下文一起销毁——这正是这条路径的兜底，不需要额外处理。
	 */
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

	/**
	 * 进出世界和切换维度都会触发（进世界/换维度是 non-null，退出世界是 null）。
	 * 跨世界时实体对象会整体替换，而 culler 里的 {@code queries} 用实体做 key、是强
	 * 引用，所以这里必须先 {@link EntityOcclusionCuller#reset()} 清干净再换新实例，
	 * 否则旧世界的实体会一直被引用住。
	 */
	@Override
	public void onWorldChange(ClientLevel world)
	{
		if(culler != null)
			culler.reset();
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

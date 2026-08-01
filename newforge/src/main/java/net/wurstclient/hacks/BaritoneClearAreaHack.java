package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"baritone clear area", "area clear", "baritone excavator",
	"clear area", "baritone clear"})
@DontSaveState
public final class BaritoneClearAreaHack extends Hack
	implements UpdateListener, RightClickListener, RenderListener
{
	private static final AABB BLOCK_BOX =
		new AABB(1 / 16.0, 1 / 16.0, 1 / 16.0, 15 / 16.0, 15 / 16.0,
			15 / 16.0);

	private BlockPos corner1;
	private BlockPos corner2;
	private boolean started;

	public BaritoneClearAreaHack()
	{
		super("BaritoneClearArea");
		setCategory(Category.BLOCKS);
	}

	@Override
	protected boolean canEnable()
	{
		return BaritoneUtils.IS_AVAILABLE && MC.player != null;
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RightClickListener.class, this);
		EVENTS.add(RenderListener.class, this);
		corner1 = null;
		corner2 = null;
		started = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		BaritoneUtils.stop();
		corner1 = null;
		corner2 = null;
	}

	@Override
	public void onUpdate()
	{
		if(!BaritoneUtils.IS_AVAILABLE)
		{
			setEnabled(false);
			return;
		}

		if(corner1 != null && corner2 != null && !started)
		{
			BaritoneUtils.clearArea(corner1, corner2);
			started = true;
		}
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(started)
			return;

		if(!(MC.hitResult instanceof BlockHitResult bHitResult)
			|| bHitResult.getType() != HitResult.Type.BLOCK)
			return;

		BlockPos pos = bHitResult.getBlockPos();

		if(corner1 == null)
			corner1 = pos;
		else if(corner2 == null)
			corner2 = pos;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(corner1 != null)
			RenderUtils.drawOutlinedBox(matrixStack,
				BLOCK_BOX.move(corner1), 0xFF00FF00, false);

		if(corner2 != null)
			RenderUtils.drawOutlinedBox(matrixStack,
				BLOCK_BOX.move(corner2), 0xFFFF0000, false);

		if(corner1 != null && corner2 != null && !started)
			RenderUtils.drawOutlinedBox(matrixStack,
				new AABB(corner1, corner2).inflate(1), 0x40FFFF00, true);
	}

	@Override
	public String getRenderName()
	{
		if(isEnabled() && started)
			return getName() + " [Clearing]";
		if(isEnabled() && corner1 != null && corner2 == null)
			return getName() + " [Set corner 2]";
		return getName();
	}
}

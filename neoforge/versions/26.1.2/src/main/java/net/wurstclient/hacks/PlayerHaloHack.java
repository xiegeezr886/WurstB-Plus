package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.CameraType;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.render.PlayerHaloRenderer;

@SearchTags({"player halo", "halo", "head halo"})
public final class PlayerHaloHack extends Hack implements RenderListener
{
	public PlayerHaloHack()
	{
		super("PlayerHalo");
		setCategory(Category.RENDER);
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
	public void onRender(PoseStack PoseStack, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		WURST.getGui().updateColors();
		int color = WURST.getGui().getTheme().accent(1);
		boolean renderLocalPlayer =
			MC.options.getCameraType() != CameraType.FIRST_PERSON;
		PlayerHaloRenderer.render(PoseStack,
			WURST.getEntitySnapshotManager().getCurrent().players(), MC.player,
			partialTicks, color, renderLocalPlayer);
	}
}

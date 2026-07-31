package net.wurstclient.hud2.elements;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.MapColor;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.util.EntitySnapshotManager.Snapshot;
import net.wurstclient.util.RenderUtils;

public final class MinimapHudElement extends HudElement
	implements UpdateListener
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int MAP_SIZE = 96;
	private static final int PADDING = 5;
	private static final int WIDTH = MAP_SIZE + PADDING * 2;
	private static final int HEIGHT = WIDTH;
	private static final int MAP_RADIUS = MAP_SIZE / 2;
	private static final int TILE_REFRESH_BUDGET = 6;

	private final MinimapTerrainCache terrain = new MinimapTerrainCache();
	private DynamicTexture texture;
	private Identifier textureLocation;
	private int centerX = Integer.MIN_VALUE;
	private int centerZ = Integer.MIN_VALUE;

	public MinimapHudElement()
	{
		super("minimap", "小地图");
	}

	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}

	@Override
	public void onEnable(HudManager manager)
	{
		WURST.getEventManager().add(UpdateListener.class, this);
		invalidateTerrain();
	}

	@Override
	public void onDisable(HudManager manager)
	{
		WURST.getEventManager().remove(UpdateListener.class, this);
		releaseTexture();
		invalidateTerrain();
	}

	@Override
	public void onUpdate()
	{
		ClientLevel level = WurstClient.MC.level;
		LocalPlayer player = WurstClient.MC.player;
		if(level == null || player == null)
		{
			invalidateTerrain();
			return;
		}

		int playerX = Mth.floor(player.getX());
		int playerZ = Mth.floor(player.getZ());
		boolean tilesChanged = terrain.refreshVisible(level, playerX, playerZ,
			MAP_RADIUS, level.getGameTime(), TILE_REFRESH_BUDGET);
		if(texture == null || playerX != centerX || playerZ != centerZ
			|| tilesChanged)
			composeTerrain(playerX, playerZ);
	}

	@Override
	public int getWidth()
	{
		return WIDTH;
	}

	@Override
	public int getHeight()
	{
		return HEIGHT;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, float partialTicks)
	{
		LocalPlayer player = WurstClient.MC.player;
		ClientLevel level = WurstClient.MC.level;
		if(player != null && level != null && texture == null)
		{
			int playerX = Mth.floor(player.getX());
			int playerZ = Mth.floor(player.getZ());
			terrain.refreshVisible(level, playerX, playerZ, MAP_RADIUS,
				level.getGameTime(), TILE_REFRESH_BUDGET * 2);
			composeTerrain(playerX, playerZ);
		}

		drawFrame(graphics, x, y);
		if(textureLocation != null)
			graphics.blit(textureLocation, x + PADDING, y + PADDING, 0, 0,
				MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);

		if(player != null)
		{
			drawChunkGrid(graphics, player, x, y);
			drawEntities(graphics, player, x, y, partialTicks);
			drawPlayerArrow(graphics, player, x, y);
		}

		drawCompass(graphics, x, y);
		FlatRenderer.drawRoundedOutline(graphics, x + PADDING - 1,
			y + PADDING - 1, x + PADDING + MAP_SIZE + 1,
			y + PADDING + MAP_SIZE + 1, MAP_RADIUS + 1, 0x72006366);
	}

	private void drawFrame(GuiGraphicsExtractor graphics, int x, int y)
	{
		FlatRenderer.fillRoundedRect(graphics, x + 1, y + 3, x + WIDTH - 1,
			y + HEIGHT, WIDTH / 2, 0x68000000);
		FlatRenderer.fillRoundedRect(graphics, x + 1, y + 1, x + WIDTH - 1,
			y + WIDTH - 1, WIDTH / 2, 0xF0080A0D);
		FlatRenderer.fillRoundedRect(graphics, x + PADDING - 1,
			y + PADDING - 1, x + PADDING + MAP_SIZE + 1,
			y + PADDING + MAP_SIZE + 1, MAP_RADIUS + 1, 0xFF050607);
	}

	private void composeTerrain(int playerX, int playerZ)
	{
		ensureTexture();
		NativeImage image = texture == null ? null : texture.getPixels();
		if(image == null)
			return;

		for(int pixelZ = 0; pixelZ < MAP_SIZE; pixelZ++)
			for(int pixelX = 0; pixelX < MAP_SIZE; pixelX++)
			{
				if(!isInsideMap(pixelX, pixelZ))
				{
					image.setPixel(pixelX, pixelZ, 0);
					continue;
				}
				int worldX = playerX + pixelX - MAP_RADIUS;
				int worldZ = playerZ + pixelZ - MAP_RADIUS;
				image.setPixel(pixelX, pixelZ,
					toNativeColor(terrain.colorAt(worldX, worldZ)));
			}

		texture.upload();
		centerX = playerX;
		centerZ = playerZ;
	}

	private void ensureTexture()
	{
		if(texture != null)
			return;
		texture = new DynamicTexture(() -> "wurstb_minimap",
			MAP_SIZE, MAP_SIZE, true);
		textureLocation = Identifier.fromNamespaceAndPath("wurst",
			"wurstb_minimap");
		WurstClient.MC.getTextureManager().register(textureLocation, texture);
	}

	private void releaseTexture()
	{
		if(textureLocation != null)
			WurstClient.MC.getTextureManager().release(textureLocation);
		texture = null;
		textureLocation = null;
	}

	private void invalidateTerrain()
	{
		terrain.clear();
		centerX = Integer.MIN_VALUE;
		centerZ = Integer.MIN_VALUE;
	}

	private void drawChunkGrid(GuiGraphicsExtractor graphics, LocalPlayer player, int x,
		int y)
	{
		int playerX = Mth.floor(player.getX());
		int playerZ = Mth.floor(player.getZ());
		int centerPixelX = x + PADDING + MAP_RADIUS;
		int centerPixelY = y + PADDING + MAP_RADIUS;
		int gridRadius = MAP_RADIUS - 2;
		int gridColor = 0x21000000;

		int firstBoundaryX = Math.floorDiv(playerX - MAP_RADIUS, 16) * 16;
		for(int worldX = firstBoundaryX; worldX <= playerX + MAP_RADIUS;
			worldX += 16)
		{
			int offset = worldX - playerX;
			int halfSpan = circleHalfSpan(offset, gridRadius);
			if(halfSpan <= 0)
				continue;
			int lineX = centerPixelX + offset;
			graphics.fill(lineX, centerPixelY - halfSpan, lineX + 1,
				centerPixelY + halfSpan + 1, gridColor);
		}

		int firstBoundaryZ = Math.floorDiv(playerZ - MAP_RADIUS, 16) * 16;
		for(int worldZ = firstBoundaryZ; worldZ <= playerZ + MAP_RADIUS;
			worldZ += 16)
		{
			int offset = worldZ - playerZ;
			int halfSpan = circleHalfSpan(offset, gridRadius);
			if(halfSpan <= 0)
				continue;
			int lineY = centerPixelY + offset;
			graphics.fill(centerPixelX - halfSpan, lineY,
				centerPixelX + halfSpan + 1, lineY + 1, gridColor);
		}
	}

	private void drawEntities(GuiGraphicsExtractor graphics, LocalPlayer player, int x,
		int y, float partialTicks)
	{
		Snapshot snapshot = WURST.getEntitySnapshotManager().getCurrent();
		double playerX = Mth.lerp(partialTicks, player.xOld, player.getX());
		double playerZ = Mth.lerp(partialTicks, player.zOld, player.getZ());
		for(Entity entity : snapshot.entities())
		{
			if(entity == player || entity.isRemoved())
				continue;
			int color = getEntityColor(entity);
			if(color == 0)
				continue;

			double entityX = Mth.lerp(partialTicks, entity.xOld, entity.getX());
			double entityZ = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
			double deltaX = entityX - playerX;
			double deltaZ = entityZ - playerZ;
			if(deltaX * deltaX + deltaZ * deltaZ
				> (MAP_RADIUS - 5) * (MAP_RADIUS - 5))
				continue;

			int pointX = x + PADDING + MAP_RADIUS + (int)Math.round(deltaX);
			int pointY = y + PADDING + MAP_RADIUS + (int)Math.round(deltaZ);
			int alpha = Math.abs(entity.getY() - player.getY()) > 16 ? 145 : 235;
			int outerRadius = entity instanceof Player ? 3 : 2;
			int innerRadius = entity instanceof Player ? 2 : 1;
			FlatRenderer.fillRoundedRect(graphics, pointX - outerRadius,
				pointY - outerRadius, pointX + outerRadius + 1,
				pointY + outerRadius + 1, outerRadius, 0xB0000000);
			FlatRenderer.fillRoundedRect(graphics, pointX - innerRadius,
				pointY - innerRadius, pointX + innerRadius + 1,
				pointY + innerRadius + 1, innerRadius,
				color & 0x00FFFFFF | alpha << 24);
		}
	}

	private int getEntityColor(Entity entity)
	{
		if(WURST.getFriends().isFriend(entity))
			return 0xFF4DA3FF;
		if(entity instanceof Player)
			return 0xFFFF4D5E;
		if(entity instanceof Enemy)
			return 0xFFFF9A3D;
		if(entity instanceof Animal || entity instanceof AmbientCreature
			|| entity instanceof WaterAnimal)
			return 0xFF58C878;
		if(entity instanceof ItemEntity)
			return 0xFFFFD35A;
		if(entity instanceof LivingEntity)
			return 0xFFB8C0CA;
		return 0;
	}

	private void drawPlayerArrow(GuiGraphicsExtractor graphics, LocalPlayer player,
		int x, int y)
	{
		float centerX = x + PADDING + MAP_RADIUS;
		float centerY = y + PADDING + MAP_RADIUS;
		double yaw = Math.toRadians(player.getYRot());
		float forwardX = (float)-Math.sin(yaw);
		float forwardY = (float)Math.cos(yaw);
		float rightX = forwardY;
		float rightY = -forwardX;
		float backX = centerX - forwardX * 4;
		float backY = centerY - forwardY * 4;
		float[][] arrow = {
			{centerX + forwardX * 7, centerY + forwardY * 7},
			{backX - rightX * 3, backY - rightY * 3},
			{centerX - forwardX, centerY - forwardY},
			{backX + rightX * 3, backY + rightY * 3}};
		WURST.getGui().updateColors();
		RenderUtils.fillQuads2D(graphics, arrow,
			WURST.getGui().getTheme().accent(1));
		RenderUtils.drawLineStrip2D(graphics, arrow, 0xD0000000);
	}

	private void drawCompass(GuiGraphicsExtractor graphics, int x, int y)
	{
		Font font = WurstClient.MC.font;
		int accent = WURST.getGui().getTheme().accent(1);
		graphics.centeredText(font, "N", x + WIDTH / 2, y + 6,
			accent);
		graphics.centeredText(font, "S", x + WIDTH / 2,
			y + WIDTH - 15, 0xD8E8EDF2);
		graphics.text(font, "W", x + 6, y + WIDTH / 2 - 4,
			0xD8E8EDF2, false);
		graphics.text(font, "E", x + WIDTH - 12,
			y + WIDTH / 2 - 4, 0xD8E8EDF2, false);
	}

	static int circleHalfSpan(int offset, int radius)
	{
		if(Math.abs(offset) >= radius)
			return 0;
		return (int)Math.floor(Math.sqrt(radius * radius - offset * offset));
	}

	static boolean isInsideMap(int pixelX, int pixelZ)
	{
		double deltaX = pixelX + 0.5 - MAP_RADIUS;
		double deltaZ = pixelZ + 0.5 - MAP_RADIUS;
		return deltaX * deltaX + deltaZ * deltaZ
			<= (MAP_RADIUS - 0.5) * (MAP_RADIUS - 0.5);
	}

	static MapColor.Brightness brightnessFor(int height, int northHeight)
	{
		return MinimapTerrainCache.brightnessFor(height, northHeight);
	}

	static int toNativeColor(int argb)
	{
		return argb & 0xFF00FF00 | argb >> 16 & 0xFF
			| (argb & 0xFF) << 16;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		HudElementConfig config = new HudElementConfig(
			HudElementConfig.HORIZONTAL_RIGHT,
			HudElementConfig.VERTICAL_TOP, 8, 160);
		config.setEnabled(false);
		return config;
	}
}

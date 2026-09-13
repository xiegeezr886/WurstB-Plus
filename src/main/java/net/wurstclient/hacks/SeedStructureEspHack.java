/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashSet;
import java.util.List;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.seed.SeedEntry;
import net.wurstclient.seed.SeedStore;
import net.wurstclient.seed.structure.StructureFinder;
import net.wurstclient.seed.structure.StructureHit;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockVertexCompiler;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EasyVertexBuffer;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;

/**
 * Renders the chunks that the current seed can place structures in, so that the
 * player can go there without searching the world first.
 *
 * <p>
 * Prediction is cheap but the result is only usable for chunks the client has
 * already loaded, because the surface height of an unloaded chunk is unknown.
 * The candidates are therefore recalculated every second and the vertex buffer
 * is only rebuilt when the shown blocks changed.
 */
@SearchTags({"structure esp", "seed structure", "structure finder", "种子结构"})
public final class SeedStructureEspHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final int UPDATE_INTERVAL = 20;
	private static final int NO_SEED_WARNING_INTERVAL = 100;
	private static final float[] COLOR = {0.2F, 0.85F, 1.0F};
	
	private final SliderSetting range = new SliderSetting("Range",
		"Chunk radius around the player to look for structures in.", 16, 1, 64,
		1, ValueDisplay.INTEGER);
	
	private final CheckboxSetting showWrongBiome = new CheckboxSetting(
		"Show Wrong Biome",
		"Also show candidates whose biome does not allow the structure.", false);
	
	private final StructureFinder finder = new StructureFinder();
	private final HashSet<BlockPos> shown = new HashSet<>();
	
	private EasyVertexBuffer vertexBuffer;
	private RegionPos bufferRegion;
	private boolean bufferUpToDate;
	private int updateCooldown;
	private int noSeedTicks;
	
	public SeedStructureEspHack()
	{
		super("SeedStructureESP");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(showWrongBiome);
	}
	
	@Override
	public String getRenderName()
	{
		if(shown.isEmpty())
			return getName();
		
		return getName() + " (" + shown.size() + ")";
	}
	
	@Override
	protected void onEnable()
	{
		noSeedTicks = 0;
		updateCooldown = 0;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		ChatUtils.message("Structure ESP: " + finder.describeSources());
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		finder.clear();
		shown.clear();
		closeBuffer();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;
		
		SeedEntry entry = SeedStore.get().current();
		
		if(entry == null)
		{
			showNothing();
			updateCooldown = 0;
			
			if(++noSeedTicks >= NO_SEED_WARNING_INTERVAL)
			{
				noSeedTicks = 0;
				ChatUtils.warning(
					"No seed set. Use .seed set <seed> to enable this hack.");
			}
			
			return;
		}
		
		noSeedTicks = 0;
		
		if(updateCooldown > 0)
		{
			updateCooldown--;
			return;
		}
		
		updateCooldown = UPDATE_INTERVAL;
		
		ChunkPos center = new ChunkPos(MC.player.blockPosition());
		List<StructureHit> hits = finder.find(center, range.getValueI(),
			!showWrongBiome.isChecked());
		
		HashSet<BlockPos> newShown = collectSurfaceBlocks(hits);
		
		if(shown.equals(newShown))
			return;
		
		shown.clear();
		shown.addAll(newShown);
		bufferUpToDate = false;
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		RegionPos region = RenderUtils.getCameraRegion();
		
		// rebuild only when the structures changed or the camera left the
		// region
		if(!bufferUpToDate || !region.equals(bufferRegion))
			rebuildBuffer(region);
		
		if(vertexBuffer == null || bufferRegion == null)
			return;
		
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GlConst.GL_ALWAYS);
		
		RenderUtils.setShaderColor(COLOR, 0.5F);
		
		matrixStack.pushPose();
		RenderUtils.applyRegionalRenderOffset(matrixStack, bufferRegion);
		
		vertexBuffer.draw(matrixStack, WurstRenderLayers.ESP_QUADS);
		
		matrixStack.popPose();
		
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}
	
	// ------------------------------------------------------------------
	// prediction
	// ------------------------------------------------------------------
	
	/**
	 * Turns the predicted candidates into the blocks that should be drawn.
	 *
	 * <p>
	 * Candidates in chunks the client has not loaded yet are skipped, since
	 * their surface height is unknown and drawing them at y=0 would be
	 * misleading. Each candidate gets a three block tall marker at the center
	 * of its chunk.
	 */
	private HashSet<BlockPos> collectSurfaceBlocks(List<StructureHit> hits)
	{
		HashSet<BlockPos> blocks = new HashSet<>();
		
		for(StructureHit hit : hits)
		{
			if(!MC.level.hasChunk(hit.chunkX(), hit.chunkZ()))
				continue;
			
			int x = hit.blockX();
			int z = hit.blockZ();
			int y = MC.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			
			blocks.add(new BlockPos(x, y - 1, z));
			blocks.add(new BlockPos(x, y, z));
			blocks.add(new BlockPos(x, y + 1, z));
		}
		
		return blocks;
	}
	
	private void showNothing()
	{
		if(!shown.isEmpty())
		{
			shown.clear();
			bufferUpToDate = false;
		}
	}
	
	// ------------------------------------------------------------------
	// rendering
	// ------------------------------------------------------------------
	
	private void closeBuffer()
	{
		if(vertexBuffer != null)
			vertexBuffer.close();
		
		vertexBuffer = null;
		bufferRegion = null;
	}
	
	private void rebuildBuffer(RegionPos region)
	{
		closeBuffer();
		
		bufferRegion = region;
		bufferUpToDate = true;
		
		if(shown.isEmpty())
			return;
		
		List<int[]> vertices = BlockVertexCompiler.compile(shown);
		
		vertexBuffer = EasyVertexBuffer.createAndUpload(Mode.QUADS,
			DefaultVertexFormat.POSITION_COLOR, buffer -> {
				for(int[] vertex : vertices)
					buffer.vertex(vertex[0] - region.x(), vertex[1],
						vertex[2] - region.z()).color(0xFFFFFFFF).endVertex();
			});
	}
}

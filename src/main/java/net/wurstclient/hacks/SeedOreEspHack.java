/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.seed.OrePredictor;
import net.wurstclient.seed.SeedEntry;
import net.wurstclient.seed.SeedMineJob;
import net.wurstclient.seed.SeedStore;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockVertexCompiler;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EasyVertexBuffer;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;

/**
 * Renders the ores that are predicted from the current seed and can hand the
 * predicted positions to {@link SeedMineJob}, which lets Baritone walk to them.
 *
 * <p>
 * Prediction is expensive, so the result of every chunk is cached until the
 * player moves far enough away from it. Only a few missing chunks are predicted
 * per tick and the vertex buffer is only rebuilt when the shown ores changed.
 */
@SearchTags({"seed ore esp", "seed xray", "ore esp", "seedmine", "种子矿透"})
public final class SeedOreEspHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final int MAX_MINE_TARGETS = 128;
	private static final int MAX_PREDICTIONS_PER_TICK = 8;
	private static final int NO_SEED_WARNING_INTERVAL = 100;
	private static final float[] ESP_COLOR = {0.25F, 1F, 0.85F};
	
	private final SliderSetting range = new SliderSetting("Range",
		"Chunk radius around the player to predict ores in.", 8, 1, 16, 1,
		ValueDisplay.INTEGER);
	
	private final CheckboxSetting autoMine = new CheckboxSetting("Auto Mine",
		"Let Baritone walk to the predicted ores automatically.", false);
	
	private final CheckboxSetting diamondOre =
		new CheckboxSetting("Diamond", "Show diamond ore.", true);
	
	private final CheckboxSetting ancientDebris =
		new CheckboxSetting("Ancient Debris", "Show ancient debris.", true);
	
	private final CheckboxSetting emeraldOre =
		new CheckboxSetting("Emerald", "Show emerald ore.", true);
	
	private final CheckboxSetting goldOre =
		new CheckboxSetting("Gold", "Show gold ore.", true);
	
	private final CheckboxSetting ironOre =
		new CheckboxSetting("Iron", "Show iron ore.", false);
	
	private final CheckboxSetting redstoneOre =
		new CheckboxSetting("Redstone", "Show redstone ore.", false);
	
	private final CheckboxSetting lapisOre =
		new CheckboxSetting("Lapis", "Show lapis lazuli ore.", false);
	
	private final CheckboxSetting copperOre =
		new CheckboxSetting("Copper", "Show copper ore.", false);
	
	private final CheckboxSetting coalOre =
		new CheckboxSetting("Coal", "Show coal ore.", false);
	
	private final CheckboxSetting quartzOre =
		new CheckboxSetting("Quartz", "Show nether quartz ore.", false);
	
	private final SeedMineJob job = new SeedMineJob();
	
	private final HashMap<Long, Map<BlockPos, String>> chunkCache =
		new HashMap<>();
	private final HashSet<BlockPos> shown = new HashSet<>();
	
	private OrePredictor predictor;
	private EasyVertexBuffer vertexBuffer;
	private RegionPos bufferRegion;
	private boolean bufferUpToDate;
	private boolean predictionFailed;
	private int noSeedTicks;
	
	public SeedOreEspHack()
	{
		super("SeedOreESP");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(autoMine);
		addSetting(diamondOre);
		addSetting(ancientDebris);
		addSetting(goldOre);
		addSetting(ironOre);
		addSetting(redstoneOre);
		addSetting(lapisOre);
		addSetting(emeraldOre);
		addSetting(copperOre);
		addSetting(coalOre);
		addSetting(quartzOre);
	}
	
	@Override
	public String getRenderName()
	{
		if(!job.isActive())
			return getName();
		
		return getName() + " [" + job.remaining() + " left]";
	}
	
	@Override
	protected void onEnable()
	{
		clearCache();
		predictionFailed = false;
		noSeedTicks = 0;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		job.cancel();
		predictor = null;
		clearCache();
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
			
			if(++noSeedTicks >= NO_SEED_WARNING_INTERVAL)
			{
				noSeedTicks = 0;
				ChatUtils.warning(
					"No seed set. Use .seed set <seed> to enable this hack.");
			}
			
			return;
		}
		
		noSeedTicks = 0;
		
		String dimensionId = MC.level.dimension().location().toString();
		
		// a new seed, a new dimension or a new world invalidates everything
		if(predictor == null || predictor.seed() != entry.seed
			|| !dimensionId.equals(predictor.dimensionId()))
		{
			predictor = new OrePredictor(entry.seed, dimensionId,
				new WorldOreContext());
			predictionFailed = false;
			job.cancel();
			clearCache();
		}
		
		BlockPos feet = MC.player.blockPosition();
		ChunkPos center = new ChunkPos(feet);
		int radius = range.getValueI();
		
		boolean cacheChanged = false;
		
		if(!predictionFailed)
		{
			cacheChanged = predictChunks(center, radius);
			cacheChanged |= pruneCache(center, radius);
		}
		
		if(cacheChanged)
			updateShown();
		
		updateMining(feet);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		RegionPos region = RenderUtils.getCameraRegion();
		
		// rebuild only when the ores changed or the camera left the region
		if(!bufferUpToDate || !region.equals(bufferRegion))
			rebuildBuffer(region);
		
		if(vertexBuffer == null || bufferRegion == null)
			return;
		
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GlConst.GL_ALWAYS);
		
		RenderUtils.setShaderColor(ESP_COLOR, 0.5F);
		
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
	 * Predicts a few of the missing chunks around the player.
	 *
	 * @return whether the cache changed.
	 */
	private boolean predictChunks(ChunkPos center, int radius)
	{
		ArrayList<ChunkPos> missing = new ArrayList<>();
		
		for(int dx = -radius; dx <= radius; dx++)
			for(int dz = -radius; dz <= radius; dz++)
			{
				ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
				
				if(!chunkCache.containsKey(pos.toLong()))
					missing.add(pos);
			}
		
		if(missing.isEmpty())
			return false;
		
		missing.sort(Comparator
			.comparingDouble((ChunkPos pos) -> distanceSq(pos, center)));
		
		int budget = Math.min(missing.size(), MAX_PREDICTIONS_PER_TICK);
		
		for(int i = 0; i < budget; i++)
		{
			ChunkPos pos = missing.get(i);
			Map<BlockPos, String> ores = null;
			
			try
			{
				ores = predictor.predictChunkWithIds(pos);
			}catch(Exception e)
			{
				// the predictor or its context is unusable, so stop instead of
				// printing one stack trace per chunk
				e.printStackTrace();
				predictionFailed = true;
				ChatUtils.error(
					"种子矿透: 矿物预测失败，已停止预测（详见日志）。");
				return false;
			}
			
			if(ores == null)
				ores = Collections.emptyMap();
			
			chunkCache.put(pos.toLong(), ores);
		}
		
		return true;
	}
	
	/**
	 * Forgets the chunks that are no longer near the player.
	 *
	 * @return whether the cache changed.
	 */
	private boolean pruneCache(ChunkPos center, int radius)
	{
		int limit = radius + 2;
		boolean changed = false;
		var iterator = chunkCache.keySet().iterator();
		
		while(iterator.hasNext())
		{
			ChunkPos pos = new ChunkPos(iterator.next());
			
			if(Math.abs(pos.x - center.x) > limit
				|| Math.abs(pos.z - center.z) > limit)
			{
				iterator.remove();
				changed = true;
			}
		}
		
		return changed;
	}
	
	private double distanceSq(ChunkPos pos, ChunkPos center)
	{
		double dx = pos.x - center.x;
		double dz = pos.z - center.z;
		return dx * dx + dz * dz;
	}
	
	/**
	 * Rebuilds the set of ores that the settings allow to be shown.
	 */
	private void updateShown()
	{
		HashSet<BlockPos> newShown = new HashSet<>();
		
		for(Map<BlockPos, String> ores : chunkCache.values())
			for(Map.Entry<BlockPos, String> ore : ores.entrySet())
				if(isOreEnabled(ore.getValue()))
					newShown.add(ore.getKey());
		
		if(shown.equals(newShown))
			return;
		
		shown.clear();
		shown.addAll(newShown);
		bufferUpToDate = false;
	}
	
	private boolean isOreEnabled(String id)
	{
		if(id == null)
			return false;
		
		String ore = id.toLowerCase();
		
		if(ore.contains("diamond"))
			return diamondOre.isChecked();
		
		if(ore.contains("ancient_debris"))
			return ancientDebris.isChecked();
		
		if(ore.contains("gold"))
			return goldOre.isChecked();
		
		if(ore.contains("iron"))
			return ironOre.isChecked();
		
		if(ore.contains("redstone"))
			return redstoneOre.isChecked();
		
		if(ore.contains("lapis"))
			return lapisOre.isChecked();
		
		if(ore.contains("emerald"))
			return emeraldOre.isChecked();
		
		if(ore.contains("copper"))
			return copperOre.isChecked();
		
		if(ore.contains("coal"))
			return coalOre.isChecked();
		
		if(ore.contains("quartz"))
			return quartzOre.isChecked();
		
		return false;
	}
	
	// ------------------------------------------------------------------
	// mining
	// ------------------------------------------------------------------
	
	private void updateMining(BlockPos feet)
	{
		if(!autoMine.isChecked())
		{
			if(job.isActive())
				job.cancel();
			
			return;
		}
		
		if(!job.isActive())
		{
			List<BlockPos> targets = nearestTargets(feet, MAX_MINE_TARGETS);
			
			if(!targets.isEmpty())
				job.start(targets, feet);
		}
		
		job.tick(feet, pos -> MC.level.getBlockState(pos).isAir());
	}
	
	private List<BlockPos> nearestTargets(BlockPos feet, int limit)
	{
		ArrayList<BlockPos> targets = new ArrayList<>();
		
		for(BlockPos pos : shown)
			if(isMinable(pos))
				targets.add(pos);
		
		targets.sort(Comparator
			.comparingDouble((BlockPos pos) -> pos.distSqr(feet)));
		
		if(targets.size() > limit)
			return new ArrayList<>(targets.subList(0, limit));
		
		return targets;
	}
	
	/**
	 * Predicted ores in chunks the client has not loaded yet read as air, so
	 * they are left alone until the player gets closer.
	 */
	private boolean isMinable(BlockPos pos)
	{
		if(!MC.level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ())))
			return false;
		
		return !MC.level.getBlockState(pos).isAir();
	}
	
	private void showNothing()
	{
		if(predictor != null)
		{
			predictor = null;
			job.cancel();
		}
		
		if(!shown.isEmpty())
		{
			shown.clear();
			bufferUpToDate = false;
		}
	}
	
	// ------------------------------------------------------------------
	// rendering
	// ------------------------------------------------------------------
	
	private void clearCache()
	{
		chunkCache.clear();
		shown.clear();
		bufferUpToDate = false;
		closeBuffer();
	}
	
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
		
		ArrayList<int[]> vertices = BlockVertexCompiler.compile(shown);
		
		vertexBuffer = EasyVertexBuffer.createAndUpload(Mode.QUADS,
			DefaultVertexFormat.POSITION_COLOR, buffer -> {
				for(int[] vertex : vertices)
					buffer.vertex(vertex[0] - region.x(), vertex[1],
						vertex[2] - region.z()).color(0xFFFFFFFF).endVertex();
			});
	}
	
	/**
	 * Reads the world data that {@link OrePredictor} needs.
	 */
	private final class WorldOreContext implements OrePredictor.OreContext
	{
		@Override
		public int minY()
		{
			return MC.level == null ? 0 : MC.level.getMinBuildHeight();
		}
		
		@Override
		public int maxY()
		{
			// inclusive: OreHeight resolves below-top against this value
			return MC.level == null ? 0
				: MC.level.getMaxBuildHeight() - 1;
		}
		

		@Override
		public boolean isAir(int x, int y, int z)
		{
			if(MC.level == null)
				return true;
			
			return MC.level.getBlockState(new BlockPos(x, y, z)).isAir();
		}
	}
	
}

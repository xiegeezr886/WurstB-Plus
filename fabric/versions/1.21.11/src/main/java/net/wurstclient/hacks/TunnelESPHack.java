/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/* Ported from Meteor Client (GPL-3.0). */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"tunnel esp", "tunnels", "caves"})
public final class TunnelESPHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private static final Direction[] DIRECTIONS = {Direction.EAST,
		Direction.NORTH, Direction.SOUTH, Direction.WEST};

	private static final int DOWN = 1;
	private static final int UP = 2;
	private static final int NORTH = 4;
	private static final int SOUTH = 8;
	private static final int WEST = 16;
	private static final int EAST = 32;

	private final SliderSetting height = new SliderSetting("Height",
		"Height of the rendered tunnel boxes.", 0.1, 0.01, 2, 0.01,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting connected = new CheckboxSetting("Connected",
		"If neighbouring holes should be connected.", true);

	private final EnumSetting<ShapeMode> shapeMode = new EnumSetting<>(
		"Shape mode", "How the tunnels are rendered.", ShapeMode.values(),
		ShapeMode.BOTH);

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside tunnel boxes.", 0.2, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> shapeMode.getSelected().sides);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the tunnel box outlines.", 1, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> shapeMode.getSelected().lines);

	private final ColorSetting sideColor = new ColorSetting("Fill color",
		"The color that fills tunnel boxes.", new Color(255, 175, 25))
			.visibleWhen(() -> shapeMode.getSelected().sides);

	private final ColorSetting lineColor = new ColorSetting("Line color",
		"The color of the tunnel box outlines.", new Color(255, 175, 25))
			.visibleWhen(() -> shapeMode.getSelected().lines);

	private final ConcurrentHashMap<Long, TChunk> chunks =
		new ConcurrentHashMap<>();

	private ExecutorService executor;
	private Level lastLevel;

	public TunnelESPHack()
	{
		super("TunnelESP");
		setCategory(Category.RENDER);
		addSetting(height);
		addSetting(connected);
		addSetting(shapeMode);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(sideColor);
		addSetting(lineColor);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		chunks.clear();
		lastLevel = MC.level;
		executor = Executors.newSingleThreadExecutor(r -> {
			Thread thread = new Thread(r, "Wurst-TunnelESP-Scanner");
			thread.setDaemon(true);
			return thread;
		});
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);

		if(executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
		chunks.clear();
		lastLevel = null;
	}

	@Override
	public void onUpdate()
	{
		Level level = MC.level;
		if(level == null || MC.player == null)
			return;

		if(level != lastLevel)
		{
			chunks.clear();
			lastLevel = level;
		}

		if(executor == null || executor.isShutdown())
			return;

		for(TChunk chunk : chunks.values())
			chunk.marked = false;

		int added = 0;
		List<LevelChunk> loadedChunks = ChunkUtils.getLoadedChunks().toList();
		for(LevelChunk chunk : loadedChunks)
		{
			long key = ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z);
			TChunk existing = chunks.get(key);

			if(existing != null)
			{
				existing.marked = true;
				continue;
			}

			if(added >= 8)
				continue;

			TChunk tChunk = new TChunk(chunk.getPos().x, chunk.getPos().z);
			chunks.put(key, tChunk);
			executor.execute(() -> searchChunk(level, chunk, tChunk));
			added++;
		}

		chunks.values().removeIf(tChunk -> !tChunk.marked);
	}

	private static int pack(int x, int y, int z)
	{
		return (x & 0xFF) << 24 | (y & 0xFFFF) << 8 | z & 0xFF;
	}

	private static int getPackedX(int packed)
	{
		return packed >> 24 & 0xFF;
	}

	private static int getPackedY(int packed)
	{
		return (short)(packed >> 8 & 0xFFFF);
	}

	private static int getPackedZ(int packed)
	{
		return packed & 0xFF;
	}

	private void searchChunk(Level level, LevelChunk chunk, TChunk tChunk)
	{
		try
		{
			Context ctx = new Context(level);
			IntSet found = new IntOpenHashSet();

			int startX = chunk.getPos().getMinBlockX();
			int startZ = chunk.getPos().getMinBlockZ();
			int endX = chunk.getPos().getMaxBlockX();
			int endZ = chunk.getPos().getMaxBlockZ();
			Heightmap heightmap = chunk
				.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);
			int minY = level.getMinY();

			for(int x = startX; x <= endX; x++)
				for(int z = startZ; z <= endZ; z++)
				{
					int surface = heightmap.getFirstAvailable(x - startX,
						z - startZ);

					for(int y = minY; y < surface; y++)
						if(isTunnel(ctx, x, y, z))
							found.add(pack(x - startX, y, z - startZ));
				}

			IntSet positions = new IntOpenHashSet();
			for(IntIterator it = found.iterator(); it.hasNext();)
			{
				int packed = it.nextInt();
				int x = getPackedX(packed);
				int y = getPackedY(packed);
				int z = getPackedZ(packed);

				if(x == 0 || x == 15 || z == 0 || z == 15)
				{
					positions.add(packed);
					continue;
				}

				for(Direction dir : DIRECTIONS)
					if(found.contains(pack(x + dir.getStepX(), y,
						z + dir.getStepZ())))
					{
						positions.add(packed);
						break;
					}
			}

			tChunk.positions = positions;
		}catch(RuntimeException e)
		{
			e.printStackTrace();
		}
	}

	private boolean isTunnel(Context ctx, int x, int y, int z)
	{
		if(!canWalkIn(ctx, x, y, z))
			return false;

		TunnelSide east = getTunnelSide(ctx, x + 1, y, z);
		if(east == TunnelSide.PARTIALLY_BLOCKED)
			return false;

		TunnelSide west = getTunnelSide(ctx, x - 1, y, z);
		if(west == TunnelSide.PARTIALLY_BLOCKED)
			return false;

		TunnelSide south = getTunnelSide(ctx, x, y, z + 1);
		if(south == TunnelSide.PARTIALLY_BLOCKED)
			return false;

		TunnelSide north = getTunnelSide(ctx, x, y, z - 1);
		if(north == TunnelSide.PARTIALLY_BLOCKED)
			return false;

		return east == TunnelSide.WALKABLE && west == TunnelSide.WALKABLE
			&& south == TunnelSide.FULLY_BLOCKED
			&& north == TunnelSide.FULLY_BLOCKED
			|| east == TunnelSide.FULLY_BLOCKED
				&& west == TunnelSide.FULLY_BLOCKED
				&& south == TunnelSide.WALKABLE
				&& north == TunnelSide.WALKABLE;
	}

	private TunnelSide getTunnelSide(Context ctx, int x, int y, int z)
	{
		if(canWalkIn(ctx, x, y, z))
			return TunnelSide.WALKABLE;
		if(!canWalkThrough(ctx, x, y, z)
			&& !canWalkThrough(ctx, x, y + 1, z))
			return TunnelSide.FULLY_BLOCKED;
		return TunnelSide.PARTIALLY_BLOCKED;
	}

	private boolean canWalkOn(Context ctx, int x, int y, int z)
	{
		BlockState state = ctx.get(x, y, z);

		if(state.isAir() || !state.getFluidState().isEmpty())
			return false;

		return !state.getCollisionShape(ctx.world, ctx.pos.set(x, y, z))
			.isEmpty();
	}

	private boolean canWalkThrough(Context ctx, int x, int y, int z)
	{
		BlockState state = ctx.get(x, y, z);

		if(state.isAir())
			return true;
		if(!state.getFluidState().isEmpty())
			return false;

		return state.getCollisionShape(ctx.world, ctx.pos.set(x, y, z))
			.isEmpty();
	}

	private boolean canWalkIn(Context ctx, int x, int y, int z)
	{
		if(!canWalkOn(ctx, x, y - 1, z))
			return false;
		if(!canWalkThrough(ctx, x, y, z))
			return false;
		if(canWalkThrough(ctx, x, y + 2, z))
			return false;
		return canWalkThrough(ctx, x, y + 1, z);
	}

	private boolean chunkContains(TChunk chunk, int x, int y, int z)
	{
		int key;

		if(x == -1)
		{
			chunk = chunks.get(ChunkPos.asLong(chunk.x - 1, chunk.z));
			key = pack(15, y, z);
		}else if(x == 16)
		{
			chunk = chunks.get(ChunkPos.asLong(chunk.x + 1, chunk.z));
			key = pack(0, y, z);
		}else if(z == -1)
		{
			chunk = chunks.get(ChunkPos.asLong(chunk.x, chunk.z - 1));
			key = pack(x, y, 15);
		}else if(z == 16)
		{
			chunk = chunks.get(ChunkPos.asLong(chunk.x, chunk.z + 1));
			key = pack(x, y, 0);
		}else
			key = pack(x, y, z);

		IntSet positions = chunk == null ? null : chunk.positions;
		return positions != null && positions.contains(key);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(chunks.isEmpty())
			return;

		Vec3 camOffset = RenderUtils.getCameraPos().reverse();
		for(TChunk chunk : chunks.values())
			renderChunk(matrixStack, chunk, camOffset);
	}

	private void renderChunk(PoseStack matrixStack, TChunk chunk,
		Vec3 camOffset)
	{
		IntSet positions = chunk.positions;
		if(positions == null || positions.isEmpty())
			return;

		boolean sides = shapeMode.getSelected().sides;
		boolean lines = shapeMode.getSelected().lines;
		int fillArgb = sideColor.getColorI(fillOpacity.getValueF());
		int lineArgb = lineColor.getColorI(lineOpacity.getValueF());

		if((fillArgb >>> 24) == 0)
			sides = false;
		if((lineArgb >>> 24) == 0)
			lines = false;
		if(!sides && !lines)
			return;

		PoseStack.Pose entry = matrixStack.last();
		MultiBufferSource.BufferSource vcp = RenderUtils.getVCP();
		RenderType fillLayer = WurstRenderLayers.getQuads(false);
		RenderType lineLayer = WurstRenderLayers.getLines(false);
		VertexConsumer fillBuffer = sides ? vcp.getBuffer(fillLayer) : null;
		VertexConsumer lineBuffer = lines ? vcp.getBuffer(lineLayer) : null;
		double boxHeight = height.getValue();

		for(int packed : positions)
		{
			int x = getPackedX(packed);
			int y = getPackedY(packed);
			int z = getPackedZ(packed);
			int mask = getExcludeMask(chunk, x, y, z);

			double x1 = x + chunk.x * 16 + camOffset.x;
			double y1 = y + camOffset.y;
			double z1 = z + chunk.z * 16 + camOffset.z;
			double x2 = x1 + 1;
			double y2 = y1 + boxHeight;
			double z2 = z1 + 1;

			if(fillBuffer != null)
				addFillBox(fillBuffer, entry, x1, y1, z1, x2, y2, z2,
					fillArgb, mask);
			if(lineBuffer != null)
				addLineBox(lineBuffer, entry, x1, y1, z1, x2, y2, z2,
					lineArgb, mask);
		}

		if(fillBuffer != null)
			vcp.endBatch(fillLayer);
		if(lineBuffer != null)
			vcp.endBatch(lineLayer);
	}

	private int getExcludeMask(TChunk chunk, int x, int y, int z)
	{
		if(!connected.isChecked())
			return 0;

		int mask = 0;
		if(chunkContains(chunk, x + 1, y, z))
			mask |= EAST;
		if(chunkContains(chunk, x - 1, y, z))
			mask |= WEST;
		if(chunkContains(chunk, x, y, z + 1))
			mask |= SOUTH;
		if(chunkContains(chunk, x, y, z - 1))
			mask |= NORTH;
		return mask;
	}

	private static void addFillBox(VertexConsumer buffer, PoseStack.Pose entry,
		double x1, double y1, double z1, double x2, double y2, double z2,
		int color, int mask)
	{
		if((mask & DOWN) == 0)
		{
			quad(buffer, entry, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1,
				z2, color);
		}
		if((mask & UP) == 0)
		{
			quad(buffer, entry, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2,
				z1, color);
		}
		if((mask & NORTH) == 0)
		{
			quad(buffer, entry, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1,
				z1, color);
		}
		if((mask & SOUTH) == 0)
		{
			quad(buffer, entry, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2,
				z2, color);
		}
		if((mask & WEST) == 0)
		{
			quad(buffer, entry, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2,
				z1, color);
		}
		if((mask & EAST) == 0)
		{
			quad(buffer, entry, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1,
				z2, color);
		}
	}

	private static void addLineBox(VertexConsumer buffer, PoseStack.Pose entry,
		double x1, double y1, double z1, double x2, double y2, double z2,
		int color, int mask)
	{
		if((mask & (DOWN | NORTH)) != (DOWN | NORTH))
			line(buffer, entry, x1, y1, z1, x2, y1, z1, 1, 0, 0, color);
		if((mask & (UP | NORTH)) != (UP | NORTH))
			line(buffer, entry, x1, y2, z1, x2, y2, z1, 1, 0, 0, color);
		if((mask & (DOWN | SOUTH)) != (DOWN | SOUTH))
			line(buffer, entry, x1, y1, z2, x2, y1, z2, 1, 0, 0, color);
		if((mask & (UP | SOUTH)) != (UP | SOUTH))
			line(buffer, entry, x1, y2, z2, x2, y2, z2, 1, 0, 0, color);

		if((mask & (WEST | NORTH)) != (WEST | NORTH))
			line(buffer, entry, x1, y1, z1, x1, y2, z1, 0, 1, 0, color);
		if((mask & (EAST | NORTH)) != (EAST | NORTH))
			line(buffer, entry, x2, y1, z1, x2, y2, z1, 0, 1, 0, color);
		if((mask & (WEST | SOUTH)) != (WEST | SOUTH))
			line(buffer, entry, x1, y1, z2, x1, y2, z2, 0, 1, 0, color);
		if((mask & (EAST | SOUTH)) != (EAST | SOUTH))
			line(buffer, entry, x2, y1, z2, x2, y2, z2, 0, 1, 0, color);

		if((mask & (DOWN | WEST)) != (DOWN | WEST))
			line(buffer, entry, x1, y1, z1, x1, y1, z2, 0, 0, 1, color);
		if((mask & (UP | WEST)) != (UP | WEST))
			line(buffer, entry, x1, y2, z1, x1, y2, z2, 0, 0, 1, color);
		if((mask & (DOWN | EAST)) != (DOWN | EAST))
			line(buffer, entry, x2, y1, z1, x2, y1, z2, 0, 0, 1, color);
		if((mask & (UP | EAST)) != (UP | EAST))
			line(buffer, entry, x2, y2, z1, x2, y2, z2, 0, 0, 1, color);
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose entry,
		double x1, double y1, double z1, double x2, double y2, double z2,
		double x3, double y3, double z3, double x4, double y4, double z4,
		int color)
	{
		buffer.addVertex(entry.pose(), (float)x1, (float)y1, (float)z1)
			.setColor(color);
		buffer.addVertex(entry.pose(), (float)x2, (float)y2, (float)z2)
			.setColor(color);
		buffer.addVertex(entry.pose(), (float)x3, (float)y3, (float)z3)
			.setColor(color);
		buffer.addVertex(entry.pose(), (float)x4, (float)y4, (float)z4)
			.setColor(color);
	}

	private static void line(VertexConsumer buffer, PoseStack.Pose entry,
		double x1, double y1, double z1, double x2, double y2, double z2,
		float nx, float ny, float nz, int color)
	{
		buffer.addVertex(entry.pose(), (float)x1, (float)y1, (float)z1)
			.setColor(color).setNormal(entry, nx, ny, nz);
		buffer.addVertex(entry.pose(), (float)x2, (float)y2, (float)z2)
			.setColor(color).setNormal(entry, nx, ny, nz);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		chunks.clear();
		lastLevel = world;
	}

	private final class TChunk
	{
		private final int x;
		private final int z;
		private volatile IntSet positions;
		private volatile boolean marked = true;

		private TChunk(int x, int z)
		{
			this.x = x;
			this.z = z;
		}
	}

	private static final class Context
	{
		private final Level world;
		private final BlockPos.MutableBlockPos pos =
			new BlockPos.MutableBlockPos();
		private ChunkAccess lastChunk;

		private Context(Level world)
		{
			this.world = world;
		}

		private BlockState get(int x, int y, int z)
		{
			if(world == null || world.isOutsideBuildHeight(y))
				return Blocks.VOID_AIR.defaultBlockState();

			int chunkX = x >> 4;
			int chunkZ = z >> 4;

			ChunkAccess chunk = lastChunk;
			if(chunk == null || chunk.getPos().x != chunkX
				|| chunk.getPos().z != chunkZ)
			{
				chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL,
					false);
				lastChunk = chunk;
			}

			if(chunk == null)
				return Blocks.VOID_AIR.defaultBlockState();

			LevelChunkSection[] sections = chunk.getSections();
			int index = chunk.getSectionIndex(y);
			if(index < 0 || index >= sections.length)
				return Blocks.VOID_AIR.defaultBlockState();

			LevelChunkSection section = sections[index];
			if(section == null)
				return Blocks.VOID_AIR.defaultBlockState();

			return section.getBlockState(x & 15, y & 15, z & 15);
		}
	}

	private enum TunnelSide
	{
		WALKABLE,
		PARTIALLY_BLOCKED,
		FULLY_BLOCKED
	}

	private enum ShapeMode
	{
		BOTH("Both", true, true),
		LINES("Lines only", false, true),
		SIDES("Sides only", true, false);

		private final String name;
		private final boolean sides;
		private final boolean lines;

		ShapeMode(String name, boolean sides, boolean lines)
		{
			this.name = name;
			this.sides = sides;
			this.lines = lines;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

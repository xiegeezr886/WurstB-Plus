/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hacks.SeedOreEspHack;
import net.wurstclient.hacks.SeedStructureEspHack;
import net.wurstclient.seed.SeedEntry;
import net.wurstclient.seed.SeedStore;
import net.wurstclient.seed.crack.CrackInput;
import net.wurstclient.seed.crack.LatticeCracker;
import net.wurstclient.seed.search.Observation;
import net.wurstclient.seed.search.SeedSearch;
import net.wurstclient.seed.search.SeedSearchJob;
import net.wurstclient.seed.scan.StructureScanner;
import net.wurstclient.seed.search.StructureSpread;
import net.wurstclient.seed.structure.StructureFinder;
import net.wurstclient.seed.structure.StructureHit;
import net.wurstclient.util.ChatUtils;

/**
 * Manages the seed that the seed ore ESP predicts from: read the current seed,
 * set or delete the seed of this world identity, list every stored identity,
 * predict structure positions or toggle the seed hacks themselves.
 */
public final class SeedCmd extends Command implements UpdateListener
{
	private static final int DEFAULT_STRUCTURE_RADIUS = 16;
	private static final int MAX_STRUCTURE_RADIUS = 64;
	private static final int MAX_STRUCTURE_LINES = 20;
	private static final int MAX_SEED_LINES = 10;
	private static final int DEFAULT_SCAN_RADIUS = 3;
	private static final int MAX_SCAN_RADIUS = 8;
	private static final int MAX_SEARCH_RESULTS = 200;
	
	private final StructureFinder structureFinder = new StructureFinder();
	private static final long DEFAULT_CRACK_TIMEOUT = 30_000L;
	private static final long MAX_CRACK_TIMEOUT = 300_000L;
	
	private final ArrayList<Observation> observations = new ArrayList<>();
	private int searchTicks;
	private boolean searchReported;
	private CrackTask crackTask;
	private int crackTicks;
	
	public SeedCmd()
	{
		super("seed", "Manages the seed used by the seed features.",
			".seed get | set <seed> | clear | list | structures [radius] | mine | structesp",
			".seed observe <structure set> <x> <z> | observe list | observe clear | observe scan [radius]",
			".seed search <from> <to> | search status | search cancel | crack [timeout seconds]",
			"Example: .seed set -4172144997902289642",
			"Example: .seed observe village_plains 320 -480",
			"Example: .seed search -5000000 5000000");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		SeedStore store = SeedStore.get();
		
		if(args.length == 0)
		{
			printSeed(store);
			return;
		}
		
		String subcommand = args[0].toLowerCase();
		
		if(subcommand.equals("get"))
			printSeed(store);
		else if(subcommand.equals("set"))
			set(store, args);
		else if(subcommand.equals("clear"))
			clear(store);
		else if(subcommand.equals("list"))
			list(store);
		else if(subcommand.equals("structures") || subcommand.equals("struct"))
			structures(args);
		else if(subcommand.equals("structesp") || subcommand.equals("sesp"))
			structEsp();
		else if(subcommand.equals("observe") || subcommand.equals("obs"))
			observe(args);
		else if(subcommand.equals("search"))
			search(args);
		else if(subcommand.equals("crack"))
			crack(args);
		else if(subcommand.equals("mine"))
			mine();
		else if(subcommand.equals("help"))
			printHelp();
		else
			throw new CmdSyntaxError("Unknown subcommand: " + args[0]);
	}
	
	private void printSeed(SeedStore store)
	{
		SeedEntry current = store.current();
		
		if(current == null)
			ChatUtils.message("种子: 未设置");
		else
			ChatUtils.message("种子: " + SeedStore.describe(current.seed));
		
		ChatUtils.message("来源: " + describeSource());
		ChatUtils.message("身份 id: " + store.identity());
		ChatUtils.message("配置文件: " + store.path());
	}
	
	private String describeSource()
	{
		if(MC.hasSingleplayerServer())
			return "单人集成服务器";
		
		return "本身份存储";
	}
	
	private void set(SeedStore store, String[] args) throws CmdException
	{
		if(args.length != 2)
			throw new CmdSyntaxError("Usage: .seed set <seed>");
		
		try
		{
			store.setFromText(args[1], "1.20.1");
		}catch(Exception e)
		{
			throw new CmdError(
				"无法解析种子 \"" + args[1] + "\": " + e.getMessage());
		}
		
		if(!store.save())
		{
			ChatUtils.error("种子已设置，但无法保存到: " + store.path());
			return;
		}
		
		SeedEntry stored = store.stored();
		long seed =
			stored != null ? stored.seed : SeedStore.parseSeed(args[1]);
		
		ChatUtils.message("种子已设置: " + SeedStore.describe(seed));
	}
	
	private void clear(SeedStore store) throws CmdException
	{
		if(!store.remove())
			throw new CmdError("本身份没有已保存的种子。");
		
		if(!store.save())
		{
			ChatUtils.error("种子已删除，但无法保存到: " + store.path());
			return;
		}
		
		ChatUtils.message("已删除本身份的种子 (" + store.identity() + ")。");
	}
	
	private void list(SeedStore store)
	{
		Map<String, SeedEntry> all = store.all();
		
		if(all == null || all.isEmpty())
		{
			ChatUtils.message("没有已保存的种子。");
			return;
		}
		
		ChatUtils.message("已保存的种子 (" + all.size() + "):");
		
		for(Map.Entry<String, SeedEntry> entry : all.entrySet())
		{
			SeedEntry value = entry.getValue();
			String seed =
				value == null ? "?" : SeedStore.describe(value.seed);
			
			ChatUtils.message("  " + entry.getKey() + " -> " + seed);
		}
	}
	
	private void mine() throws CmdException
	{
		SeedOreEspHack hack = WURST.getHax().seedOreEspHack;
		
		if(hack == null)
			throw new CmdError("种子矿透 hack 不可用。");
		
		hack.setEnabled(!hack.isEnabled());
		
		ChatUtils.message(
			"种子矿透: " + (hack.isEnabled() ? "开启" : "关闭"));
	}
	
	/**
	 * Toggles the structure ESP, which draws the predicted structure positions.
	 */
	private void structEsp() throws CmdException
	{
		SeedStructureEspHack hack = WURST.getHax().seedStructureEspHack;
		
		if(hack == null)
			throw new CmdError("结构 ESP hack 不可用。");
		
		hack.setEnabled(!hack.isEnabled());
		
		ChatUtils.message(
			"结构透视: " + (hack.isEnabled() ? "开启" : "关闭"));
	}
	
	/**
	 * Predicts the structure positions of the current seed around the player.
	 * The placement math is vanilla's own, so the result is exact for every
	 * structure set that uses random spreading; candidates in chunks that the
	 * client has not loaded cannot have their biome checked and are therefore
	 * only dropped once the chunk is loaded.
	 */
	private void structures(String[] args) throws CmdException
	{
		int radius = DEFAULT_STRUCTURE_RADIUS;
		
		if(args.length > 1)
			try
			{
				radius = Integer.parseInt(args[1]);
			}catch(NumberFormatException e)
			{
				throw new CmdSyntaxError(
					"Usage: .seed structures [radius in chunks]");
			}
		
		if(radius < 1 || radius > MAX_STRUCTURE_RADIUS)
			throw new CmdError("半径必须在 1 到 " + MAX_STRUCTURE_RADIUS
				+ " 区块之间。");
		
		if(MC.player == null || MC.level == null)
			throw new CmdError("需要先进入世界。");
		
		if(SeedStore.get().current() == null)
			throw new CmdError("还没有种子，先用 .seed set <seed> 设置。");
		
		if(!structureFinder.refresh())
			throw new CmdError("无法读取当前世界的结构集。");
		
		ChunkPos center = new ChunkPos(MC.player.blockPosition());
		List<StructureHit> hits = structureFinder.find(center, radius, true);
		
		ChatUtils.message("结构预测（半径 " + radius + " 区块）: "
			+ structureFinder.describeSources() + "，候选 " + hits.size()
			+ " 个");
		
		if(hits.isEmpty())
		{
			ChatUtils.message("附近没有候选结构。");
			return;
		}
		
		BlockPos reference = MC.player.blockPosition();
		int shown = Math.min(hits.size(), MAX_STRUCTURE_LINES);
		
		for(int i = 0; i < shown; i++)
		{
			StructureHit hit = hits.get(i);
			int distance = (int)Math.sqrt(hit.distanceSq(reference));
			
			ChatUtils.message("  " + hit.describe() + " - " + distance
				+ " 格");
		}
		
		if(hits.size() > shown)
			ChatUtils.message("  ...还有 " + (hits.size() - shown)
				+ " 个（可减小半径）");
	}
	
	/**
	 * Records a structure the player actually saw, which is the input of the
	 * seed search.
	 */
	private void observe(String[] args) throws CmdException
	{
		if(args.length < 2)
			throw new CmdSyntaxError(
				"Usage: .seed observe <structure set> <x> <z> | observe list | observe clear");
		
		if(args[1].equals("list"))
		{
			if(observations.isEmpty())
			{
				ChatUtils.message("还没有观测。");
				return;
			}
			
			ChatUtils.message("观测 (" + observations.size() + "):");
			
			for(Observation observation : observations)
				ChatUtils.message("  " + observation.describe());
			
			return;
		}
		
		if(args[1].equals("clear"))
		{
			observations.clear();
			ChatUtils.message("已清空观测。");
			return;
		}
		
		if(args[1].equals("scan"))
		{
			scan(args);
			return;
		}
		
		if(args.length != 4)
			throw new CmdSyntaxError(
				"Usage: .seed observe <structure set> <x> <z>");
		
		int x;
		int z;
		
		try
		{
			x = Integer.parseInt(args[2]);
			z = Integer.parseInt(args[3]);
		}catch(NumberFormatException e)
		{
			throw new CmdError("坐标必须是整数。");
		}
		
		String setId =
			args[1].contains(":") ? args[1] : "minecraft:" + args[1];
		observations.add(Observation.ofBlock(setId, x, z));
		
		ChatUtils.message("已记录观测: "
			+ observations.get(observations.size() - 1).describe());
		ChatUtils.message("提示: 结构集名称见 .seed structures 或 .seed search 的候选列表。");
	}
	
	/**
	 * Searches a range of level seeds for the ones that reproduce every
	 * observation. Runs on a background thread and reports through
	 * {@link #onUpdate()}.
	 */
	private void search(String[] args) throws CmdException
	{
		if(args.length > 1 && (args[1].equals("cancel")
			|| args[1].equals("stop")))
		{
			if(!SeedSearchJob.isRunning())
				throw new CmdError("没有正在运行的搜索。");
			
			SeedSearchJob.cancelCurrent();
			ChatUtils.message("已请求取消搜索。");
			return;
		}
		
		if(args.length > 1 && args[1].equals("status"))
		{
			SeedSearchJob job = SeedSearchJob.current();
			
			if(job == null)
				throw new CmdError("还没有启动过搜索。");
			
			ChatUtils.message(job.describe());
			
			if(job.isFinished())
				printCandidates(job.results());
			
			return;
		}
		
		if(args.length != 3)
			throw new CmdSyntaxError("Usage: .seed search <from> <to>");
		
		long from;
		long to;
		
		try
		{
			from = SeedStore.parseSeed(args[1]);
			to = SeedStore.parseSeed(args[2]);
		}catch(Exception e)
		{
			throw new CmdError("无法解析范围: " + e.getMessage());
		}
		
		if(to < from)
			throw new CmdError("范围必须满足 <from> <= <to>。");
		
		if(MC.player == null || MC.level == null)
			throw new CmdError("需要先进入世界。");
		
		if(observations.isEmpty())
			throw new CmdError(
				"还没有观测，先用 .seed observe <结构集> <x> <z> 记录一个结构。");
		
		if(SeedSearchJob.isRunning())
			throw new CmdError("已有搜索在运行，先执行 .seed search cancel。");
		
		List<StructureSpread> spreads = structureFinder.spreads();
		
		if(spreads.isEmpty())
			throw new CmdError("读取不到结构集（需要已进入世界）。");
		
		List<SeedSearch.Target> targets =
			SeedSearch.targets(observations, spreads);
		
		if(targets.isEmpty())
			throw new CmdError(
				"这些观测无法用于反解（结构集未知，或间距太小、每个区块都可能生成）。");
		
		ChatUtils.message(
			"结构集自检: " + structureFinder.describeSpreadCheck());
		
		if(structureFinder.describeSpreadCheck().startsWith("MISMATCH"))
			throw new CmdError("自检失败，已拒绝搜索（详见日志）。");
		
		int threads = Math.max(1,
			Math.min(8, Runtime.getRuntime().availableProcessors()));
		
		ChatUtils.message("开始搜索 " + SeedStore.describe(from) + " .. "
			+ SeedStore.describe(to) + "，" + threads + " 线程，" + targets.size()
			+ " 条观测（" + SeedSearch.describeTargets(targets) + "）");
		
		searchTicks = 0;
		searchReported = false;
		EVENTS.add(UpdateListener.class, this);
		SeedSearchJob.start(from, to, targets, threads, MAX_SEARCH_RESULTS);
	}
	
	/**
	 * Polls whichever background task is running and unsubscribes once nothing
	 * is left to report.
	 */
	@Override
	public void onUpdate()
	{
		boolean busy = false;
		
		if(crackTask != null)
		{
			if(crackTask.isFinished())
			{
				CrackTask done = crackTask;
				crackTask = null;
				reportCrack(done.result());
			}else
			{
				busy = true;
				
				if(++crackTicks % 20 == 0)
					ChatUtils.message(
						"反解中...（已等待 " + crackTicks / 20 + " 秒）");
			}
		}
		
		if(!searchReported)
		{
			SeedSearchJob job = SeedSearchJob.current();
			
			if(job == null)
				searchReported = true;
			else if(!job.isFinished())
			{
				busy = true;
				
				if(++searchTicks % 20 == 0)
					ChatUtils.message("搜索中: " + job.describeProgress());
			}else
			{
				searchReported = true;
				ChatUtils.message("搜索完成: " + job.describe());
				printCandidates(job.results());
			}
		}
		
		if(!busy && crackTask == null)
			EVENTS.remove(UpdateListener.class, this);
	}
	
	private void printCandidates(List<Long> seeds)
	{
		if(seeds.isEmpty())
		{
			ChatUtils.message("该范围内没有匹配的种子（可扩大范围或补充观测）。");
			return;
		}
		
		int shown = Math.min(seeds.size(), MAX_SEED_LINES);
		
		for(int i = 0; i < shown; i++)
			ChatUtils.message("  候选 " + (i + 1) + ": "
				+ SeedStore.describe(seeds.get(i)) + "  (.seed set "
				+ seeds.get(i) + ")");
		
		if(seeds.size() > shown)
			ChatUtils.message("  ...还有 " + (seeds.size() - shown) + " 个候选");
		
		ChatUtils.message(
			"确认后可用 .seed set <seed> 写入；补充更多结构观测可进一步缩小候选。");
	}
	
	/**
	 * Looks for recognizable structures in the loaded chunks around the player
	 * and records what it finds as observations.
	 *
	 * <p>
	 * The detectors are heuristics - player builds can look like a structure and
	 * a partly loaded structure can be missed - so everything they report is
	 * printed with a reason and should be checked against the candidate list of
	 * {@code .seed structures} before it is used for a search.
	 */
	private void scan(String[] args) throws CmdException
	{
		int radius = DEFAULT_SCAN_RADIUS;
		
		if(args.length > 2)
			try
			{
				radius = Integer.parseInt(args[2]);
			}catch(NumberFormatException e)
			{
				throw new CmdSyntaxError("Usage: .seed observe scan [radius]");
			}
		
		if(radius < 1 || radius > MAX_SCAN_RADIUS)
			throw new CmdError("扫描半径必须在 1 到 " + MAX_SCAN_RADIUS
				+ " 区块之间。");
		
		if(MC.player == null || MC.level == null)
			throw new CmdError("需要先进入世界。");
		
		BlockPos feet = MC.player.blockPosition();
		int centerX = Math.floorDiv(feet.getX(), 16);
		int centerZ = Math.floorDiv(feet.getZ(), 16);
		
		ChatUtils.message(
			"扫描已加载区块（半径 " + radius + " 区块，可能需要几秒）...");
		
		List<StructureScanner.Sighting> sightings =
			StructureScanner.scan(new ScanView(), centerX, centerZ, radius);
		
		if(sightings.isEmpty())
		{
			ChatUtils.message("附近没有认出结构。支持: "
				+ String.join(", ", StructureScanner.supportedSets()));
			return;
		}
		
		int added = 0;
		
		for(StructureScanner.Sighting sighting : sightings)
		{
			Observation observation = new Observation(sighting.setId,
				sighting.chunkX, sighting.chunkZ);
			boolean isNew = addObservation(observation);
			
			if(isNew)
				added++;
			
			ChatUtils.message("  " + observation.describe() + " - "
				+ sighting.reason + " (score " + sighting.score + ")"
				+ (isNew ? "" : " [已存在]"));
		}
		
		ChatUtils.message("新增 " + added + " 条观测，共 " + observations.size()
			+ " 条。可用 .seed structures 复核，再用 .seed search <from> <to>。");
	}
	
	/**
	 * @return whether the observation was not recorded before.
	 */
	private boolean addObservation(Observation observation)
	{
		for(Observation existing : observations)
			if(existing.setId.equals(observation.setId)
				&& existing.chunkX == observation.chunkX
				&& existing.chunkZ == observation.chunkZ)
				return false;
		
		observations.add(observation);
		return true;
	}
	
	/**
	 * Recovers the level seed without a search range, by turning the recorded
	 * observations into constraints on the 48 bit seed and solving them with
	 * lattice reduction.
	 *
	 * <p>
	 * This is the fallback for "I have no idea where the seed is": it needs at
	 * least two observations of a placement whose range is between 2 and
	 * {@code LatticeCracker.MAX_RANGE}, and it reports honestly when it cannot
	 * solve them.
	 */
	private void crack(String[] args) throws CmdException
	{
		long timeout = DEFAULT_CRACK_TIMEOUT;
		
		if(args.length > 1)
			try
			{
				timeout = Long.parseLong(args[1]) * 1000L;
			}catch(NumberFormatException e)
			{
				throw new CmdSyntaxError("Usage: .seed crack [timeout seconds]");
			}
		
		if(timeout < 1000L || timeout > MAX_CRACK_TIMEOUT)
			throw new CmdError("超时必须在 1 到 " + MAX_CRACK_TIMEOUT / 1000
				+ " 秒之间。");
		
		if(MC.level == null)
			throw new CmdError("需要先进入世界。");
		
		if(observations.isEmpty())
			throw new CmdError(
				"还没有观测，先用 .seed observe <结构集> <x> <z> 或 .seed observe scan。");
		
		if(crackTask != null)
			throw new CmdError("已有反解在运行。");
		
		List<StructureSpread> spreads = structureFinder.spreads();
		ArrayList<LatticeCracker.Constraint> constraints = new ArrayList<>();
		int used = 0;
		
		for(Observation observation : observations)
		{
			StructureSpread spread =
				Observation.find(spreads, observation.setId);
			
			if(spread == null)
				continue;
			
			List<LatticeCracker.Constraint> found =
				CrackInput.constraintsFor(spread.spacing, spread.separation,
					spread.salt, observation.chunkX, observation.chunkZ);
			
			if(found.isEmpty())
				continue;
			
			constraints.addAll(found);
			used++;
		}
		
		if(constraints.size() < 2)
			throw new CmdError(
				"可用于反解的观测不足（需要间距 2.." + LatticeCracker.MAX_RANGE
					+ " 的线性放置，三角放置暂不支持）。");
		
		ChatUtils.message("开始反解: " + used + " 条观测 / " + constraints.size()
			+ " 条约束，超时 " + timeout / 1000 + " 秒...");
		
		crackTicks = 0;
		crackTask = new CrackTask(constraints, timeout);
		EVENTS.add(UpdateListener.class, this);
		crackTask.start();
	}
	
	private void reportCrack(LatticeCracker.Result result)
	{
		if(result == null)
		{
			ChatUtils.error("反解失败：没有结果（详见日志）。");
			return;
		}
		
		ChatUtils.message("反解结束: " + result.detail + "（" + result.millis
			+ " ms，" + result.candidates + " 个候选）");
		
		if(!result.success)
		{
			ChatUtils.message("未能解出种子——补充更多结构观测（不同结构集、相距更远）后重试。");
			return;
		}
		
		ChatUtils.message("种子: " + SeedStore.describe(result.seed)
			+ "  (.seed set " + result.seed + ")");
	}
	
	/**
	 * Runs the lattice solver on a worker thread, because it can take seconds.
	 */
	private static final class CrackTask
	{
		private final List<LatticeCracker.Constraint> constraints;
		private final long timeout;
		private final Thread thread;
		private volatile LatticeCracker.Result result;
		private volatile boolean finished;
		
		private CrackTask(List<LatticeCracker.Constraint> constraints,
			long timeout)
		{
			this.constraints = constraints;
			this.timeout = timeout;
			
			thread = new Thread(() -> {
				try
				{
					result = LatticeCracker.crack(constraints, timeout);
				}catch(Exception e)
				{
					e.printStackTrace();
				}finally
				{
					finished = true;
				}
			}, "Wurst lattice cracker");
			thread.setDaemon(true);
		}
		
		private void start()
		{
			thread.start();
		}
		
		private boolean isFinished()
		{
			return finished;
		}
		
		private LatticeCracker.Result result()
		{
			return result;
		}
	}
	
	/**
	 * Reads blocks and biomes out of the loaded level. Block ids are cached per
	 * block, because one scan asks for tens of thousands of them.
	 */
	private static final class ScanView implements StructureScanner.BlockView
	{
		private final BlockPos.MutableBlockPos pos =
			new BlockPos.MutableBlockPos();
		private final IdentityHashMap<Block, String> blockIds =
			new IdentityHashMap<>();
		
		@Override
		public String blockId(int x, int y, int z)
		{
			if(MC.level == null)
				return null;
			
			BlockState state = MC.level.getBlockState(pos.set(x, y, z));
			Block block = state.getBlock();
			String cached = blockIds.get(block);
			
			if(cached != null)
				return cached;
			
			cached = BuiltInRegistries.BLOCK.getKey(block).toString();
			blockIds.put(block, cached);
			return cached;
		}
		
		@Override
		public boolean isLoaded(int chunkX, int chunkZ)
		{
			return MC.level != null && MC.level.hasChunk(chunkX, chunkZ);
		}
		
		@Override
		public String biomeId(int x, int z)
		{
			if(MC.level == null)
				return null;
			
			return MC.level.getBiome(pos.set(x, 64, z)).unwrapKey()
				.map(key -> key.location().toString()).orElse(null);
		}
	}
}

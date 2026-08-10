package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.command.Command;
import net.wurstclient.hack.Hack;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.FuzzySearch;

public final class FeatureMenuSupport
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Map<Feature, String> ONE_LINE_DESCRIPTIONS =
		new IdentityHashMap<>();
	private static List<Feature> allFeatures = List.of();
	private static int cachedFeatureCount = -1;

	private FeatureMenuSupport()
	{
	}

	public static List<Feature> getAllFeatures()
	{
		int featureCount = WURST.getHax().countHax()
			+ WURST.getCmds().countCmds() + WURST.getOtfs().countOtfs();
		if(featureCount == cachedFeatureCount)
			return allFeatures;

		List<Feature> features = new ArrayList<>();
		features.addAll(WURST.getHax().getAllHax());
		features.addAll(WURST.getCmds().getAllCmds());
		features.addAll(WURST.getOtfs().getAllOtfs());
		allFeatures = List.copyOf(features);
		cachedFeatureCount = featureCount;
		ONE_LINE_DESCRIPTIONS.clear();
		return allFeatures;
	}

	static String getOneLineDescription(Feature feature)
	{
		return ONE_LINE_DESCRIPTIONS.computeIfAbsent(feature,
			ignored -> feature.getDescription().replace('\n', ' ')
				.replace('\r', ' '));
	}

	static boolean matchesSearch(Feature feature, String query)
	{
		return searchScore(feature, query) != FuzzySearch.NO_MATCH;
	}

	static int searchScore(Feature feature, String query)
	{
		int best = boostedScore(FuzzySearch.score(feature.getName(), query), 120);
		best = Math.max(best, boostedScore(
			FuzzySearch.score(feature.getDisplayName(), query), 140));
		best = Math.max(best, boostedScore(FuzzySearch.score(
			feature.getSearchTags().replace('\u00a7', ' '), query), 80));
		best = Math.max(best, boostedScore(
			FuzzySearch.score(feature.getDescription(), query), 0));

		StringBuilder settings = new StringBuilder();
		feature.getSettings().values().forEach(setting -> settings.append(' ')
			.append(setting.getName()));
		best = Math.max(best, boostedScore(
			FuzzySearch.score(settings.toString(), query), 40));
		String combined = feature.getName() + " " + feature.getDisplayName()
			+ " " + feature.getSearchTags().replace('\u00a7', ' ') + settings;
		return Math.max(best, boostedScore(
			FuzzySearch.score(combined, query), 60));
	}

	public static List<Feature> searchFeatures(List<Feature> features, String query)
	{
		return features.stream()
			.map(feature -> new ScoredFeature(feature,
				searchScore(feature, query)))
			.filter(result -> result.score() != FuzzySearch.NO_MATCH)
			.sorted((first, second) -> {
				int scoreOrder = Integer.compare(second.score(), first.score());
				if(scoreOrder != 0)
					return scoreOrder;
				return String.CASE_INSENSITIVE_ORDER.compare(
					first.feature().getDisplayName(),
					second.feature().getDisplayName());
			})
			.map(ScoredFeature::feature).toList();
	}

	private static int boostedScore(int score, int boost)
	{
		return score == FuzzySearch.NO_MATCH ? FuzzySearch.NO_MATCH
			: score + boost;
	}

	private record ScoredFeature(Feature feature, int score)
	{}

	public static boolean runPrimaryAction(Feature feature)
	{
		if(feature.getPrimaryAction().isEmpty())
			return false;

		if(!feature.isEnabled() && WURST.getHax().tooManyHaxHack.isEnabled()
			&& WURST.getHax().tooManyHaxHack.isBlocked(feature))
		{
			ChatUtils.error(feature.getDisplayName()
				+ " 已被‘太多功能’限制。");
			return true;
		}

		feature.doPrimaryAction();
		return true;
	}

	static String getTypeName(Feature feature)
	{
		if(feature instanceof Hack)
			return "功能";
		if(feature instanceof Command)
			return "命令";
		if(feature instanceof OtherFeature)
			return "工具";
		return "项目";
	}
}

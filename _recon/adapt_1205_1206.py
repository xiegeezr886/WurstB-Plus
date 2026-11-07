# -*- coding: utf-8 -*-
"""Adapt the legacy HUD port to 1.20.5/1.20.6."""
import re
from pathlib import Path

SCORE = '''List<PlayerScoreEntry> scores =
			new ArrayList<>(scoreboard.listPlayerScores(objective));
		scores.sort(
			Comparator.comparingInt(PlayerScoreEntry::value).reversed()
				.thenComparing(PlayerScoreEntry::owner,
					String.CASE_INSENSITIVE_ORDER));

		List<ScoreLine> lines = new ArrayList<>();
		for(PlayerScoreEntry score : scores)
		{
			PlayerTeam team = scoreboard.getPlayersTeam(score.owner());
			String name = toLegacy(
				PlayerTeam.formatNameForTeam(team, score.ownerName()));
			lines.add(new ScoreLine(name, score.value()));
			if(lines.size() >= 15)
				break;
		}
		return lines;'''

EFFECTS = '''List<EffectInfo> effects = new ArrayList<>();
		for(MobEffectInstance instance : WurstClient.MC.player
			.getActiveEffects())
		{
			MobEffect type = instance.getEffect().value();
			effects.add(new EffectInfo(type.getDisplayName().getString(),
				instance.getAmplifier(), instance.getDuration(),
				type.getColor(), -1, 0, 0, 1, 1));
		}
		return effects;'''

ICONS = '''package net.wurstclient.hud2.compat;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.hud2.nanovg.NanoVGContext;

public final class HudItemIcons
{
	private HudItemIcons() {}

	public static int get(ItemStack stack)
	{
		return 0;
	}

	public static void capturePending(NanoVGContext nvg, int budget) {}

	public static void clear() {}
}
'''


def patch(base):
    root = Path(base) / 'src/main/java/net/wurstclient'
    for p in root.rglob('*.java'):
        t = p.read_text(encoding='utf-8')
        o = t
        t = t.replace('getSkinTextureLocation().toString()',
                      'getSkin().body().texturePath().toString()')
        if p.name == 'HudCompat.java':
            t = t.replace('import net.minecraft.world.scores.Score;',
                          'import net.minecraft.world.scores.PlayerScoreEntry;')
            t = re.sub(
                r'List<Score> scores = new ArrayList<>\(\s*'
                r'scoreboard\.getPlayerScores\(objective\)\);.*?return lines;',
                SCORE, t, flags=re.S)
            t = re.sub(
                r'List<EffectInfo> effects = new ArrayList<>\(\);.*?'
                r'return effects;', EFFECTS, t, flags=re.S)
        if t != o:
            p.write_text(t, encoding='utf-8', newline='\n')
    icons = root / 'hud2/compat/HudItemIcons.java'
    icons.write_text(ICONS, encoding='utf-8', newline='\n')
    print('adapted', base)


if __name__ == '__main__':
    import sys
    for b in sys.argv[1:]:
        patch(b)

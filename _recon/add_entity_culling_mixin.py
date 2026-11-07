# -*- coding: utf-8 -*-
"""Add the lite EntityCulling mixin where EntityCullingHack is missing."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

HEADER = '''package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityCullingMixin
{
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void soar$shouldRender(Entity entity, Frustum frustum, double x,
		double y, double z, %sCallbackInfoReturnable<Boolean> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.entityCullingHack.shouldCull(entity))
			cir.setReturnValue(false);
	}
}
'''


def version_of(project):
    parts = project.parts
    try:
        return parts[parts.index('versions') + 1]
    except ValueError:
        return ''


def add_config(project):
    resources = project / 'src/main/resources'
    config = None
    for name in ('wurstpenguin.mixins.json', 'wurst.mixins.json'):
        path = resources / name
        if path.exists():
            config = path
            break
    if config is None:
        return False
    text = config.read_text(encoding='utf-8')
    if '"EntityCullingMixin"' in text:
        return False
    text = re.sub(r'("client"\s*:\s*\[)',
                  r'\1\n    "EntityCullingMixin",', text, count=1)
    config.write_text(text, encoding='utf-8', newline='\n')
    return True


TARGETS = {'1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10',
           '1.21.11', '26.2', '26.3'}


def main():
    root = REPO.resolve()
    stats = {'mixin': 0, 'config': 0, 'skipped': 0}
    for project in project_dirs():
        if project.resolve() == root:
            continue
        version = version_of(project)
        if version not in TARGETS:
            stats['skipped'] += 1
            continue

        extra = 'float partialTicks, ' if version == '26.3' else ''
        source = HEADER % extra
        out = project / 'src/main/java/net/wurstclient/mixin/EntityCullingMixin.java'
        out.write_text(source, encoding='utf-8', newline='\n')
        stats['mixin'] += 1
        if add_config(project):
            stats['config'] += 1
    print(stats)


if __name__ == '__main__':
    main()

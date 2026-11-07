# -*- coding: utf-8 -*-
"""Add the Soar mixin hooks to the three 1.20.1 root projects."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO
from patch_soar_mixins import (patch_minecraft, patch_living_entity,
                              patch_entity, patch_held_item, patch_camera,
                              patch_overlay, patch_pumpkin, insert_import,
                              insert_before_final_brace, read, write)


def patch_skin_1201(path):
    text = read(path)
    if 'soar$protectSkin' in text:
        return False

    text = insert_import(text, 'org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable')
    text = insert_import(text, 'net.minecraft.client.resources.DefaultPlayerSkin')
    text = insert_import(text, 'net.wurstclient.WurstClient')
    if 'net.minecraft.client.Minecraft' not in text:
        text = insert_import(text, 'net.minecraft.client.Minecraft')

    method = r'''
	@Inject(
		method = "getInsecureSkinLocation(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/resources/ResourceLocation;",
		at = @At("HEAD"),
		cancellable = true)
	private void soar$protectSkin(GameProfile profile,
		CallbackInfoReturnable<ResourceLocation> cir)
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || WurstClient.INSTANCE.getHax() == null
			|| !WurstClient.INSTANCE.getHax().skinProtectHack.isEnabled())
			return;

		if(profile.getId().equals(mc.player.getUUID()))
			cir.setReturnValue(
				DefaultPlayerSkin.getDefaultSkin(profile.getId()));
	}
'''
    text = insert_before_final_brace(text, method)
    write(path, text)
    return True


def patch_root(project):
    root = project / 'src/main/java/net/wurstclient/mixin'
    stats = {}

    def do(name, fn, *args):
        p = root / (name + '.java')
        if not p.exists():
            stats['missing_' + name] = 1
            return
        if fn(p, *args):
            stats[name] = 1

    do('MinecraftClientMixin', patch_minecraft)
    do('LivingEntityMixin', patch_living_entity, '1.20.1')
    do('EntityMixin', patch_entity)
    do('HeldItemRendererMixin', patch_held_item, '1.20.1')
    do('PlayerSkinProviderMixin', patch_skin_1201)
    do('CameraMixin', patch_camera)
    do('InGameOverlayRendererMixin', patch_overlay)
    do('IngameHudMixin', patch_pumpkin)
    return stats


def main():
    for rel in ('.', 'fabric', 'neoforge'):
        project = (REPO / rel).resolve()
        print(rel, patch_root(project))


if __name__ == '__main__':
    main()

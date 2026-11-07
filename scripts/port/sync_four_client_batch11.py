"""Merge feeding controls and add collision controls with verified API adapters."""
import argparse
import hashlib
import json
import re
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch11/src/net/wurstclient'
HOOKS = ['CollisionsShapeMixin', 'CollisionsMovementMixin', 'CollisionsBorderMixin']


def shape_hook(api):
    legacy = 'net/minecraft/world/level/block/state/BlockState.getCollisionShape:' in api.split('Compiled from "BlockCollisions.java"')[1].split('Compiled from')[0]
    if legacy:
        target = 'Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;'
        signature = 'BlockState state, BlockGetter world, BlockPos pos, CollisionContext context'
        call = 'state, world, pos, context'
    else:
        assert 'net/minecraft/world/phys/shapes/CollisionContext.getCollisionShape:' in api
        target = 'Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;'
        signature = 'CollisionContext context, BlockState state, CollisionGetter world, BlockPos pos'
        call = 'context, state, world, pos'
    return '''/* Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public abstract class CollisionsShapeMixin
{
	@WrapOperation(method = "computeNext", at = @At(value = "INVOKE",
		target = "''' + target + '''"))
	private VoxelShape wurstCollisionShape(''' + signature + ''', Operation<VoxelShape> original)
	{
		VoxelShape shape = original.call(''' + call + ''');
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? shape : hax.collisionsHack.collisionShape(state, world, pos, shape);
	}
}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--preview', action='store_true', help='Write adapted results to the task staging area only.')
    args = parser.parse_args()
    manifest = PLAN / 'batch11-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        if not args.preview:
            wait_for_existing_build(tree)
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        api = read(PLAN / 'batch11' / f"api-{tree['mc']}.txt")
        for signature in ['public boolean isInsideCloseToBorder(net.minecraft.world.entity.Entity, net.minecraft.world.phys.AABB);',
                          'public boolean isWithinBounds(net.minecraft.core.BlockPos);',
                          'public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape();',
                          'public void move(net.minecraft.world.entity.MoverType, net.minecraft.world.phys.Vec3);',
                          'getControllingPassenger();', 'public void setPos(double, double, double);']:
            assert signature in api, (tree['mc'], signature)
        planned, observed = {}, {}

        def original(file):
            observed[file] = file.read_bytes()
            backup = PLAN / 'batch11/before' / file.relative_to(ROOT)
            if backup.exists():
                return read(backup)
            if not args.preview:
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(file)

        name = 'hacks/AutoBreedHack.java'
        file = java / name
        before = original(file)

        def adapt_breed(text):
            if 'EntityTypes.COW' in before:
                text = text.replace('EntityType.', 'EntityTypes.')
            for pattern in [r'MC\.gameMode\.interact\(MC\.player, target,[^;]*;', r'MC\.player\.swing\(breedingHand[^;]*;']:
                old, new = re.search(pattern, text), re.search(pattern, before)
                assert old and new, (file, pattern)
                text = text.replace(old[0], new[0])
            return text

        planned[file] = merge(before, adapt_breed(read(PLAN / 'batch11/base' / name)), adapt_breed(read(STAGE / name)), file)
        for name in ['hacks/CollisionsHack.java', 'mixin/CollisionsMovementMixin.java', 'mixin/CollisionsBorderMixin.java']:
            file = java / name
            assert not file.exists() or file.relative_to(ROOT).as_posix() in previous, file
            text = read(STAGE / name)
            if name.endswith('CollisionsHack.java'):
                vehicle = api.split('Compiled from "ServerboundMoveVehiclePacket.java"')[1].split('Compiled from')[0]
                position = None
                if 'net.minecraft.core.PositionAndRotation movingTo();' in vehicle:
                    assert 'net.minecraft.world.phys.Vec3 position();' in read(PLAN / 'batch11/position-26.3.txt')
                    position = 'packet.movingTo().position()'
                elif 'net.minecraft.world.phys.Vec3 position();' in vehicle:
                    position = 'packet.position()'
                else:
                    assert 'public double getX();' in vehicle and 'public double getZ();' in vehicle
                if position:
                    text = text.replace('packet.getX(), packet.getZ()', position + '.x, ' + position + '.z')
            planned[file] = text
        file = java / 'mixin/CollisionsShapeMixin.java'
        assert not file.exists() or file.relative_to(ROOT).as_posix() in previous, file
        planned[file] = shape_hook(api)
        registry = java / 'hack/HackList.java'
        text = original(registry)
        marker = 'public final class HackList implements UpdateListener\n{\n'
        assert text.count(marker) == 1 and 'new CollisionsHack()' not in text, registry
        planned[registry] = text.replace(marker, marker + '\tpublic final CollisionsHack collisionsHack = new CollisionsHack();\n')
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            text = original(config)
            if json.loads(text.lstrip('\ufeff')).get('package') != 'net.wurstclient.mixin':
                continue
            assert all('"' + hook + '"' not in text for hook in HOOKS), config
            planned[config] = text.replace('"client": [', '"client": [\n' + ''.join('    "' + hook + '",\n' for hook in HOOKS).rstrip())
            configs.append(config)
        assert configs, root
        translation = root / 'src/main/resources/assets/wurst/translations/zh_cn.json'
        text = original(translation)
        additions = {'hack.name.collisions': '碰撞控制',
                     'description.wurst.hack.collisions': '为选定方块添加碰撞，可避开岩浆块、阻止进入未加载区块并移除客户端世界边界碰撞。'}
        data = json.loads(text.lstrip('\ufeff'))
        assert not any(key in data for key in additions), translation
        index = text.rfind('}')
        planned[translation] = text[:index].rstrip() + ',\n' + ',\n'.join('  ' + json.dumps(k) + ': ' + json.dumps(v, ensure_ascii=False) for k, v in additions.items()) + '\n}\n'
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
            if file in observed:
                assert file.read_bytes() == observed[file], f'Changed during merge: {file}'
        for file, text in planned.items():
            dest = PLAN / 'batch11/generated' / file.relative_to(ROOT) if args.preview else file
            write(dest, text)
            if not args.preview:
                previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        if not args.preview:
            write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 11 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()

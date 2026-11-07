package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.QuadInstance;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ModelBlockRenderer {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final BlockModelLighter lighter;
    private final boolean ambientOcclusion;
    private final boolean cull;
    private final BlockColors blockColors;
    private final RandomSource random = RandomSource.createThreadLocalInstance(0L);
    private final List<BlockStateModelPart> parts = new ObjectArrayList<>();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final QuadInstance quadInstance = new QuadInstance();
    private int tintCacheIndex = -1;
    private int tintCacheValue;
    private boolean tintSourcesInitialized;
    private final List<@Nullable BlockTintSource> tintSources = new ObjectArrayList<>();
    private final IntList computedTintValues = new IntArrayList();

    public ModelBlockRenderer(boolean ambientOcclusion, boolean cull, BlockColors blockColors) {
        this.lighter = net.neoforged.neoforge.client.model.ao.EnhancedBlockModelLighter.newInstance();
        this.ambientOcclusion = ambientOcclusion;
        this.cull = cull;
        this.blockColors = blockColors;
    }

    public static boolean forceOpaque(boolean cutoutLeaves, BlockState blockState) {
        return !cutoutLeaves && blockState.getBlock() instanceof LeavesBlock;
    }

    public void tesselateBlock(
        BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState blockState, BlockStateModel model, long seed
    ) {
        this.random.setSeed(seed);
        model.collectParts(level, pos, blockState, this.random, this.parts);
        if (!this.parts.isEmpty()) {
            this.lighter.reset();
            try {
                Vec3 offset = blockState.getOffset(pos);
                boolean perPartAO = net.neoforged.neoforge.client.config.NeoForgeClientConfig.INSTANCE.handleAmbientOcclusionPerPart.getAsBoolean();
                boolean useAO = this.ambientOcclusion && (perPartAO || switch(parts.getFirst().ambientOcclusion()) {
                    case TRUE -> true;
                    case DEFAULT -> blockState.getLightEmission(level, pos) == 0;
                    case FALSE -> false;
                });
                if (useAO) {
                    this.tesselateAmbientOcclusion(output, x + (float)offset.x, y + (float)offset.y, z + (float)offset.z, this.parts, level, blockState, pos);
                } else {
                    this.tesselateFlat(output, x + (float)offset.x, y + (float)offset.y, z + (float)offset.z, this.parts, level, blockState, pos);
                }
            } finally {
                this.parts.clear();
                this.resetTintCache();
            }
        }
    }

    private void configureTintCache(BlockState blockState) {
        List<BlockTintSource> tintSources = this.blockColors.getTintSources(blockState);
        int tintSourceCount = tintSources.size();
        if (tintSourceCount > 0) {
            this.tintSources.addAll(tintSources);

            for (int i = 0; i < tintSourceCount; i++) {
                this.computedTintValues.add(-1);
            }
        }
    }

    private void resetTintCache() {
        this.tintCacheIndex = -1;
        if (this.tintSourcesInitialized) {
            this.tintSources.clear();
            this.computedTintValues.clear();
            this.tintSourcesInitialized = false;
        }
    }

    private void tesselateAmbientOcclusion(
        BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos
    ) {
        boolean perPartAO = net.neoforged.neoforge.client.config.NeoForgeClientConfig.INSTANCE.handleAmbientOcclusionPerPart.getAsBoolean();
        int lightEmission = -1;
        int cacheValid = 0;
        int shouldRenderFaceCache = 0;

        for (BlockStateModelPart part : parts) {
            boolean ao = !perPartAO || switch (part.ambientOcclusion()) {
                case TRUE -> true;
                case DEFAULT -> {
                    if (lightEmission == -1) {
                        lightEmission = state.getLightEmission(level, pos);
                    }
                    yield lightEmission == 0;
                }
                case FALSE -> false;
            };
            for (Direction direction : DIRECTIONS) {
                int cacheMask = 1 << direction.ordinal();
                boolean validCacheForDirection = (cacheValid & cacheMask) != 0;
                boolean shouldRenderFace = (shouldRenderFaceCache & cacheMask) != 0;
                if (!validCacheForDirection || shouldRenderFace) {
                    List<BakedQuad> culledQuads = part.getQuads(direction);
                    if (!culledQuads.isEmpty()) {
                        if (!validCacheForDirection) {
                            shouldRenderFace = this.shouldRenderFace(level, pos, state, direction, this.scratchPos.setWithOffset(pos, direction));
                            cacheValid |= cacheMask;
                            if (shouldRenderFace) {
                                shouldRenderFaceCache |= cacheMask;
                            }
                        }

                        if (shouldRenderFace) {
                            BlockPos relativePos = this.scratchPos.setWithOffset(pos, direction);
                            if (!ao) {
                                int lightCoords = this.lighter.getLightCoords(state, level, relativePos);

                                for (BakedQuad quad : culledQuads) {
                                    this.lighter.prepareQuadFlat(level, state, pos, lightCoords, quad, this.quadInstance);
                                    this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
                                }
                            } else
                            for (BakedQuad quad : culledQuads) {
                                if (!quad.materialInfo().ambientOcclusion()) {
                                    this.lighter.prepareQuadFlat(level, state, pos, -1, quad, this.quadInstance);
                                    this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
                                    continue;
                                }
                                this.lighter.prepareQuadAmbientOcclusion(level, state, pos, quad, this.quadInstance);
                                this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
                            }
                        }
                    }
                }
            }

            if (!ao) {
                for (BakedQuad quad : part.getQuads(null)) {
                    this.lighter.prepareQuadFlat(level, state, pos, -1, quad, this.quadInstance);
                    this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
                }
            } else
            for (BakedQuad quad : part.getQuads(null)) {
                if (!quad.materialInfo().ambientOcclusion()) {
                    this.lighter.prepareQuadFlat(level, state, pos, -1, quad, this.quadInstance);
                    this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
                    continue;
                }
                this.lighter.prepareQuadAmbientOcclusion(level, state, pos, quad, this.quadInstance);
                this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
            }
        }
    }

    private void tesselateFlat(
        BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos
    ) {
        int cacheValid = 0;
        int shouldRenderFaceCache = 0;

        for (BlockStateModelPart part : parts) {
            for (Direction direction : DIRECTIONS) {
                int cacheMask = 1 << direction.ordinal();
                boolean validCacheForDirection = (cacheValid & cacheMask) != 0;
                boolean shouldRenderFace = (shouldRenderFaceCache & cacheMask) != 0;
                if (!validCacheForDirection || shouldRenderFace) {
                    List<BakedQuad> culledQuads = part.getQuads(direction);
                    if (!culledQuads.isEmpty()) {
                        BlockPos relativePos = this.scratchPos.setWithOffset(pos, direction);
                        if (!validCacheForDirection) {
                            shouldRenderFace = this.shouldRenderFace(level, pos, state, direction, relativePos);
                            cacheValid |= cacheMask;
                            if (shouldRenderFace) {
                                shouldRenderFaceCache |= cacheMask;
                            }
                        }

                        if (shouldRenderFace) {
                            int lightCoords = this.lighter.getLightCoords(state, level, relativePos);

                            for (BakedQuad quad : culledQuads) {
                                this.lighter.prepareQuadFlat(level, state, pos, lightCoords, quad, this.quadInstance);
                                this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
                            }
                        }
                    }
                }
            }

            for (BakedQuad quad : part.getQuads(null)) {
                this.lighter.prepareQuadFlat(level, state, pos, -1, quad, this.quadInstance);
                this.putQuadWithTint(output, x, y, z, level, state, pos, quad);
            }
        }
    }

    private boolean shouldRenderFace(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction direction, BlockPos neighborPos) {
        if (!this.cull) {
            return true;
        } else {
            BlockState neighborState = level.getBlockState(neighborPos);
            return Block.shouldRenderFace(level, pos, state, neighborState, direction);
        }
    }

    /** @deprecated Neo: use {@link #shouldRenderFace(BlockAndTintGetter, BlockPos, BlockState, Direction, BlockPos)} instead */
    @Deprecated
    private boolean shouldRenderFace(BlockAndTintGetter level, BlockState state, Direction direction, BlockPos neighborPos) {
        if (!this.cull) {
            return true;
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        return Block.shouldRenderFace(state, neighborState, direction);
    }

    private void putQuadWithTint(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad) {
        int tintIndex = quad.materialInfo().tintIndex();
        if (tintIndex != -1) {
            this.quadInstance.multiplyColor(this.getTintColor(level, state, pos, tintIndex));
        }

        output.put(x, y, z, quad, this.quadInstance);
    }

    private int getTintColor(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex) {
        if (this.tintCacheIndex == tintIndex) {
            return this.tintCacheValue;
        }

        int tintColor = this.computeTintColor(level, state, pos, tintIndex);
        this.tintCacheIndex = tintIndex;
        this.tintCacheValue = tintColor;
        return tintColor;
    }

    private int computeTintColor(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex) {
        if (!this.tintSourcesInitialized) {
            this.configureTintCache(state);
            this.tintSourcesInitialized = true;
            if (this.tintSources.isEmpty()) {
                net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions.of(state)
                        .collectDynamicTintValues(state, level, pos, this.computedTintValues);
                if (!this.computedTintValues.isEmpty()) {
                    for (int i = 0; i < this.computedTintValues.size(); i++) {
                        this.tintSources.add(null);
                    }
                }
            }
        }

        if (tintIndex >= this.tintSources.size()) {
            return -1;
        } else {
            BlockTintSource tintSource = this.tintSources.set(tintIndex, null);
            if (tintSource != null) {
                int computedTintValue = tintSource.colorInWorld(state, level, pos);
                this.computedTintValues.set(tintIndex, computedTintValue);
                return computedTintValue;
            } else {
                return this.computedTintValues.getInt(tintIndex);
            }
        }
    }
}

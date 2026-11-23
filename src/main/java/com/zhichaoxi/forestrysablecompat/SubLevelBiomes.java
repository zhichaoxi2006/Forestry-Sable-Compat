package com.zhichaoxi.forestrysablecompat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves Forestry's biome lookups for block entities inside a Sable sub-level.
 * <p>
 * Sable keeps sub-level blocks in a plot far away from the world (the "plotyard"); a block entity's
 * {@link net.minecraft.world.level.block.entity.BlockEntity#getBlockPos()} is that plot position.
 * Its plot chunks carry the biome that was sampled when the sub-level was assembled, so a plain
 * {@code level.getBiome(plotPos)} returns the <em>assembly</em> biome forever, even after the
 * structure has moved somewhere else.
 * <p>
 * This helper is used from Forestry's own biome lookups. It detects that the position is inside a
 * sub-level plot and projects the position through the sub-level's current pose into the world; the
 * biome is then looked up at the structure's actual world location. Normal (non-plot) positions are
 * passed through unchanged. Sable itself is not modified.
 */
public final class SubLevelBiomes {
    private SubLevelBiomes() {
    }

    /**
     * @return the biome at the world position corresponding to {@code pos}, or the vanilla biome at
     * {@code pos} when {@code pos} is not inside a Sable sub-level
     */
    public static Holder<Biome> getBiome(final LevelReader level, final BlockPos pos) {
        if (level instanceof final Level world) {
            final SubLevel subLevel = Sable.HELPER.getContaining(world, pos);
            if (subLevel != null) {
                // logicalPose() maps plot coordinates (where the block entity lives) to world space.
                final Vec3 worldPos = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(pos));
                return world.getBiome(BlockPos.containing(worldPos));
            }
        }
        return level.getBiome(pos);
    }
}

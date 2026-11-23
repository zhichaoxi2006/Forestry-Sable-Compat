package com.zhichaoxi.forestrysablecompat.mixin;

import com.zhichaoxi.forestrysablecompat.SubLevelBiomes;
import forestry.core.engine.climate.ForestryClimateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The direct {@code getBiomeState(level, pos)} API used by Foresty's hive/village code (and by
 * add-ons) has the same plot-position lookup.
 */
@Mixin(ForestryClimateManager.class)
public class ForestryClimateManagerMixin {
    @Redirect(
            method = "getBiomeState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> sable$getBiome(final LevelReader level, final BlockPos pos) {
        return SubLevelBiomes.getBiome(level, pos);
    }
}

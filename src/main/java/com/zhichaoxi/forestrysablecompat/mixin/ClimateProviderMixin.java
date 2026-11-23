package com.zhichaoxi.forestrysablecompat.mixin;

import com.zhichaoxi.forestrysablecompat.SubLevelBiomes;
import forestry.core.engine.climate.ClimateProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Every Forestry climate provider (apiary, bee house, alveary, hive, ...) caches the biome it reads
 * in its constructor and is recreated every 64 ticks. Redirecting that read is enough for the
 * temperature/humidity to follow a moving sub-level.
 */
@Mixin(ClimateProvider.class)
public class ClimateProviderMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> sable$getBiome(final LevelReader level, final BlockPos pos) {
        return SubLevelBiomes.getBiome(level, pos);
    }
}

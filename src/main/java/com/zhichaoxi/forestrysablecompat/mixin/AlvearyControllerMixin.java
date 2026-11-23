package com.zhichaoxi.forestrysablecompat.mixin;

import com.zhichaoxi.forestrysablecompat.SubLevelBiomes;
import forestry.apiculture.alveary.multiblock.AlvearyController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Alveary controller: same plot-position problem as the apiary. Its cached climate provider is
 * covered by {@code ClimateProviderMixin}; this redirect covers the live {@code getBiome()} call
 * (used for the hellish-climate check).
 */
@Mixin(AlvearyController.class)
public class AlvearyControllerMixin {
    @Redirect(
            method = "getBiome",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> sable$getBiome(final Level level, final BlockPos pos) {
        return SubLevelBiomes.getBiome(level, pos);
    }
}

package com.zhichaoxi.forestrysablecompat.mixin;

import com.zhichaoxi.forestrysablecompat.SubLevelBiomes;
import forestry.apiculture.hives.HiveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Natural hives can be physicalized too; their {@code getBiome()} has the same plot-position
 * lookup as the apiary.
 */
@Mixin(HiveBlockEntity.class)
public class HiveBlockEntityMixin {
    @Redirect(
            method = "getBiome",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> sable$getBiome(final Level level, final BlockPos pos) {
        return SubLevelBiomes.getBiome(level, pos);
    }
}

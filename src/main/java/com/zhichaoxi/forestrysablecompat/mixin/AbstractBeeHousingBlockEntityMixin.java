package com.zhichaoxi.forestrysablecompat.mixin;

import com.zhichaoxi.forestrysablecompat.SubLevelBiomes;
import forestry.apiculture.bees.AbstractBeeHousingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Apiary and bee house: {@code getBiome()} currently asks the level for the biome at the block
 * entity's position, which is the Sable plot position. Use the sub-level's current world position
 * instead, so the climate follows the structure when it moves.
 */
@Mixin(AbstractBeeHousingBlockEntity.class)
public class AbstractBeeHousingBlockEntityMixin {
    @Redirect(
            method = "getBiome",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> sable$getBiome(final Level level, final BlockPos pos) {
        return SubLevelBiomes.getBiome(level, pos);
    }
}

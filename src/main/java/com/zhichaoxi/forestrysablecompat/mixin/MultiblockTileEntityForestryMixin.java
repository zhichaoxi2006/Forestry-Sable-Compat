package com.zhichaoxi.forestrysablecompat.mixin;

import com.zhichaoxi.forestrysablecompat.ForestryContents;
import forestry.core.platform.multiblock.MultiblockTileEntityForestry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Multiblock parts override {@link MultiblockTileEntityForestry#clearContent()}, so the injection in
 * {@link TileForestryMixin} never runs for them. Their implementation only empties the block
 * entity's own inventory (which is empty on an alveary part); the shared controller inventory, where
 * the contents actually live, has to be emptied as well or
 * {@code MultiblockTileEntityForestry.setRemoved()} drops it while the part is moved.
 */
@Mixin(MultiblockTileEntityForestry.class)
public abstract class MultiblockTileEntityForestryMixin {
    @Inject(method = "clearContent", at = @At("HEAD"))
    private void forestrySableCompat$clearContent(final CallbackInfo callback) {
        ForestryContents.clearBeforeMove(this);
    }
}

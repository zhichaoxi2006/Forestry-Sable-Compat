package com.zhichaoxi.forestrysablecompat.mixin;

import com.zhichaoxi.forestrysablecompat.ForestryContents;
import forestry.core.platform.tile.TileForestry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives {@link TileForestry#clearContent()} the behaviour its {@code Clearable}/{@code Container}
 * contract promises.
 * <p>
 * Forestry's implementation is empty, so Sable's {@code Clearable.tryClear()} before a block is
 * moved into (or out of) a sub-level clears nothing, and the block's own removal logic drops the
 * contents into the world while the copied block entity keeps them.
 * <p>
 * This is the same extension point Sable uses for vanilla block entities (see
 * {@code AbstractFurnaceBlockEntityMixin} in Sable itself), so no Sable class has to be mixed into
 * and other "Sable Fix" mods keep working.
 */
@Mixin(TileForestry.class)
public abstract class TileForestryMixin {
    @Inject(method = "clearContent", at = @At("HEAD"))
    private void forestrySableCompat$clearContent(final CallbackInfo callback) {
        ForestryContents.clearBeforeMove(this);
    }
}

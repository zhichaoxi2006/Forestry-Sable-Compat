package com.zhichaoxi.forestrysablecompat.mixin;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import forestry.core.platform.multiblock.MultiblockTileEntityForestry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Strips the stale holder anchor from the NBT that Sable copies into a sub-level (and back out of
 * one).
 * <p>
 * The saved {@code anchorPos} still points at the holder's old position. Sable plots live in the
 * same level, so {@code MultiblockValidation.resolveExistingController} follows that stale anchor
 * index entry, finds the controller that is still hosted by the blocks being moved, and reuses it
 * instead of seeding a fresh one from the moved payload. That path then clears every member's stash,
 * which is where the alveary inventory travelled, so an assembled multiblock came out of a move
 * empty. Without the anchor the destination validates its own structure, seeds from the copied
 * payload and the contents survive.
 * <p>
 * Only the copied tag is touched; the block entity that stays behind keeps its live anchor, and
 * {@code MultiblockValidation} re-anchors every member once the moved structure validates.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public final class SubLevelAssemblyHelperMixin {
    /** {@code MultiblockTileEntityForestry.ANCHOR_KEY}, which is private. */
    private static final String ANCHOR_KEY = "anchorPos";

    @Redirect(
            method = "moveBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;saveWithFullMetadata(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"
            ),
            require = 1
    )
    private static CompoundTag forestrySableCompat$saveWithoutStaleAnchor(final BlockEntity blockEntity,
                                                                          final HolderLookup.Provider registries) {
        final CompoundTag tag = blockEntity.saveWithFullMetadata(registries);
        if (blockEntity instanceof MultiblockTileEntityForestry<?>) {
            tag.remove(ANCHOR_KEY);
        }
        return tag;
    }
}

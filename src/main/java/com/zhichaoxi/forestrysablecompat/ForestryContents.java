package com.zhichaoxi.forestrysablecompat;

import forestry.core.engine.circuits.ISocketable;
import forestry.core.platform.multiblock.MultiblockController;
import forestry.core.platform.multiblock.MultiblockTileEntityForestry;
import forestry.core.platform.tile.TileForestry;
import net.minecraft.world.item.ItemStack;

/**
 * Clears the contents of a Forestry block entity before Sable removes the block from the level.
 * <p>
 * Sable saves the block entity's NBT, then asks the block entity to clear itself
 * ({@code Clearable.tryClear}), then destroys the block. Whatever is still inside at that point is
 * dropped by the block's own removal logic: the contents would exist both on the ground and inside
 * the block that was moved into the sub-level.
 * <p>
 * Forestry advertises {@code Clearable} through {@code Container} but its
 * {@link TileForestry#clearContent()} is an empty method, and the circuit sockets
 * ({@link ISocketable}) are a second, separate inventory that no {@code Container} view exposes.
 * Multiblock machines (the alveary, the managed farm) are a separate hierarchy again: their
 * inventory lives in the {@link MultiblockController} hosted by the holder member, so it has to be
 * emptied through {@link MultiblockTileEntityForestry#getController()} as well. Both have to be
 * emptied here, or the contents are duplicated.
 */
public final class ForestryContents {
    private ForestryContents() {
    }

    /**
     * Empties everything Forestry would drop when the block is removed, without touching anything
     * else in the block entity. Safe to call more than once and on any block entity.
     */
    public static void clearBeforeMove(final Object blockEntity) {
        if (blockEntity instanceof final TileForestry tile) {
            // The inventory Container.dropContents() would spill.
            tile.getInternalInventory().clearContent();
        }

        if (blockEntity instanceof final MultiblockTileEntityForestry<?> multiblock) {
            // The shared inventory of an assembled multiblock travels in the holder's payload and is
            // dropped by MultiblockTileEntityForestry.setRemoved() when the last member is removed.
            final MultiblockController controller = multiblock.getController();
            if (controller != null) {
                controller.getInternalInventory().clearContent();
            }
        }

        // Circuit boards live in a separate InventoryAdapter that is only reachable through
        // ISocketable; BlockBase.onRemove() drops them through InventoryUtil.dropSockets().
        if (blockEntity instanceof final ISocketable socketable) {
            for (int slot = 0; slot < socketable.getSocketCount(); slot++) {
                socketable.setSocket(slot, ItemStack.EMPTY);
            }
        }
    }
}

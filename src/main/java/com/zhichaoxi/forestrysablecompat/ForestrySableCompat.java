package com.zhichaoxi.forestrysablecompat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Forestry: Community Edition x Sable compatibility.
 * <p>
 * Sable clears the contents of a block entity before moving it into (or out of) a sub-level, because
 * the block's own removal logic would otherwise drop the contents into the world while the copied
 * block entity keeps them. Forestry advertises {@code Clearable} through {@code Container} but its
 * {@code clearContent()} is empty, so that contract silently does nothing and every physicalization
 * round trip duplicates (or, for the block being destroyed, loses) what the machine holds.
 */
@Mod(ForestrySableCompat.MOD_ID)
public final class ForestrySableCompat {
    public static final String MOD_ID = "forestry_sable_compat";

    public ForestrySableCompat(final IEventBus modBus) {
    }
}

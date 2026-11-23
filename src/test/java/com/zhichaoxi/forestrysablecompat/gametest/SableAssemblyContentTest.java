package com.zhichaoxi.forestrysablecompat.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import forestry.apiculture.alveary.multiblock.AlvearyController;
import forestry.core.content.machines.tiles.TileCentrifuge;
import forestry.core.engine.circuits.EnumCircuitBoardType;
import forestry.core.engine.circuits.ISocketable;
import forestry.core.features.CoreItems;
import forestry.core.platform.multiblock.MultiblockController;
import forestry.core.platform.multiblock.MultiblockIndex;
import forestry.core.platform.multiblock.MultiblockValidation;
import forestry.core.platform.tile.TileForestry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Reproduces the Forestry: Community Edition x Sable content bug without any client.
 * <p>
 * Each test fills a Forestry block with known contents, moves it into a sub-level the way Sable does
 * when a structure is physicalized, and asserts two things:
 * <ol>
 *     <li>the copy that ends up inside the sub-level still holds the contents, and</li>
 *     <li>no copy of them was dropped in the world at the position the block came from.</li>
 * </ol>
 * The second one is what fails on unpatched Sable, and it is also what the generic "Sable Fix Mod"
 * cannot fix for Forestry: it clears {@code Container} slots, but Forestry's circuit sockets are a
 * separate {@code InventoryAdapter} reachable only through {@code ISocketable}.
 * <p>
 * The alveary tests cover the other hierarchy: a 3x3x3 multiblock whose inventory is not on the
 * block entities at all but in the {@link AlvearyController} hosted by the lowest member, moved as
 * part of the 36 blocks (27 components plus the wooden slab cap).
 */
@GameTestHolder(SableAssemblyContentTest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SableAssemblyContentTest {
    static final String MOD_ID = "forestry_sable_compat_gametest";

    /** Inside the 16x16x16 "empty" template. */
    private static final BlockPos MACHINE_POS = new BlockPos(8, 1, 8);
    private static final ResourceLocation CENTRIFUGE_ID =
            ResourceLocation.fromNamespaceAndPath("forestry", "centrifuge");

    /**
     * Lowest corner of the 3x3x3 alveary, still inside the template: the box ends at +2, the wooden
     * slab cap sits at +3 and the entrance air ring needs one block around the top layer.
     */
    private static final BlockPos ALVEARY_POS = new BlockPos(6, 1, 6);
    private static final ResourceLocation ALVEARY_ID =
            ResourceLocation.fromNamespaceAndPath("forestry", "alveary_block");

    /** Product slot of the alveary's shared {@code BeeHousingInventory}. */
    private static final int ALVEARY_PRODUCT_SLOT = 2;

    private SableAssemblyContentTest() {
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void inventorySurvivesPhysicalization(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(MACHINE_POS);
        final TileForestry tile = placeMachine(helper);

        final ItemStack stored = new ItemStack(Items.DIAMOND, 5);
        tile.getInternalInventory().setItem(0, stored.copy());

        final ServerSubLevel subLevel = assemble(level, pos);
        final List<ItemEntity> drops = dropsAt(level, pos);

        final BlockEntity moved = plotBlockEntity(subLevel);
        helper.assertTrue(moved instanceof TileForestry,
                "the physicalized block came back as " + moved + " instead of a Forestry machine" + describe(drops));
        final ItemStack kept = ((TileForestry) moved).getInternalInventory().getItem(0);

        helper.assertTrue(ItemStack.isSameItemSameComponents(stored, kept),
                "the sub-level machine held " + kept + " instead of " + stored + describe(drops));
        helper.assertTrue(!dropped(level, pos, stored),
                "the physicalized machine dropped its inventory" + describe(drops));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void socketSurvivesPhysicalization(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(MACHINE_POS);
        final TileForestry tile = placeMachine(helper);

        final ItemStack chipset = newCircuitBoard();
        ((ISocketable) tile).setSocket(0, chipset.copy());

        final ServerSubLevel subLevel = assemble(level, pos);
        final List<ItemEntity> drops = dropsAt(level, pos);

        final BlockEntity moved = plotBlockEntity(subLevel);
        helper.assertTrue(moved instanceof ISocketable,
                "the physicalized block came back as " + moved + " without a circuit socket" + describe(drops));
        final ItemStack kept = ((ISocketable) moved).getSocket(0);

        helper.assertTrue(ItemStack.isSameItemSameComponents(chipset, kept),
                "the sub-level machine's socket held " + kept + " instead of " + chipset + describe(drops));
        helper.assertTrue(!dropped(level, pos, chipset),
                "the physicalized machine dropped its circuit board" + describe(drops));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void contentsSurviveRoundTrip(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(MACHINE_POS);
        final TileForestry tile = placeMachine(helper);

        final ItemStack stored = new ItemStack(Items.DIAMOND, 5);
        final ItemStack chipset = newCircuitBoard();
        tile.getInternalInventory().setItem(0, stored.copy());
        ((ISocketable) tile).setSocket(0, chipset.copy());

        final ServerSubLevel subLevel = assemble(level, pos);
        final Level plot = subLevel.getLevel();
        final BlockPos plotCenter = subLevel.getPlot().getCenterBlock();

        // De-physicalize: move the plot block back to where it came from, the same call Sable makes
        // when a sub-level is dissolved into blocks again.
        SubLevelAssemblyHelper.moveBlocks((ServerLevel) plot,
                new SubLevelAssemblyHelper.AssemblyTransform(plotCenter, pos, 0, Rotation.NONE, level),
                List.of(plotCenter));

        final BlockEntity restored = level.getBlockEntity(pos);
        final List<ItemEntity> drops = dropsAt(level, pos);
        helper.assertTrue(restored instanceof TileForestry,
                "after de-physicalization the block at the origin was " + restored + describe(drops));

        final TileForestry restoredTile = (TileForestry) restored;
        final ItemStack keptInventory = restoredTile.getInternalInventory().getItem(0);
        final ItemStack keptSocket = ((ISocketable) restoredTile).getSocket(0);

        helper.assertTrue(ItemStack.isSameItemSameComponents(stored, keptInventory),
                "the de-physicalized machine held " + keptInventory + " instead of " + stored + describe(drops));
        helper.assertTrue(ItemStack.isSameItemSameComponents(chipset, keptSocket),
                "the de-physicalized machine's socket held " + keptSocket + " instead of " + chipset + describe(drops));
        helper.assertTrue(!dropped(level, pos, stored) && !dropped(level, pos, chipset),
                "de-physicalization dropped contents" + describe(drops));
        helper.succeed();
    }

    /**
     * The 3x3x3 alveary is the hard case: its inventory is not on the block entities at all. It lives
     * in the {@link AlvearyController} hosted by the lowest member, and a moved structure has to carry
     * it in the holder's {@code multiblockData} payload instead of dropping it when the moved members
     * are removed.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void alvearyContentsSurvivePhysicalization(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos base = helper.absolutePos(ALVEARY_POS);
        final ItemStack stored = new ItemStack(Items.DIAMOND, 3);

        final MultiblockController controller = buildAndFillAlveary(helper, base, stored);
        final List<BlockPos> blocks = alvearyBlocks(base);

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, base, blocks,
                new BoundingBox3i(base.getX(), base.getY(), base.getZ(),
                        base.getX() + 3, base.getY() + 4, base.getZ() + 3));
        final List<ItemEntity> drops = dropsAt(level, base);

        helper.assertTrue(!dropped(level, base, stored),
                "the physicalized alveary dropped its contents" + describe(drops));

        // Sub-level blocks validate from onLoad when their chunk loads; do the same explicitly so the
        // assertion does not depend on chunk order.
        final Level plot = subLevel.getLevel();
        final BlockPos plotBase = subLevel.getPlot().getCenterBlock();
        MultiblockValidation.validateAt(plot, plotBase);

        final MultiblockController plotController = MultiblockIndex.get(plot, plotBase);
        helper.assertTrue(plotController instanceof AlvearyController,
                "the alveary did not re-form inside the sub-level (controller: " + plotController + ")" + describe(drops));

        final ItemStack kept = plotController.getInternalInventory().getItem(ALVEARY_PRODUCT_SLOT);
        helper.assertTrue(ItemStack.isSameItemSameComponents(stored, kept),
                "the sub-level alveary held " + kept + " instead of " + stored + describe(drops));
        helper.succeed();
    }

    /**
     * Same alveary, moved back out of the sub-level. The de-physicalization direction goes through the
     * same move, so it has to re-form at the original position with its contents intact as well.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void alvearyContentsSurviveRoundTrip(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos base = helper.absolutePos(ALVEARY_POS);
        final ItemStack stored = new ItemStack(Items.DIAMOND, 3);

        buildAndFillAlveary(helper, base, stored);
        final List<BlockPos> blocks = alvearyBlocks(base);

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, base, blocks,
                new BoundingBox3i(base.getX(), base.getY(), base.getZ(),
                        base.getX() + 3, base.getY() + 4, base.getZ() + 3));
        final Level plot = subLevel.getLevel();
        final BlockPos plotBase = subLevel.getPlot().getCenterBlock();
        final List<BlockPos> plotBlocks = offset(plotBase, base, blocks);

        SubLevelAssemblyHelper.moveBlocks((ServerLevel) plot,
                new SubLevelAssemblyHelper.AssemblyTransform(plotBase, base, 0, Rotation.NONE, level),
                plotBlocks);
        MultiblockValidation.validateAt(level, base);

        final List<ItemEntity> drops = dropsAt(level, base);
        helper.assertTrue(!dropped(level, base, stored), "de-physicalization dropped the alveary contents" + describe(drops));

        final MultiblockController controller = MultiblockIndex.get(level, base);
        helper.assertTrue(controller instanceof AlvearyController,
                "the alveary did not re-form at the origin (controller: " + controller + ")" + describe(drops));

        final ItemStack kept = controller.getInternalInventory().getItem(ALVEARY_PRODUCT_SLOT);
        helper.assertTrue(ItemStack.isSameItemSameComponents(stored, kept),
                "the de-physicalized alveary held " + kept + " instead of " + stored + describe(drops));
        helper.succeed();
    }

    private static MultiblockController buildAndFillAlveary(final GameTestHelper helper, final BlockPos base,
                                                            final ItemStack stored) {
        helper.killAllEntities();
        final Block alveary = BuiltInRegistries.BLOCK.get(ALVEARY_ID);
        helper.assertTrue(alveary != Blocks.AIR, ALVEARY_ID + " is not registered");
        for (final BlockPos pos : BlockPos.betweenClosed(ALVEARY_POS, ALVEARY_POS.offset(2, 2, 2))) {
            helper.setBlock(pos, alveary);
        }
        for (final BlockPos pos : BlockPos.betweenClosed(ALVEARY_POS.offset(0, 3, 0), ALVEARY_POS.offset(2, 3, 2))) {
            helper.setBlock(pos, Blocks.OAK_SLAB);
        }

        MultiblockValidation.validateAt(helper.getLevel(), base);
        final MultiblockController controller = MultiblockIndex.get(helper.getLevel(), base);
        helper.assertTrue(controller instanceof AlvearyController,
                "the alveary did not form at " + base + " (controller: " + controller + ")");
        helper.assertTrue(controller.isAssembled(), "the alveary controller is not assembled");

        controller.getInternalInventory().setItem(ALVEARY_PRODUCT_SLOT, stored.copy());
        helper.assertTrue(ItemStack.isSameItemSameComponents(stored, controller.getInternalInventory().getItem(ALVEARY_PRODUCT_SLOT)),
                "the alveary inventory did not keep the inserted stack");
        return controller;
    }

    private static TileForestry placeMachine(final GameTestHelper helper) {
        helper.killAllEntities();
        final Block block = BuiltInRegistries.BLOCK.get(CENTRIFUGE_ID);
        helper.assertTrue(block != Blocks.AIR, CENTRIFUGE_ID + " is not registered");
        helper.setBlock(MACHINE_POS, block);

        final BlockEntity blockEntity = helper.getBlockEntity(MACHINE_POS);
        helper.assertTrue(blockEntity instanceof TileCentrifuge,
                MACHINE_POS + " did not create a centrifuge block entity but " + blockEntity);
        return (TileForestry) blockEntity;
    }

    private static ItemStack newCircuitBoard() {
        return CoreItems.CIRCUITBOARDS.stack(EnumCircuitBoardType.BASIC, 1);
    }

    private static ServerSubLevel assemble(final ServerLevel level, final BlockPos pos) {
        return SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
    }

    /** The 27 alveary blocks plus the 3x3 wooden slab cap, lowest position first (the canonical holder). */
    private static List<BlockPos> alvearyBlocks(final BlockPos base) {
        final List<BlockPos> blocks = new ArrayList<>(36);
        for (final BlockPos pos : BlockPos.betweenClosed(base, base.offset(2, 2, 2))) {
            blocks.add(pos.immutable());
        }
        for (final BlockPos pos : BlockPos.betweenClosed(base.offset(0, 3, 0), base.offset(2, 3, 2))) {
            blocks.add(pos.immutable());
        }
        return blocks;
    }

    /** Maps positions in the world box onto the same offsets in the plot box. */
    private static List<BlockPos> offset(final BlockPos newBase, final BlockPos oldBase, final List<BlockPos> blocks) {
        final List<BlockPos> result = new ArrayList<>(blocks.size());
        for (final BlockPos pos : blocks) {
            result.add(newBase.offset(pos.getX() - oldBase.getX(), pos.getY() - oldBase.getY(), pos.getZ() - oldBase.getZ()));
        }
        return result;
    }

    private static BlockEntity plotBlockEntity(final ServerSubLevel subLevel) {
        final Level plot = subLevel.getLevel();
        return plot.getBlockEntity(subLevel.getPlot().getCenterBlock());
    }

    private static List<ItemEntity> dropsAt(final ServerLevel level, final BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8.0));
    }

    private static boolean dropped(final ServerLevel level, final BlockPos pos, final ItemStack stack) {
        return dropsAt(level, pos).stream()
                .map(ItemEntity::getItem)
                .anyMatch(dropped -> ItemStack.isSameItemSameComponents(dropped, stack));
    }

    private static String describe(final List<ItemEntity> drops) {
        if (drops.isEmpty()) {
            return "; nothing was dropped";
        }
        final StringBuilder message = new StringBuilder("; dropped at the origin:");
        for (final ItemEntity drop : drops) {
            message.append(' ').append(drop.getItem());
        }
        return message.toString();
    }
}

package com.zhichaoxi.forestrysablecompat.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import forestry.api.IForestryApi;
import forestry.apiculture.bees.AbstractBeeHousingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Verifies that the compat mod makes Forestry read the biome at a sub-level's <em>current world
 * position</em>, while Sable's own plot biome (used by {@code level.getBiome(plotPos)}) is left
 * unchanged.
 * <p>
 * The test runs in the Nether because it reliably has multiple biomes close together. It finds two
 * positions with different biomes, assembles an apiary in the first, then moves the sub-level's pose
 * so the apiary is over the second. The apiary must report the second biome; the plot chunk must
 * still report the first.
 */
@GameTestHolder(SubLevelBiomeTest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubLevelBiomeTest {
    static final String MOD_ID = "forestry_sable_compat_gametest";

    /** Inside the 16x16x16 "empty" template. */
    private static final BlockPos ORIGIN = new BlockPos(8, 1, 8);
    private static final ResourceLocation APIARY_ID =
            ResourceLocation.fromNamespaceAndPath("forestry", "apiary");

    private SubLevelBiomeTest() {
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void apiaryUsesWorldBiomeOfSubLevel(final GameTestHelper helper) {
        final ServerLevel overworld = helper.getLevel();
        final ServerLevel level = overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(level != null, "the nether is not available");

        final BlockPos templateOrigin = helper.absolutePos(ORIGIN);
        final BlockPos origin = new BlockPos(templateOrigin.getX(), 64, templateOrigin.getZ());

        final BlockPos biomePosA = origin;
        final Holder<Biome> biomeA = level.getBiome(biomePosA);
        final ResourceKey<Biome> keyA = biomeA.unwrapKey().orElse(null);
        helper.assertTrue(keyA != null, "no biome key at " + biomePosA);

        final BlockPos biomePosB = findDifferentBiome(level, origin, keyA, 16384);
        helper.assertTrue(biomePosB != null, "no second biome near " + origin + " (biome A: " + keyA.location() + ")");
        final Holder<Biome> biomeB = level.getBiome(biomePosB);
        helper.assertTrue(biomeB.unwrapKey().isPresent() && !biomeB.unwrapKey().get().equals(keyA),
                "could not find a biome different from " + keyA.location());

        final Block apiary = BuiltInRegistries.BLOCK.get(APIARY_ID);
        helper.assertTrue(apiary != Blocks.AIR, APIARY_ID + " is not registered");
        level.getChunkAt(biomePosA);
        level.setBlock(biomePosA, apiary.defaultBlockState(), 3);
        helper.assertTrue(level.getBlockEntity(biomePosA) instanceof AbstractBeeHousingBlockEntity,
                "no apiary block entity at " + biomePosA + ": " + level.getBlockEntity(biomePosA));

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, biomePosA, List.of(biomePosA),
                new BoundingBox3i(biomePosA.getX(), biomePosA.getY(), biomePosA.getZ(),
                        biomePosA.getX() + 1, biomePosA.getY() + 1, biomePosA.getZ() + 1));
        final BlockPos plotPos = subLevel.getPlot().getCenterBlock();
        final BlockEntity moved = level.getBlockEntity(plotPos);
        helper.assertTrue(moved instanceof AbstractBeeHousingBlockEntity,
                "the physicalized apiary came back as " + moved);

        // Sable's own lookup still sees the biome that was captured at assembly time.
        helper.assertTrue(level.getBiome(plotPos).unwrapKey().orElseThrow().equals(keyA),
                "the plot biome was not " + keyA.location() + " after assembly: " + level.getBiome(plotPos));

        // Move the sub-level so the apiary block sits in the second biome. The block entity stays in
        // its plot chunk; only the sub-level's pose changes.
        final Vec3 before = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotPos));
        final Vec3 target = Vec3.atCenterOf(biomePosB);
        subLevel.logicalPose().position().add(target.x - before.x, target.y - before.y, target.z - before.z);
        subLevel.updateLastPose();

        final AbstractBeeHousingBlockEntity housing = (AbstractBeeHousingBlockEntity) moved;
        final Holder<Biome> expected = level.getBiome(BlockPos.containing(target));
        final Holder<Biome> actual = housing.getBiome();
        helper.assertTrue(actual.unwrapKey().equals(expected.unwrapKey()),
                "the apiary reported " + actual.unwrapKey().map(ResourceKey::location).orElse(null)
                        + " instead of the biome at the sub-level's world position "
                        + expected.unwrapKey().map(ResourceKey::location).orElse(null));

        // The climate provider must follow too, not only the direct getBiome() call.
        housing.setLevel(level);
        helper.assertTrue(housing.temperature() == IForestryApi.INSTANCE.getClimateManager().getTemperature(expected),
                "apiary temperature was " + housing.temperature() + " instead of "
                        + IForestryApi.INSTANCE.getClimateManager().getTemperature(expected));
        helper.assertTrue(housing.humidity() == IForestryApi.INSTANCE.getClimateManager().getHumidity(expected),
                "apiary humidity was " + housing.humidity() + " instead of "
                        + IForestryApi.INSTANCE.getClimateManager().getHumidity(expected));

        // Sable's plot lookup must still be the assembly biome after the move.
        helper.assertTrue(level.getBiome(plotPos).unwrapKey().orElseThrow().equals(keyA),
                "the plot biome changed after moving the sub-level: " + level.getBiome(plotPos));
        helper.succeed();
    }

    private static BlockPos findDifferentBiome(final ServerLevel level, final BlockPos origin,
                                               final ResourceKey<Biome> excluded, final int maxRadius) {
        for (int radius = 0; radius <= maxRadius; radius += 64) {
            for (int step = 0; step < 32; step++) {
                final double angle = step * Math.PI * 2.0 / 32.0;
                final BlockPos candidate = new BlockPos(
                        origin.getX() + (int) Math.round(Math.cos(angle) * radius), origin.getY(),
                        origin.getZ() + (int) Math.round(Math.sin(angle) * radius));
                final Optional<ResourceKey<Biome>> key = level.getBiome(candidate).unwrapKey();
                if (key.isPresent() && !key.get().equals(excluded)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}

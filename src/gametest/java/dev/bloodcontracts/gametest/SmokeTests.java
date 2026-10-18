package dev.bloodcontracts.gametest;

import dev.bloodcontracts.BloodContracts;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class SmokeTests {
    @GameTest(template = BloodContractsGameTests.TEMPLATE)
    public static void modLoadsWithoutCreateOrCbc(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(BloodContracts.MODID), "mod not loaded");
        helper.succeed();
    }
}

package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.TestSupport.hit;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.core.BountyManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Blood Contracts advancement tab. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class AdvancementTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    public static boolean done(GameTestHelper helper, ServerPlayer player, String name) {
        AdvancementHolder adv = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, name));
        helper.assertTrue(adv != null, "advancement " + name + " not loaded");
        return player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    @GameTest(template = T, batch = "adv_place_claim")
    public static void signingAndCollectingGrantAdvancements(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer hunter = TestPlayers.hunter(helper);
        helper.assertTrue(!done(helper, sponsor, "root"), "root granted too early");
        hold(sponsor, new ItemStack(Items.DIAMOND));
        placeFromHand(manager, sponsor, target);
        helper.assertTrue(done(helper, sponsor, "root"), "signing a contract did not grant the root advancement");
        helper.assertTrue(!done(helper, hunter, "blood_money"), "blood_money granted too early");

        hit(target, target.damageSources().playerAttack(hunter), 1000f);

        helper.assertTrue(done(helper, hunter, "blood_money"), "collecting a bounty did not grant blood_money");
        helper.assertTrue(!done(helper, sponsor, "blood_money"), "the sponsor did not collect anything");
        helper.succeed();
    }

    @GameTest(template = T, batch = "adv_most_wanted")
    public static void threeBountiesMakeYouPublicEnemy(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer target = TestPlayers.target(helper);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(!done(helper, target, "most_wanted"), "most_wanted granted with " + i + " bounties");
            ServerPlayer sponsor = TestPlayers.create(helper, "sponsor", new Vec3(1.5, 1, 1.5 + i));
            hold(sponsor, new ItemStack(Items.EMERALD));
            placeFromHand(manager, sponsor, target);
        }
        helper.assertTrue(done(helper, target, "most_wanted"), "3 bounties did not grant most_wanted");
        helper.succeed();
    }

    @GameTest(template = T, batch = "adv_cbc_optional")
    public static void cannonAdvancementOnlyExistsWithCbc(GameTestHelper helper) {
        boolean cbc = net.neoforged.fml.ModList.get().isLoaded("createbigcannons");
        boolean loaded = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(BloodContracts.MODID, "special_delivery")) != null;
        helper.assertTrue(loaded == cbc, "special_delivery loaded=" + loaded + " with CBC=" + cbc);
        helper.succeed();
    }
}

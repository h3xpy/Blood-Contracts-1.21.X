package dev.bloodcontracts.gametest;

import static dev.bloodcontracts.gametest.LifecycleTests.expectRefused;
import static dev.bloodcontracts.gametest.TestSupport.assertConserved;
import static dev.bloodcontracts.gametest.TestSupport.countInInventory;
import static dev.bloodcontracts.gametest.TestSupport.hold;
import static dev.bloodcontracts.gametest.TestSupport.marked;
import static dev.bloodcontracts.gametest.TestSupport.placeFromHand;

import dev.bloodcontracts.BloodContracts;
import dev.bloodcontracts.core.Bounty;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyManager;
import dev.bloodcontracts.core.BountyResult;
import dev.bloodcontracts.menu.BountyCreateMenu;
import dev.bloodcontracts.network.BCPayloads;
import dev.bloodcontracts.network.BCPayloads.BountyListPayload;
import dev.bloodcontracts.network.BCPayloads.CancelBountyRequest;
import dev.bloodcontracts.network.BCPayloads.CreateBountyRequest;
import dev.bloodcontracts.network.ServerPayloadHandler;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side payload handlers called directly, with valid and invalid data, without a client. */
@GameTestHolder(BloodContracts.MODID)
@PrefixGameTestTemplate(false)
public class NetworkTests {
    private static final String T = BloodContractsGameTests.TEMPLATE;

    /** Opens the creation menu server-side and puts the stack in its reward slot, as a client click would. */
    private static BountyCreateMenu openMenuWith(ServerPlayer player, ItemStack stack) {
        ServerPayloadHandler.handleOpenCreate(player);
        BountyCreateMenu menu = (BountyCreateMenu) player.containerMenu;
        menu.rewardContainer().setItem(0, stack);
        return menu;
    }

    @GameTest(template = T, batch = "net_create_valid")
    public static void createFromMenuSlotEscrowsTheSlotItem(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();
        ItemStack reward = marked(Items.DIAMOND, 4, marker);
        ItemStack expected = reward.copy();
        BountyCreateMenu menu = openMenuWith(sponsor, reward);

        BountyResult<Bounty> result = ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID()));

        helper.assertTrue(result.isOk(), "valid request refused: " + result.error());
        helper.assertTrue(menu.rewardContainer().getItem(0).isEmpty(), "slot not emptied");
        helper.assertTrue(ItemStack.matches(expected, result.value().rewards().get(0)), "escrow differs from the slot content");
        helper.assertTrue(sponsor.containerMenu == sponsor.inventoryMenu, "menu not closed after creation");
        helper.assertTrue(countInInventory(sponsor, marker) == 0, "closing the menu gave the item back after escrow");
        assertConserved(helper, manager, marker, 4);
        helper.succeed();
    }

    @GameTest(template = T, batch = "net_create_invalid")
    public static void invalidCreateRequestsChangeNothing(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        UUID marker = UUID.randomUUID();

        // No menu open: refused even though the player holds an item.
        hold(sponsor, marked(Items.DIAMOND, 2, marker));
        expectRefused(helper, ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID())), BountyError.NO_MENU);
        helper.assertTrue(countInInventory(sponsor, marker) == 2, "item taken without a menu");

        // Menu open with an empty slot.
        ServerPayloadHandler.handleOpenCreate(sponsor);
        expectRefused(helper, ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(target.getUUID())), BountyError.EMPTY_SLOT);

        // Menu open with an item, but target = self, then an unknown target.
        sponsor.closeContainer();
        UUID marker2 = UUID.randomUUID();
        BountyCreateMenu menu = openMenuWith(sponsor, marked(Items.EMERALD, 3, marker2));
        expectRefused(helper, ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(sponsor.getUUID())), BountyError.SELF_TARGET);
        expectRefused(helper, ServerPayloadHandler.handleCreate(sponsor, new CreateBountyRequest(UUID.randomUUID())), BountyError.UNKNOWN_TARGET);
        helper.assertTrue(menu.rewardContainer().getItem(0).getCount() == 3, "a refused request touched the slot");
        helper.assertTrue(manager.activeBounties().isEmpty(), "a refused request created a bounty");
        assertConserved(helper, manager, marker, 2);
        assertConserved(helper, manager, marker2, 3);

        // Closing without confirming gives the item back.
        sponsor.closeContainer();
        helper.assertTrue(countInInventory(sponsor, marker2) == 3, "item not returned when the menu closed");
        assertConserved(helper, manager, marker2, 3);
        helper.succeed();
    }

    @GameTest(template = T, batch = "net_cancel")
    public static void invalidCancelRequestsChangeNothing(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer other = TestPlayers.hunter(helper);
        UUID marker = UUID.randomUUID();
        hold(sponsor, marked(Items.DIAMOND, 2, marker));
        Bounty b = placeFromHand(manager, sponsor, target).value();

        expectRefused(helper, ServerPayloadHandler.handleCancel(sponsor, new CancelBountyRequest(UUID.randomUUID())), BountyError.NOT_FOUND);
        expectRefused(helper, ServerPayloadHandler.handleCancel(other, new CancelBountyRequest(b.id())), BountyError.NOT_OWNER);
        expectRefused(helper, ServerPayloadHandler.handleCancel(target, new CancelBountyRequest(b.id())), BountyError.NOT_OWNER);
        helper.assertTrue(manager.findActive(b.id()).isPresent(), "invalid cancel changed state");
        assertConserved(helper, manager, marker, 2);

        helper.assertTrue(ServerPayloadHandler.handleCancel(sponsor, new CancelBountyRequest(b.id())).isOk(), "valid cancel refused");
        expectRefused(helper, ServerPayloadHandler.handleCancel(sponsor, new CancelBountyRequest(b.id())), BountyError.NOT_ACTIVE);
        helper.assertTrue(ServerPayloadHandler.handleClaim(sponsor, new BCPayloads.ClaimRewardRequest(java.util.Optional.empty())).isOk(), "claim refused");
        helper.assertTrue(countInInventory(sponsor, marker) == 2, "refund wrong");
        assertConserved(helper, manager, marker, 2);
        helper.succeed();
    }

    @GameTest(template = T, batch = "net_list_codec")
    public static void listPayloadRoundTripsAndHidesSponsor(GameTestHelper helper) {
        BountyManager manager = TestSupport.reset(helper);
        ServerPlayer sponsor = TestPlayers.sponsor(helper);
        ServerPlayer target = TestPlayers.target(helper);
        ServerPlayer viewer = TestPlayers.hunter(helper);
        hold(sponsor, new ItemStack(Items.DIAMOND, 5));
        placeFromHand(manager, sponsor, target);

        BountyListPayload forViewer = ServerPayloadHandler.buildList(viewer, false, null, false);
        helper.assertTrue(forViewer.active().size() == 1, "list should contain the bounty");
        helper.assertTrue(forViewer.active().get(0).sponsorName().isEmpty(), "sponsor leaked with showSponsor=false");
        helper.assertTrue(!forViewer.active().get(0).mine(), "viewer is not the sponsor");
        BountyListPayload forSponsor = ServerPayloadHandler.buildList(sponsor, false, null, false);
        helper.assertTrue(forSponsor.active().get(0).mine() && forSponsor.active().get(0).sponsorName().isPresent(), "sponsor view wrong");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        BountyListPayload.STREAM_CODEC.encode(buf, forViewer);
        BountyListPayload decoded = BountyListPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.active().size() == 1, "decode lost entries");
        var v = decoded.active().get(0);
        helper.assertTrue(v.icon().is(Items.DIAMOND) && v.icon().getCount() == 5 && v.targetName().equals(target.getGameProfile().getName()),
                "decoded view differs");

        // A request payload round-trips too and carries nothing but the UUID.
        RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        UUID id = UUID.randomUUID();
        BCPayloads.CreateBountyRequest.STREAM_CODEC.encode(buf2, new BCPayloads.CreateBountyRequest(id));
        helper.assertTrue(buf2.readableBytes() == 16, "create request should only carry a UUID");
        helper.assertTrue(BCPayloads.CreateBountyRequest.STREAM_CODEC.decode(buf2).target().equals(id), "UUID corrupted");
        helper.succeed();
    }
}

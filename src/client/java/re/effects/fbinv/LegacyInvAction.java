package re.effects.fbinv;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

final class LegacyInvAction implements Action {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("fb_inventory_view", "inv_v1_optional");

    @Override
    public Identifier name() {
        return ID;
    }

    @Override
    public void handle(ReplayServer server, RegistryFriendlyByteBuf buf) {
        ((InvAccess) server).fbinv$read(buf, true);
    }
}

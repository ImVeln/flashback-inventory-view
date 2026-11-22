package re.effects.fbinv;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

final class ClearInvAction implements Action {
    static final ClearInvAction INSTANCE = new ClearInvAction();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("fb_inventory_view", "clear_inv_optional");

    private ClearInvAction() {
    }

    @Override
    public Identifier name() {
        return ID;
    }

    @Override
    public void handle(ReplayServer srv, RegistryFriendlyByteBuf buf) {
        ((InvAccess) srv).fbinv$clear();
    }
}

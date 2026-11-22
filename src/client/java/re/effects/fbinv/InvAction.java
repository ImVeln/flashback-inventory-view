package re.effects.fbinv;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class InvAction implements Action {
    public static final InvAction INSTANCE = new InvAction();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("fb_inventory_view", "inv_v2_optional");

    private InvAction() {
    }

    @Override
    public Identifier name() {
        return ID;
    }

    @Override
    public void handle(ReplayServer server, RegistryFriendlyByteBuf buf) {
        ((InvAccess) server).fbinv$read(buf, false);
    }

    public void write(ReplayWriter writer, Inv inv, Inv prev) {
        if (inv == null) {
            writer.startAndFinishAction(ClearInvAction.INSTANCE);
            return;
        }
        writer.startAction(this);
        inv.write(writer.friendlyByteBuf(), prev);
        writer.finishAction(this);
    }
}

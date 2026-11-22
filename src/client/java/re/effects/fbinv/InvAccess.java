package re.effects.fbinv;

import net.minecraft.network.RegistryFriendlyByteBuf;

public interface InvAccess {
    void fbinv$read(RegistryFriendlyByteBuf buf, boolean legacy);

    void fbinv$clear();

    Inv fbinv$view();
}

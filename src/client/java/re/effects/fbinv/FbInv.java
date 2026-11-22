package re.effects.fbinv;

import com.moulberry.flashback.action.ActionRegistry;
import net.fabricmc.api.ClientModInitializer;

public final class FbInv implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ActionRegistry.register(InvAction.INSTANCE);
        ActionRegistry.register(new LegacyInvAction());
        ActionRegistry.register(ClearInvAction.INSTANCE);
    }
}

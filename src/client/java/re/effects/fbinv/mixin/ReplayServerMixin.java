package re.effects.fbinv.mixin;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.effects.fbinv.Inv;
import re.effects.fbinv.InvAccess;

@Mixin(ReplayServer.class)
abstract class ReplayServerMixin implements InvAccess {
    @Shadow(remap = false) public volatile boolean fastForwarding;
    @Unique private Inv fbinv$inv;
    @Unique private volatile Inv fbinv$view;

    @Override
    public void fbinv$read(RegistryFriendlyByteBuf buf, boolean legacy) {
        fbinv$inv = Inv.read(buf, fbinv$inv, legacy);
    }

    @Override
    public void fbinv$clear() {
        fbinv$inv = null;
    }

    @Override
    public Inv fbinv$view() {
        return fbinv$view;
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void fbinv$publishTick(CallbackInfo ci) {
        fbinv$view = fbinv$inv;
    }

    @Inject(method = "handleActions", at = @At("RETURN"), remap = false)
    private void fbinv$publishActions(CallbackInfo ci) {
        if (!fastForwarding) {
            fbinv$view = fbinv$inv;
        }
    }
}

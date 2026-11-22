package re.effects.fbinv.mixin;

import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.playback.ReplayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.effects.fbinv.InvAccess;

@Mixin(value = ReplayReader.class, remap = false)
abstract class ReplayReaderMixin {
    @Inject(method = "handleSnapshot", at = @At("HEAD"))
    private void fbinv$resetInv(ReplayServer srv, CallbackInfo ci) {
        ((InvAccess) srv).fbinv$clear();
    }
}

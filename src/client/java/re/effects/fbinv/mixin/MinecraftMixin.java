package re.effects.fbinv.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import re.effects.fbinv.Inv;
import re.effects.fbinv.InvAccess;
import re.effects.fbinv.InvScreen;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @WrapOperation(
        method = "handleKeybinds",
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyInventory:Lnet/minecraft/client/KeyMapping;"),
            to = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendOpenInventory()V")
        ),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z")
    )
    private boolean fbinv$open(KeyMapping key, Operation<Boolean> original) {
        if (!original.call(key)) {
            return false;
        }
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.screen != null || mc.player == null || Flashback.isExporting()) {
            return true;
        }

        ReplayServer srv = Flashback.getReplayServer();
        AbstractClientPlayer cam = Flashback.getSpectatingPlayer();
        if (srv == null || cam == null || cam.getId() != srv.getLocalPlayerId()) {
            return true;
        }
        if (!srv.doClientRendering()) {
            return false;
        }

        Inv inv = ((InvAccess) srv).fbinv$view();
        if (inv != null && inv.belongsTo(cam.getUUID())) {
            mc.setScreen(InvScreen.create(mc, srv, cam, inv));
        } else {
            InvScreen.notifyMissing(mc);
        }
        return false;
    }
}

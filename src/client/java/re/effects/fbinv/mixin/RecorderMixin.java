package re.effects.fbinv.mixin;

import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.record.Recorder;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.effects.fbinv.Inv;
import re.effects.fbinv.InvAction;

import java.util.ArrayList;
import java.util.function.Consumer;

@Mixin(value = Recorder.class, remap = false)
abstract class RecorderMixin {
    @Shadow @Final private AsyncReplaySaver asyncReplaySaver;
    @Shadow private StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec;
    @Unique private Inv fbinv$prev;
    @Unique private boolean fbinv$gap;

    @Inject(method = "writeLocalData", at = @At("RETURN"))
    private void fbinv$writeTick(CallbackInfo ci) {
        Inv prev = fbinv$prev;
        Inv inv = fbinv$write(false);
        if (inv == null || inv == prev) {
            return;
        }
        var pkts = new ArrayList<Packet<? super ClientGamePacketListener>>();
        inv.writeHotbar(prev, pkts::add);
        if (!pkts.isEmpty()) {
            asyncReplaySaver.writeGamePackets(gamePacketCodec, pkts);
        }
    }

    @Inject(method = "writeCustomSnapshot", at = @At("HEAD"))
    private void fbinv$writeSnapshot(Consumer<Packet<? super ClientGamePacketListener>> send, CallbackInfo ci) {
        Inv inv = fbinv$write(true);
        if (inv != null) {
            inv.writeHotbar(null, send);
        }
    }

    @Inject(
        method = "endTick",
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/io/AsyncReplaySaver;writeReplayChunk(Ljava/lang/String;Ljava/lang/String;)V"),
            to = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/record/Recorder;writeSnapshot(Z)V", ordinal = 1)
        ),
        at = @At(
            value = "INVOKE",
            target = "Lcom/moulberry/flashback/io/AsyncReplaySaver;submit(Ljava/util/function/Consumer;)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void fbinv$writeResumeSnapshot(CallbackInfo ci) {
        fbinv$write(true);
    }

    @Unique
    private Inv fbinv$write(boolean full) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            if (full || !fbinv$gap) {
                asyncReplaySaver.submit(writer -> InvAction.INSTANCE.write(writer, null, null));
            }
            fbinv$prev = null;
            if (!full) {
                fbinv$gap = true;
            }
            return null;
        }

        fbinv$gap = false;
        Inv prev = full ? null : fbinv$prev;
        Inv inv = Inv.capture(mc, player, prev);
        if (inv != prev) {
            fbinv$prev = inv;
            asyncReplaySaver.submit(writer -> InvAction.INSTANCE.write(writer, inv, prev));
        }
        return inv;
    }
}

package re.effects.fbinv.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.effects.fbinv.ExportInv;
import re.effects.fbinv.InvScreen;

@Mixin(value = ExportJob.class, remap = false)
abstract class ExportJobMixin {
    @Shadow @Final private ExportSettings settings;
    @Unique private InvScreen fbinv$opened;
    @Unique private Screen fbinv$frame;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fbinv$capture(ExportSettings settings, CallbackInfo ci) {
        fbinv$opened = ExportInv.take(settings);
    }

    @WrapMethod(method = "run")
    private void fbinv$run(Operation<Void> run) {
        Minecraft mc = Minecraft.getInstance();
        try {
            run.call();
        } finally {
            fbinv$detach(mc);
            if (fbinv$frame != null) {
                fbinv$frame.removed();
                fbinv$frame = null;
            }
            if (fbinv$opened != null) {
                fbinv$opened.reopen(mc);
                fbinv$opened = null;
            }
        }
    }

    @Inject(method = "doExport", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lcom/moulberry/flashback/exporting/PerfectFrames;waitUntilFrameReady()V"))
    private void fbinv$attach(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (fbinv$opened == null || settings.noGui() || mc.screen != null || mc.getOverlay() != null) {
            return;
        }
        mc.screen = fbinv$frame;
        Screen next = fbinv$opened.exportFrame(mc, fbinv$frame);
        boolean changed = next != fbinv$frame;
        if (changed) {
            if (fbinv$frame != null) {
                fbinv$frame.removed();
            }
            fbinv$frame = next;
            mc.screen = next;
        }
        if (next != null) {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            if (changed) {
                next.init(w, h);
            } else if (next.width != w || next.height != h) {
                next.resize(w, h);
            }
        }
    }

    @Inject(method = "doExport", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lcom/moulberry/flashback/exporting/ExportJob;submitDownloadedFrames(Lcom/moulberry/flashback/exporting/VideoWriter;Lcom/moulberry/flashback/exporting/SaveableFramebufferQueue;Z)V"))
    private void fbinv$detach(CallbackInfo ci) {
        fbinv$detach(Minecraft.getInstance());
    }

    @Unique
    private void fbinv$detach(Minecraft mc) {
        if (fbinv$frame != null && mc.screen == fbinv$frame) {
            mc.screen = null;
        }
    }
}

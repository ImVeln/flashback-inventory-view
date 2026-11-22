package re.effects.fbinv.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.ui.windows.StartExportWindow;
import com.moulberry.flashback.exporting.ExportSettings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.effects.fbinv.ExportInv;
import re.effects.fbinv.InvScreen;

import java.util.concurrent.CompletableFuture;

@Mixin(value = StartExportWindow.class, remap = false)
abstract class StartExportMixin {
    @Shadow private static boolean open;
    @Unique private static InvScreen fbinv$preview;

    @Inject(method = "open", at = @At("HEAD"))
    private static void fbinv$open(CallbackInfo ci) {
        fbinv$preview = InvScreen.get(Minecraft.getInstance().screen);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void fbinv$close(CallbackInfo ci) {
        if (!open) {
            fbinv$preview = null;
        }
    }

    @WrapMethod(method = "createExportSettings")
    private static CompletableFuture<ExportSettings> fbinv$request(String name, FlashbackConfigV1 cfg,
        Operation<CompletableFuture<ExportSettings>> original) {
        InvScreen current = InvScreen.get(Minecraft.getInstance().screen);
        InvScreen view = current == null ? fbinv$preview : current;
        return original.call(name, cfg).thenApply(settings -> {
            if (settings != null) {
                ExportInv.bind(settings, view);
            }
            return settings;
        });
    }
}

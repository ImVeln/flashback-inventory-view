package re.effects.fbinv.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import re.effects.fbinv.InvScreen;

import java.util.Collection;

@Mixin(EffectsInInventory.class)
abstract class EffectsMixin {
    @Shadow @Final private AbstractContainerScreen<?> screen;

    @ModifyExpressionValue(method = "render", at = @At(
        value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"
    ))
    private Collection<MobEffectInstance> fbinv$effects(Collection<MobEffectInstance> effects) {
        InvScreen view = InvScreen.get(screen);
        return view == null ? effects : view.cam().getActiveEffects();
    }
}

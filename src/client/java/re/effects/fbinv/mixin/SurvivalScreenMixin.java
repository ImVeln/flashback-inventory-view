package re.effects.fbinv.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import re.effects.fbinv.InvScreen;

@Mixin(InventoryScreen.class)
abstract class SurvivalScreenMixin {
    @ModifyExpressionValue(method = {"init", "containerTick", "renderBg"}, at = @At(
        value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;"
    ))
    private LocalPlayer fbinv$player(LocalPlayer player) {
        var view = InvScreen.get((InventoryScreen) (Object) this);
        return view == null ? player : InvScreen.displayPlayer(Minecraft.getInstance());
    }
}

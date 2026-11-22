package re.effects.fbinv.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import re.effects.fbinv.InvScreen;

@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeScreenMixin {
    @ModifyExpressionValue(method = {"init", "containerTick", "selectTab", "renderBg", "getTooltipFromContainerItem"}, at = @At(
        value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;"
    ))
    private LocalPlayer fbinv$player(LocalPlayer player) {
        var view = InvScreen.get((CreativeModeInventoryScreen) (Object) this);
        return view == null ? player : InvScreen.displayPlayer(Minecraft.getInstance());
    }

    @WrapWithCondition(method = "init", at = @At(
        value = "INVOKE", target = "Lnet/minecraft/world/inventory/InventoryMenu;addSlotListener(Lnet/minecraft/world/inventory/ContainerListener;)V"
    ))
    private boolean fbinv$listen(InventoryMenu menu, ContainerListener listener) {
        return InvScreen.get((CreativeModeInventoryScreen) (Object) this) == null;
    }
}

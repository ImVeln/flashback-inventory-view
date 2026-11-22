package re.effects.fbinv.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.effects.fbinv.BookView;
import re.effects.fbinv.InvScreen;
import re.effects.fbinv.InvUi;

import java.util.List;

@Mixin(RecipeBookComponent.class)
abstract class BookMixin implements BookView {
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;
    @Shadow @Final private RecipeBookPage recipeBookPage;
    @Shadow protected Minecraft minecraft;
    @Shadow private RecipeBookTabButton selectedTab;
    @Shadow private EditBox searchBox;
    @Shadow private String lastSearch;

    @Shadow public abstract boolean isVisible();
    @Shadow public abstract void recipesUpdated();
    @Shadow
    private void replaceSelected(RecipeBookTabButton tab) {
        throw new AssertionError();
    }

    @Override
    public int fbinv$tab() {
        return Math.max(0, tabButtons.indexOf(selectedTab));
    }

    @Override
    public int fbinv$page() {
        return ((PageAccess) recipeBookPage).fbinv$page();
    }

    @Override
    public String fbinv$search() {
        return searchBox == null ? "" : searchBox.getValue();
    }

    @Override
    public void fbinv$restore(InvUi ui) {
        if (!isVisible() || searchBox == null) {
            return;
        }
        int tab = ui.bookTab();
        if (tab >= 0 && tab < tabButtons.size() && selectedTab != tabButtons.get(tab)) {
            replaceSelected(tabButtons.get(tab));
        }
        searchBox.setValue(ui.search());
        lastSearch = ui.search();
        recipesUpdated();
        PageAccess page = (PageAccess) recipeBookPage;
        page.fbinv$page(Math.clamp(ui.page(), 0, Math.max(0, page.fbinv$total() - 1)));
        page.fbinv$refresh();
    }

    @ModifyExpressionValue(method = {"init", "initVisuals", "tick", "updateStackedContents", "keyPressed"}, at = @At(
        value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;"
    ))
    private LocalPlayer fbinv$player(LocalPlayer player) {
        return InvScreen.displayPlayer(minecraft);
    }

    @ModifyExpressionValue(method = "updateCollections", at = @At(
        value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;searchTrees()Lnet/minecraft/client/multiplayer/SessionSearchTrees;"
    ))
    private SessionSearchTrees fbinv$searchTrees(SessionSearchTrees original) {
        InvScreen view = InvScreen.get(minecraft.screen);
        return view == null ? original : view.searchTrees();
    }

    @Inject(method = "sendUpdateSettings", at = @At("HEAD"), cancellable = true)
    private void fbinv$localOnly(CallbackInfo ci) {
        if (InvScreen.get(minecraft.screen) != null) {
            ci.cancel();
        }
    }
}

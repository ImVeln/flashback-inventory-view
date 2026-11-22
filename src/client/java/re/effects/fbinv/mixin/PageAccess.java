package re.effects.fbinv.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookPage.class)
public interface PageAccess {
    @Accessor("currentPage")
    int fbinv$page();

    @Accessor("currentPage")
    void fbinv$page(int page);

    @Accessor("totalPages")
    int fbinv$total();

    @Invoker("updateButtonsForPage")
    void fbinv$refresh();
}

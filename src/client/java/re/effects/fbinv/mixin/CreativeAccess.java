package re.effects.fbinv.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeAccess {
    @Accessor("selectedTab")
    static CreativeModeTab fbinv$tab() {
        throw new AssertionError();
    }

    @Accessor("selectedTab")
    static void fbinv$tab(CreativeModeTab tab) {
        throw new AssertionError();
    }

    @Accessor("scrollOffs")
    float fbinv$scroll();

    @Accessor("scrollOffs")
    void fbinv$scroll(float scroll);

    @Accessor("searchBox")
    EditBox fbinv$searchBox();

    @Invoker("selectTab")
    void fbinv$select(CreativeModeTab tab);

    @Invoker("refreshSearchResults")
    void fbinv$search();
}

package re.effects.fbinv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import re.effects.fbinv.mixin.BookAccess;
import re.effects.fbinv.mixin.CreativeAccess;
import re.effects.fbinv.mixin.SurvivalAccess;

import java.util.List;

public record InvUi(boolean creative, boolean opTab, Identifier tab, float scroll,
                    boolean book, boolean craftable, int bookTab, int page, String search,
                    List<RecipeDisplayEntry> recipes) {
    private static final Identifier PERSONAL = Identifier.withDefaultNamespace("inventory");

    static InvUi capture(Minecraft mc, LocalPlayer player, InvUi prev) {
        if (player.hasInfiniteMaterials()) {
            boolean opTab = mc.options.operatorItemsTab().get() && player.canUseGameMasterBlocks();
            if (mc.screen instanceof CreativeModeInventoryScreen screen) {
                CreativeAccess access = (CreativeAccess) screen;
                Identifier tab = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(CreativeAccess.fbinv$tab());
                return new InvUi(true, opTab, tab == null ? PERSONAL : tab, access.fbinv$scroll(),
                    false, false, 0, 0, access.fbinv$searchBox().getValue(), List.of());
            }
            return fallback(true, opTab);
        }

        var book = player.getRecipeBook();
        boolean open = book.isOpen(RecipeBookType.CRAFTING);
        boolean craftable = book.isFiltering(RecipeBookType.CRAFTING);
        List<RecipeDisplayEntry> recipes = List.of();
        int tab = 0;
        int page = 0;
        String search = "";
        if (open) {
            var known = ((BookAccess) book).fbinv$known();
            recipes = prev != null && prev.recipes.size() == known.size()
                && prev.recipes.stream().allMatch(recipe -> recipe.equals(known.get(recipe.id())))
                ? prev.recipes : List.copyOf(known.values());
            if (mc.screen instanceof InventoryScreen screen) {
                var access = ((SurvivalAccess) screen).fbinv$book();
                BookView view = (BookView) access;
                tab = view.fbinv$tab();
                page = view.fbinv$page();
                search = view.fbinv$search();
            }
        }
        return new InvUi(false, false, PERSONAL, 0, open, craftable, tab, page, search, recipes);
    }

    public static InvUi fallback(boolean creative, boolean opTab) {
        return new InvUi(creative, opTab, PERSONAL, 0, false, false, 0, 0, "", List.of());
    }

    void write(RegistryFriendlyByteBuf buf, InvUi prev) {
        buf.writeBoolean(creative);
        if (creative) {
            buf.writeBoolean(opTab);
            buf.writeIdentifier(tab);
            buf.writeFloat(scroll);
            buf.writeUtf(search, 50);
            return;
        }
        buf.writeBoolean(book);
        buf.writeBoolean(craftable);
        if (!book) {
            return;
        }
        buf.writeVarInt(bookTab);
        buf.writeVarInt(page);
        buf.writeUtf(search, 50);
        boolean changed = prev == null || prev.recipes != recipes;
        buf.writeBoolean(changed);
        if (changed) {
            buf.writeVarInt(recipes.size());
            for (RecipeDisplayEntry recipe : recipes) {
                RecipeDisplayEntry.STREAM_CODEC.encode(buf, recipe);
            }
        }
    }

    static InvUi read(RegistryFriendlyByteBuf buf, InvUi prev) {
        if (buf.readBoolean()) {
            boolean op = buf.readBoolean();
            Identifier tab = buf.readIdentifier();
            float scroll = buf.readFloat();
            String search = buf.readUtf(50);
            if (!Float.isFinite(scroll) || scroll < 0 || scroll > 1) {
                throw new IllegalArgumentException("Invalid creative inventory scroll");
            }
            return new InvUi(true, op, tab, scroll, false, false, 0, 0, search, List.of());
        }
        boolean open = buf.readBoolean();
        boolean craftable = buf.readBoolean();
        int tab = 0;
        int page = 0;
        String search = "";
        List<RecipeDisplayEntry> recipes = List.of();
        if (open) {
            tab = buf.readVarInt();
            page = buf.readVarInt();
            search = buf.readUtf(50);
            if (buf.readBoolean()) {
                int count = buf.readVarInt();
                if (count < 0 || count > buf.readableBytes()) {
                    throw new IllegalArgumentException("Invalid inventory recipe count");
                }
                var list = new java.util.ArrayList<RecipeDisplayEntry>(count);
                for (int i = 0; i < count; i++) {
                    list.add(RecipeDisplayEntry.STREAM_CODEC.decode(buf));
                }
                recipes = List.copyOf(list);
            } else if (prev != null) {
                recipes = prev.recipes;
            }
        }
        return new InvUi(false, false, PERSONAL, 0, open, craftable, tab, page, search, recipes);
    }
}

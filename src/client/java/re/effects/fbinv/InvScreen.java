package re.effects.fbinv;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.GameType;
import re.effects.fbinv.mixin.CreativeAccess;
import re.effects.fbinv.mixin.SurvivalAccess;

import java.util.List;

public final class InvScreen {
    private static final SystemToast.SystemToastId MISSING = new SystemToast.SystemToastId();
    private static final long CAMERA_GRACE_NS = 750_000_000L;

    private final ReplayServer srv;
    private AbstractClientPlayer cam;
    private final LocalPlayer player;
    private final SessionSearchTrees searchTrees = new SessionSearchTrees();
    private final boolean creative;
    private Inv shown;
    private InvUi ui;
    private List<RecipeDisplayEntry> recipes = List.of();
    private long missingCamSince;

    private InvScreen(Minecraft mc, ReplayServer srv, AbstractClientPlayer cam, Inv inv) {
        this.srv = srv;
        this.cam = cam;
        this.shown = inv;
        this.ui = state(inv, cam);
        this.creative = ui.creative();
        this.player = new LocalPlayer(mc, mc.level, mc.getConnection(), new StatsCounter(), new ClientRecipeBook(),
            Input.EMPTY, false) {
            @Override
            public GameType gameMode() {
                return creative ? GameType.CREATIVE : GameType.SURVIVAL;
            }

            @Override
            public boolean canUseGameMasterBlocks() {
                return ui.opTab();
            }

            @Override
            public PlayerSkin getSkin() {
                return InvScreen.this.cam.getSkin();
            }
        };
        player.getAbilities().instabuild = creative;
        setBook();
    }

    public static Screen create(Minecraft mc, ReplayServer srv, AbstractClientPlayer cam, Inv inv) {
        InvScreen view = new InvScreen(mc, srv, cam, inv);
        Screen screen = view.creative ? new Creative(view) : new Survival(view);
        inv.apply(view.player, view.creative);
        return screen;
    }

    public static void notifyMissing(Minecraft mc) {
        SystemToast.addOrUpdate(mc.getToastManager(), MISSING,
            Component.translatable("fbinv.title"), Component.translatable("fbinv.missing"));
    }

    private static InvUi state(Inv inv, AbstractClientPlayer cam) {
        return inv.ui() == null ? InvUi.fallback(cam.gameMode() == GameType.CREATIVE, false) : inv.ui();
    }

    public static InvScreen get(Screen screen) {
        if (screen instanceof Survival survival) {
            return survival.view;
        }
        return screen instanceof Creative creative ? creative.view : null;
    }

    public static LocalPlayer displayPlayer(Minecraft mc) {
        InvScreen view = get(mc.screen);
        return view == null ? mc.player : view.player;
    }

    public AbstractClientPlayer cam() {
        return cam;
    }

    public SessionSearchTrees searchTrees() {
        return searchTrees;
    }

    private void setBook() {
        if (creative) {
            return;
        }
        ClientRecipeBook book = player.getRecipeBook();
        book.setOpen(RecipeBookType.CRAFTING, ui.book());
        book.setFiltering(RecipeBookType.CRAFTING, ui.craftable());
        if (recipes != ui.recipes()) {
            recipes = ui.recipes();
            book.clear();
            recipes.forEach(book::add);
            book.rebuildCollections();
            searchTrees.updateRecipes(book, player.level());
        }
    }

    private boolean tick(Minecraft mc) {
        if (Flashback.getReplayServer() != srv) {
            mc.setScreen(null);
            return false;
        }
        if (Flashback.isExporting() || !srv.doClientRendering()) {
            return false;
        }
        AbstractClientPlayer nextCam = Flashback.getSpectatingPlayer();
        if (nextCam == null) {
            long now = System.nanoTime();
            if (missingCamSince == 0) {
                missingCamSince = now;
            }
            if (now - missingCamSince < CAMERA_GRACE_NS) {
                return false;
            }
        }
        Inv inv = ((InvAccess) srv).fbinv$view();
        if (nextCam == null || !cam.getUUID().equals(nextCam.getUUID()) || inv == null || !inv.belongsTo(nextCam.getUUID())) {
            mc.setScreen(null);
            if (inv == null && nextCam != null && cam.getUUID().equals(nextCam.getUUID())) {
                notifyMissing(mc);
            }
            return false;
        }
        missingCamSince = 0;
        Screen next = sync(mc, mc.screen, inv, nextCam);
        if (next != mc.screen) {
            mc.setScreen(next);
            return false;
        }
        return true;
    }

    public Screen exportFrame(Minecraft mc, Screen cached) {
        if (Flashback.getReplayServer() != srv || !srv.doClientRendering() || mc.level == null) {
            return null;
        }
        if (!(mc.level.getEntity(srv.getLocalPlayerId()) instanceof AbstractClientPlayer owner)
            || !cam.getUUID().equals(owner.getUUID())) {
            return null;
        }
        Inv inv = ((InvAccess) srv).fbinv$view();
        if (inv == null || !inv.belongsTo(owner.getUUID())) {
            return null;
        }
        InvScreen view = get(cached);
        return view == null ? create(mc, srv, owner, inv) : view.sync(mc, cached, inv, owner);
    }

    public void reopen(Minecraft mc) {
        AbstractClientPlayer nextCam = Flashback.getSpectatingPlayer();
        if (mc.screen == null && nextCam != null && cam.getUUID().equals(nextCam.getUUID())) {
            Screen screen = exportFrame(mc, null);
            if (screen != null) {
                mc.setScreen(screen);
            }
        }
    }

    private Screen sync(Minecraft mc, Screen screen, Inv inv, AbstractClientPlayer nextCam) {
        cam = nextCam;
        InvUi next = state(inv, nextCam);
        if (shown == inv && next.equals(ui) && player.level() == mc.level) {
            return screen;
        }
        if (creative != next.creative() || ui.opTab() != next.opTab() || player.level() != mc.level) {
            return create(mc, srv, nextCam, inv);
        }
        InvUi prev = ui;
        ui = next;
        shown = inv;
        inv.apply(player, creative);
        setBook();
        if (!next.equals(prev)) {
            if (screen instanceof Survival survival) {
                survival.refresh(prev);
            } else if (screen instanceof Creative creativeScreen) {
                creativeScreen.restore();
            }
        }
        return screen;
    }

    private static boolean closeKey(Minecraft mc, KeyEvent evt) {
        if (evt.isEscape() || mc.options.keyInventory.matches(evt)) {
            mc.setScreen(null);
            return true;
        }
        return false;
    }

    private static final class Survival extends InventoryScreen {
        private final InvScreen view;

        private Survival(InvScreen view) {
            super(view.player);
            this.view = view;
        }

        @Override
        protected void init() {
            super.init();
            restore();
        }

        private void refresh(InvUi prev) {
            if (prev.book() != view.ui.book() || prev.craftable() != view.ui.craftable()) {
                rebuildWidgets();
            } else {
                restore();
            }
        }

        private void restore() {
            if (view.ui.book()) {
                BookView book = (BookView) ((SurvivalAccess) (InventoryScreen) this).fbinv$book();
                book.fbinv$restore(view.ui);
            }
        }

        @Override
        public void containerTick() {
            if (view.tick(minecraft)) {
                super.containerTick();
            }
        }

        @Override
        public void onClose() {
            minecraft.setScreen(null);
        }

        @Override
        public void removed() {
        }

        @Override
        public boolean keyPressed(KeyEvent evt) {
            if (evt.isEscape() && ((SurvivalAccess) (InventoryScreen) this).fbinv$book().keyPressed(evt)) {
                return true;
            }
            return closeKey(minecraft, evt);
        }

        @Override
        public boolean charTyped(CharacterEvent evt) {
            return false;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent evt, boolean dbl) {
            SurvivalAccess access = (SurvivalAccess) (InventoryScreen) this;
            if (!access.fbinv$narrow() || !access.fbinv$book().isVisible()) {
                for (var child : children()) {
                    if (child instanceof ImageButton button && button.mouseClicked(evt, dbl)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent evt) {
            return false;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent evt, double dx, double dy) {
            return false;
        }

        @Override
        public boolean mouseScrolled(double x, double y, double sx, double sy) {
            return false;
        }
    }

    private static final class Creative extends CreativeModeInventoryScreen {
        private final InvScreen view;
        private CreativeModeTab prevTab;

        private Creative(InvScreen view) {
            super(view.player, view.player.connection.enabledFeatures(), view.ui.opTab());
            this.view = view;
        }

        @Override
        protected void init() {
            if (prevTab == null) {
                prevTab = CreativeAccess.fbinv$tab();
            }
            super.init();
            restore();
        }

        private void restore() {
            CreativeAccess access = (CreativeAccess) (CreativeModeInventoryScreen) this;
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(view.ui.tab());
            if (tab == null || !tab.shouldDisplay()) {
                tab = CreativeModeTabs.getDefaultTab();
            }
            access.fbinv$select(tab);
            if (tab.getType() == CreativeModeTab.Type.SEARCH) {
                access.fbinv$searchBox().setValue(view.ui.search());
                access.fbinv$search();
            }
            access.fbinv$scroll(view.ui.scroll());
            menu.scrollTo(view.ui.scroll());
        }

        @Override
        public void containerTick() {
            if (view.tick(minecraft)) {
                super.containerTick();
            }
        }

        @Override
        public void onClose() {
            minecraft.setScreen(null);
        }

        @Override
        public void removed() {
            if (prevTab != null) {
                CreativeAccess.fbinv$tab(prevTab);
            }
        }

        @Override
        public boolean keyPressed(KeyEvent evt) {
            return closeKey(minecraft, evt);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent evt, boolean dbl) {
            return false;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent evt) {
            if (evt.button() == 0) {
                for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
                    if (tab.shouldDisplay() && checkTabClicked(tab, evt.x() - leftPos, evt.y() - topPos)) {
                        ((CreativeAccess) (CreativeModeInventoryScreen) this).fbinv$select(tab);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent evt, double dx, double dy) {
            return false;
        }

        @Override
        public boolean mouseScrolled(double x, double y, double sx, double sy) {
            var slot = hoveredSlot;
            hoveredSlot = null;
            try {
                return super.mouseScrolled(x, y, sx, sy);
            } finally {
                hoveredSlot = slot;
            }
        }
    }
}

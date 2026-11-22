package re.effects.fbinv;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.ActionRegistry;
import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.playback.ReplayServer;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import re.effects.fbinv.mixin.CreativeAccess;
import re.effects.fbinv.mixin.SurvivalAccess;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipFile;

public final class ReplaySmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("fbinv-smoke");
    private static final long SETTLE = Duration.ofMillis(850).toNanos();
    private static final long TIMEOUT = Duration.ofSeconds(90).toNanos();
    private static final float SCROLL = 0.4f;

    private Phase phase = Phase.START;
    private long since = System.nanoTime();
    private int ticks;
    private int end;
    private Path archive;
    private UUID owner;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (phase == Phase.DONE) {
            return;
        }
        ticks++;
        try {
            require(System.nanoTime() - since < TIMEOUT, "Timed out in " + phase);
            step(mc);
        } catch (Exception | AssertionError ex) {
            LOG.error("Replay smoke failed in {}", phase, ex);
            phase = Phase.DONE;
            throw new AssertionError("Flashback inventory regression", ex);
        }
    }

    private void step(Minecraft mc) throws Exception {
        ReplayServer srv = Flashback.getReplayServer();
        switch (phase) {
            case START -> {
                if (mc.screen instanceof TitleScreen) {
                    openWorld(mc);
                    next(Phase.SURVIVAL);
                }
            }
            case SURVIVAL -> {
                if (mc.player != null && mc.screen == null && ticks > 40) {
                    recordSurvival(mc);
                    next(Phase.PAUSE);
                }
            }
            case PAUSE -> {
                if (ticks > 25) {
                    recordGap(mc);
                    Flashback.RECORDER.setPaused(true);
                    mc.setScreen(null);
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> server.getPlayerList().getPlayer(owner).setGameMode(GameType.CREATIVE));
                    next(Phase.CREATIVE);
                }
            }
            case CREATIVE -> {
                if (mc.player.hasInfiniteMaterials() && ticks > 15) {
                    recordCreative(mc);
                    next(Phase.SAVE);
                }
            }
            case SAVE -> {
                if (ticks > 28) {
                    Flashback.finishRecordingReplay();
                    archive = newestReplay();
                    next(Phase.OPEN);
                }
            }
            case OPEN -> {
                if (ticks > 15) {
                    checkCheckpoints(archive);
                    Flashback.openReplayWorld(archive);
                    next(Phase.FOLLOW);
                }
            }
            case FOLLOW -> {
                if (srv != null && mc.screen == null && mc.player != null && view(srv) != null && ticks > 20) {
                    require(!view(srv).ui().creative(), "Initial snapshot should be survival");
                    srv.execute(() -> {
                        for (var viewer : srv.getReplayViewers()) {
                            var cam = viewer.level().getEntity(srv.getLocalPlayerId());
                            if (cam != null) viewer.setCamera(cam);
                        }
                    });
                    next(Phase.KEY);
                }
            }
            case KEY -> {
                if (Flashback.getSpectatingPlayer() != null && settled(srv)) {
                    KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(69));
                    next(Phase.CHECK_START);
                }
            }
            case CHECK_START -> {
                if (settled(srv)) {
                    checkSurvival(mc);
                    end = srv.getTotalReplayTicks() - 1;
                    srv.goToReplayTick(end);
                    next(Phase.CHECK_CREATIVE);
                }
            }
            case CHECK_CREATIVE -> {
                if (settled(srv) && srv.getReplayTick() == end) {
                    checkCreative(mc);
                    srv.goToReplayTick(0);
                    next(Phase.CHECK_REWIND);
                }
            }
            case CHECK_REWIND -> {
                if (settled(srv) && srv.getReplayTick() == 0) {
                    checkSurvival(mc);
                    srv.execute(() -> fixture(srv, true));
                    next(Phase.CHECK_V1);
                }
            }
            case CHECK_V1 -> {
                if (settled(srv) && view(srv) != null && view(srv).ui() == null) {
                    var screen = survival(mc);
                    require(screen.getMenu().getSlot(10).getItem().is(Items.GOLD_INGOT), "v1 main slot");
                    require(screen.getMenu().getCarried().isEmpty(), "v1 cursor default");
                    require(screen.getMenu().getSlot(1).getItem().isEmpty(), "v1 craft grid default");
                    require(!((SurvivalAccess) screen).fbinv$book().isVisible(), "v1 recipe book default");
                    srv.execute(() -> fixture(srv, false));
                    next(Phase.CHECK_GAP);
                }
            }
            case CHECK_GAP -> {
                if (settled(srv)) {
                    require(view(srv) == null && mc.screen == null, "Snapshot without a base must clear the view");
                    require(mc.player.containerMenu == mc.player.inventoryMenu, "Viewer inventory must stay isolated");
                    LOG.info("REPLAY SMOKE PASSED");
                    phase = Phase.DONE;
                    mc.stop();
                }
            }
            case DONE -> { }
        }
    }

    private static void openWorld(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(4);
        var data = WorldDataConfiguration.DEFAULT;
        var cfg = new LevelSettings("Inventory smoke", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
            new GameRules(data.enabledFeatures()), data);
        mc.createWorldOpenFlows().createFreshLevel("InventorySmoke" + System.currentTimeMillis(), cfg,
            new WorldOptions(71L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
    }

    private void recordSurvival(Minecraft mc) {
        owner = mc.player.getUUID();
        mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
        mc.setScreen(new InventoryScreen(mc.player));
        var menu = mc.player.inventoryMenu;
        menu.getSlot(1).set(new ItemStack(Items.BIRCH_LOG, 2));
        menu.getSlot(0).set(new ItemStack(Items.BIRCH_PLANKS, 4));
        var apple = new ItemStack(Items.APPLE, 3);
        apple.set(DataComponents.CUSTOM_NAME, Component.literal("Replay Apple"));
        menu.setCarried(apple);
        var inv = mc.player.getInventory();
        inv.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
        inv.setItem(10, new ItemStack(Items.DIAMOND, 17));
        inv.setSelectedSlot(2);
        Flashback.getConfig().recording.recordHotbar = false;
        Flashback.getConfig().recordingControls.quicksave = true;
        Flashback.getConfig().internal.showTipOfTheDay = false;
        Flashback.startRecordingReplay();
    }

    private static void recordCreative(Minecraft mc) {
        var inv = mc.player.getInventory();
        inv.setItem(2, ItemStack.EMPTY);
        inv.setItem(6, new ItemStack(Items.GOLD_INGOT, 13));
        inv.setSelectedSlot(6);
        var screen = new CreativeModeInventoryScreen(mc.player, mc.getConnection().enabledFeatures(), false);
        mc.setScreen(screen);
        var access = (CreativeAccess) screen;
        access.fbinv$select(CreativeModeTabs.searchTab());
        access.fbinv$searchBox().setValue("stone");
        access.fbinv$search();
        access.fbinv$scroll(SCROLL);
        screen.getMenu().scrollTo(SCROLL);
        screen.getMenu().setCarried(new ItemStack(Items.EMERALD, 2));
        Flashback.RECORDER.setPaused(false);
    }

    private static void recordGap(Minecraft mc) {
        var player = mc.player;
        try {
            mc.player = null;
            Flashback.RECORDER.writeCustomSnapshot(packet -> {
                throw new AssertionError("No hotbar packets expected without a local player");
            });
        } finally {
            mc.player = player;
        }
    }

    private static Path newestReplay() throws Exception {
        try (var files = Files.list(Flashback.getReplayFolder())) {
            return files.filter(f -> f.toString().endsWith(".zip"))
                .max(Comparator.comparingLong(f -> f.toFile().lastModified())).orElseThrow();
        }
    }

    private static void checkCheckpoints(Path path) throws Exception {
        try (var zip = new ZipFile(path.toFile());
             var meta = new InputStreamReader(zip.getInputStream(zip.getEntry("metadata.json")), StandardCharsets.UTF_8)) {
            var chunks = JsonParser.parseReader(meta).getAsJsonObject().getAsJsonObject("chunks");
            require(chunks.size() >= 2, "Pause and resume should create a checkpoint");
            var names = chunks.keySet().iterator();
            checkChunk(zip, names.next(), false);
            checkChunk(zip, names.next(), true);
        }
    }

    private static void checkChunk(ZipFile zip, String name, boolean resumed) throws Exception {
        try (var entry = zip.getInputStream(zip.getEntry(name))) {
            var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(entry.readAllBytes()));
            try {
                require(buf.readInt() == Flashback.MAGIC, "Flashback chunk header");
                int len = buf.readVarInt();
                int id = -1;
                Identifier wanted = resumed ? InvAction.INSTANCE.name() : ClearInvAction.INSTANCE.name();
                for (int i = 0; i < len; i++) {
                    if (buf.readIdentifier().equals(wanted)) id = i;
                }
                require(id >= 0, "Inventory action in chunk header");
                int size = buf.readInt();
                if (resumed) {
                    require(size > 0 && buf.readVarInt() == id, "Resume snapshot should start with inventory");
                    buf.readInt();
                    buf.readUUID();
                    require(buf.readUnsignedByte() == 6, "Selected slot in resume snapshot");
                    require(Long.bitCount(buf.readVarLong()) == 47, "Resume snapshot should have all slots");
                } else {
                    buf.skipBytes(size);
                    boolean cleared = false;
                    while (buf.isReadable()) {
                        int action = buf.readVarInt();
                        int bytes = buf.readInt();
                        if (action == id && bytes == 0) {
                            cleared = true;
                            break;
                        }
                        buf.skipBytes(bytes);
                    }
                    require(cleared, "A snapshot written without a local player should clear the inventory");
                }
            } finally {
                buf.release();
            }
        }
    }

    private static InventoryScreen survival(Minecraft mc) {
        require(mc.screen instanceof InventoryScreen, "Expected vanilla survival screen");
        return (InventoryScreen) mc.screen;
    }

    private static void checkSurvival(Minecraft mc) {
        var screen = survival(mc);
        var menu = screen.getMenu();
        require(menu.getSlot(1).getItem().is(Items.BIRCH_LOG), "Survival craft input");
        require(menu.getSlot(0).getItem().is(Items.BIRCH_PLANKS), "Survival craft result");
        require(menu.getSlot(10).getItem().getCount() == 17, "Survival main slot count");
        require(menu.getCarried().getCount() == 3 && menu.getCarried().getHoverName().getString().equals("Replay Apple"),
            "Survival cursor stack and components");
        require(((SurvivalAccess) screen).fbinv$book().isVisible(), "Survival recipe book");
        var inv = Flashback.getSpectatingPlayer().getInventory();
        require(inv.getSelectedSlot() == 2 && inv.getItem(2).is(Items.IRON_INGOT), "Survival native hotbar");
    }

    private static void checkCreative(Minecraft mc) {
        require(mc.screen instanceof CreativeModeInventoryScreen, "Expected vanilla creative screen");
        var screen = (CreativeModeInventoryScreen) mc.screen;
        var access = (CreativeAccess) screen;
        require(CreativeAccess.fbinv$tab() == CreativeModeTabs.searchTab(), "Creative search tab");
        require(access.fbinv$searchBox().getValue().equals("stone"), "Creative search text");
        require(access.fbinv$scroll() == SCROLL, "Creative scroll");
        require(screen.getMenu().getCarried().is(Items.EMERALD), "Creative cursor");
        var inv = Flashback.getSpectatingPlayer().getInventory();
        require(inv.getSelectedSlot() == 6 && inv.getItem(6).getCount() == 13 && inv.getItem(2).isEmpty(),
            "Creative native hotbar");
    }

    private void fixture(ReplayServer srv, boolean legacy) {
        var writer = new ReplayWriter(srv.registryAccess());
        var action = legacy ? ActionRegistry.getAction(Identifier.fromNamespaceAndPath("fb_inventory_view", "inv_v1_optional"))
            : InvAction.INSTANCE;
        writer.startSnapshot();
        writer.startAction(action);
        var buf = writer.friendlyByteBuf();
        buf.writeUUID(owner);
        buf.writeByte(2);
        buf.writeVarLong(legacy ? (1L << 41) - 1 : 0);
        if (legacy) {
            for (int slot = 0; slot < 41; slot++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, slot == 10 ? new ItemStack(Items.GOLD_INGOT) : ItemStack.EMPTY);
            }
        } else {
            buf.writeBoolean(false);
        }
        writer.finishAction(action);
        writer.endSnapshot();
        var bytes = Unpooled.wrappedBuffer(writer.popBytes());
        try {
            new ReplayReader(bytes, srv.registryAccess()).handleSnapshot(srv);
        } finally {
            bytes.release();
            writer.friendlyByteBuf().release();
        }
    }

    private static Inv view(ReplayServer srv) {
        return ((InvAccess) srv).fbinv$view();
    }

    private void next(Phase next) {
        phase = next;
        since = System.nanoTime();
        ticks = 0;
        LOG.info("Replay smoke: {}", phase);
    }

    private boolean settled(ReplayServer srv) {
        return srv != null && srv.doClientRendering() && System.nanoTime() - since > SETTLE;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private enum Phase {
        START, SURVIVAL, PAUSE, CREATIVE, SAVE, OPEN, FOLLOW, KEY, CHECK_START,
        CHECK_CREATIVE, CHECK_REWIND, CHECK_V1, CHECK_GAP, DONE
    }
}

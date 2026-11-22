package re.effects.fbinv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class Inv {
    public static final int HOTBAR = 9;
    public static final int MAIN = 36;
    public static final int OFFHAND = 40;

    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int CRAFT = OFFHAND + 1;
    private static final int CURSOR = CRAFT + 5;
    private static final int SLOTS = CURSOR + 1;
    private static final long FULL = (1L << SLOTS) - 1;
    private static final long LEGACY = (1L << CRAFT) - 1;

    private final UUID owner;
    private final int sel;
    private final ItemStack[] items;
    private final InvUi ui;

    private Inv(UUID owner, int sel, ItemStack[] items, InvUi ui) {
        this.owner = owner;
        this.sel = sel;
        this.items = items;
        this.ui = ui;
    }

    public static Inv capture(Minecraft mc, LocalPlayer player, Inv prev) {
        UUID owner = player.getUUID();
        Inventory inv = player.getInventory();
        int sel = inv.getSelectedSlot();
        if (prev != null && !owner.equals(prev.owner)) {
            prev = null;
        }
        InvUi ui = InvUi.capture(mc, player, prev == null ? null : prev.ui);
        boolean ownMenu = mc.screen instanceof InventoryScreen || mc.screen instanceof CreativeModeInventoryScreen;
        ItemStack[] items = null;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = readSlot(player, inv, ownMenu, slot);
            if (prev != null && ItemStack.matches(stack, prev.items[slot])) {
                continue;
            }
            if (items == null) {
                items = prev == null ? new ItemStack[SLOTS] : prev.items.clone();
            }
            items[slot] = stack.copy();
        }
        if (items == null) {
            return sel == prev.sel && Objects.equals(ui, prev.ui) ? prev : new Inv(owner, sel, prev.items, ui);
        }
        return new Inv(owner, sel, items, ui);
    }

    private static ItemStack readSlot(LocalPlayer player, Inventory inv, boolean ownMenu, int slot) {
        if (slot < MAIN) {
            return inv.getItem(slot);
        }
        if (slot == OFFHAND) {
            return player.getOffhandItem();
        }
        if (slot < CRAFT) {
            return player.getItemBySlot(ARMOR[slot - MAIN]);
        }
        if (!ownMenu) {
            return ItemStack.EMPTY;
        }
        return slot == CURSOR ? player.containerMenu.getCarried() : player.inventoryMenu.getSlot(slot - CRAFT).getItem();
    }

    public boolean belongsTo(UUID uuid) {
        return owner.equals(uuid);
    }

    public InvUi ui() {
        return ui;
    }

    public void writeHotbar(Inv prev, Consumer<Packet<? super ClientGamePacketListener>> send) {
        if (prev != null && !prev.belongsTo(owner)) {
            prev = null;
        }
        if (prev == null || prev.sel != sel) {
            send.accept(new ClientboundSetHeldSlotPacket(sel));
        }
        for (int slot = 0; slot < HOTBAR; slot++) {
            if (prev == null || items[slot] != prev.items[slot]) {
                send.accept(new ClientboundContainerSetSlotPacket(0, 0, slot, items[slot].copy()));
            }
        }
    }

    void apply(LocalPlayer player, boolean creative) {
        Inventory inv = player.getInventory();
        inv.setSelectedSlot(sel);
        for (int slot = 0; slot < MAIN; slot++) {
            inv.setItem(slot, items[slot].copy());
        }
        for (int i = 0; i < ARMOR.length; i++) {
            player.setItemSlot(ARMOR[i], items[MAIN + i].copy());
        }
        player.setItemSlot(EquipmentSlot.OFFHAND, items[OFFHAND].copy());
        if (!creative) {
            for (int slot = 1; slot < 5; slot++) {
                player.inventoryMenu.getSlot(slot).set(items[CRAFT + slot].copy());
            }
            player.inventoryMenu.getSlot(0).set(items[CRAFT].copy());
        }
        player.containerMenu.setCarried(items[CURSOR].copy());
        inv.setChanged();
    }

    void write(RegistryFriendlyByteBuf buf, Inv prev) {
        buf.writeUUID(owner);
        buf.writeByte(sel);
        long mask = FULL;
        InvUi lastUi = null;
        if (prev != null && prev.belongsTo(owner)) {
            lastUi = prev.ui;
            mask = 0;
            for (int slot = 0; slot < SLOTS; slot++) {
                if (items[slot] != prev.items[slot]) {
                    mask |= 1L << slot;
                }
            }
        }
        buf.writeVarLong(mask);
        for (int slot = 0; slot < SLOTS; slot++) {
            if ((mask & (1L << slot)) != 0) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, items[slot]);
            }
        }
        boolean changed = !ui.equals(lastUi);
        buf.writeBoolean(changed);
        if (changed) {
            ui.write(buf, lastUi);
        }
    }

    public static Inv read(RegistryFriendlyByteBuf buf, Inv prev, boolean legacy) {
        UUID owner = buf.readUUID();
        int sel = buf.readUnsignedByte();
        long mask = buf.readVarLong();
        long full = legacy ? LEGACY : FULL;
        if (sel >= HOTBAR || (mask & ~full) != 0) {
            throw new IllegalArgumentException("Invalid replay inventory slot");
        }
        if (prev != null && !prev.belongsTo(owner)) {
            prev = null;
        }
        if (mask != full && prev == null) {
            buf.skipBytes(buf.readableBytes());
            return null;
        }
        ItemStack[] items = mask == full ? new ItemStack[SLOTS] : prev.items.clone();
        int count = legacy ? CRAFT : SLOTS;
        for (int slot = 0; slot < count; slot++) {
            if ((mask & (1L << slot)) != 0) {
                items[slot] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            }
        }
        if (legacy) {
            Arrays.fill(items, CRAFT, SLOTS, ItemStack.EMPTY);
            return new Inv(owner, sel, items, null);
        }
        InvUi ui = prev == null ? null : prev.ui;
        if (buf.readBoolean()) {
            ui = InvUi.read(buf, ui);
        }
        if (ui == null) {
            return null;
        }
        return new Inv(owner, sel, items, ui);
    }
}

package thunder.hack.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import thunder.hack.ThunderHack;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Parent;
import thunder.hack.utility.math.ExplosionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

public final class AutoTotem extends Module {
    public Setting<OffHand> offhand = new Setting<>("Item", OffHand.Totem);
    public Setting<Float> healthF = new Setting<>("HP", 16f, 0f, 20f);
    public Setting<Float> healthS = new Setting<>("ShieldGappleHp", 16f, 0f, 20f, v -> offhand.getValue() == OffHand.Shield);
    public Setting<Boolean> matrix = new Setting<>("Matrix", true);
    public Setting<Boolean> stopMotion = new Setting<>("stopMotion", false);
    public Setting<Boolean> resetAttackCooldown = new Setting<>("ResetAttackCooldown", false);

    public final Setting<Parent> safety = new Setting<>("Safety", new Parent(false, 0));
    public Setting<Boolean> hotbarFallBack = new Setting<>("HotbarFallback", false).withParent(safety);
    public Setting<Boolean> fallBackCalc = new Setting<>("FallBackCalc", true, v -> hotbarFallBack.getValue()).withParent(safety);
    public Setting<Boolean> onElytra = new Setting<>("OnElytra", true).withParent(safety);
    public Setting<Boolean> onFall = new Setting<>("OnFall", true).withParent(safety);
    public Setting<Boolean> onCrystal = new Setting<>("OnCrystal", true).withParent(safety);
    public Setting<Boolean> onObsidianPlace = new Setting<>("OnObsidianPlace", false).withParent(safety);
    public Setting<Boolean> onCrystalInHand = new Setting<>("OnCrystalInHand", false).withParent(safety);
    public Setting<Boolean> onMinecartTnt = new Setting<>("OnMinecartTNT", true).withParent(safety);
    public Setting<Boolean> onTnt = new Setting<>("OnTNT", true).withParent(safety);
    public Setting<Boolean> rcGap = new Setting<>("RCGap", false);
    public Setting<Boolean> crapple = new Setting<>("CrappleSpoof", true, v -> offhand.getValue() == OffHand.GApple);

    private enum OffHand {Totem, Crystal, GApple, Shield}

    private static AutoTotem instance;

    private int delay;

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT);
        instance = this;
    }

    @EventHandler
    public void onSync(EventSync e) {
        swapTo(getItemSlot());
        delay--;
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (e.getPacket() instanceof EntitySpawnS2CPacket spawn && hotbarFallBack.getValue()) {
            if (spawn.getEntityType() == EntityType.END_CRYSTAL) {
                if (mc.player.squaredDistanceTo(spawn.getX(), spawn.getY(), spawn.getZ()) < 36) {
                    if (fallBackCalc.getValue() && ExplosionUtility.getSelfExplosionDamage(new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()), AutoCrystal.selfPredictTicks.getValue()) < mc.player.getHealth() + mc.player.getAbsorptionAmount() + 4f)
                        return;
                    runInstant();
                }
            }
        }
        if (e.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (blockUpdate.getState().getBlock() == Blocks.OBSIDIAN && onObsidianPlace.getValue()) {
                if (mc.player.squaredDistanceTo(blockUpdate.getPos().toCenterPos()) < 36 && delay <= 0) {
                    runInstant();
                }
            }
        }
    }

    private void runInstant() {
        SearchInvResult hotbarResult = InventoryUtility.findItemInHotBar(Items.TOTEM_OF_UNDYING);
        SearchInvResult invResult = InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING);
        if (hotbarResult.found()) {
            hotbarResult.switchTo();
            delay = 20;
        } else if (invResult.found()) {
            int slot = invResult.slot() >= 36 ? invResult.slot() - 36 : invResult.slot();
            if (!hotbarFallBack.getValue()) swapTo(slot);
            else mc.interactionManager.pickFromInventory(slot);
            delay = 20;
        }
    }

    public void swapTo(int slot) {
        if (slot != -1 && delay <= 0) {
            if (mc.currentScreen instanceof GenericContainerScreen) return;

            if (stopMotion.getValue()) mc.player.setVelocity(0, mc.player.getVelocity().getY(), 0);

            int nearest_slot = findNearestCurrentItem();
            int prevCurrentItem = mc.player.getInventory().selectedSlot;
            if (slot >= 9) {
                if (matrix.getValue()) {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));

                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearest_slot, SlotActionType.SWAP, mc.player);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));

                    debug(slot + " " + nearest_slot);

                    sendPacket(new UpdateSelectedSlotC2SPacket(nearest_slot));
                    mc.player.getInventory().selectedSlot = nearest_slot;

                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

                    sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                    mc.player.getInventory().selectedSlot = prevCurrentItem;

                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearest_slot, SlotActionType.SWAP, mc.player);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));

                    if (resetAttackCooldown.getValue()) mc.player.resetLastAttackedTicks();
                } else {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    clickSlot(slot);
                    clickSlot(45);
                    clickSlot(slot);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                }
            } else {
                sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.getInventory().selectedSlot = slot;

                debug(slot + " select");

                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

                sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                mc.player.getInventory().selectedSlot = prevCurrentItem;
                if (resetAttackCooldown.getValue())
                    mc.player.resetLastAttackedTicks();
            }
            delay = 5;
        }
    }

    public static int findNearestCurrentItem() {
        int currentItem = mc.player.getInventory().selectedSlot;
        if (currentItem == 8) return 7;
        if (currentItem == 0) return 1;
        return currentItem - 1;
    }

    public int getItemSlot() {
        SearchInvResult gapple = InventoryUtility.findItemInInventory(Items.ENCHANTED_GOLDEN_APPLE);
        SearchInvResult crapple = InventoryUtility.findItemInInventory(Items.GOLDEN_APPLE);
        SearchInvResult shield = InventoryUtility.findItemInInventory(Items.SHIELD);

        int itemSlot = -1;
        Item item = null;

        if (offhand.getValue() == OffHand.Totem) {
            if (!mc.player.getOffHandStack().getName().toString().toLowerCase().contains("руна")
                    && !mc.player.getOffHandStack().getName().toString().toLowerCase().contains("шар")) {
                item = Items.TOTEM_OF_UNDYING;
            }
        } else if (offhand.getValue() == OffHand.Crystal) {
            item = Items.END_CRYSTAL;
        } else if (offhand.getValue() == OffHand.GApple) {
            if (this.crapple.getValue()) {
                if (mc.player.hasStatusEffect(StatusEffects.ABSORPTION)) {
                    if (crapple.found()) item = Items.GOLDEN_APPLE;
                    else if (gapple.found()) item = Items.ENCHANTED_GOLDEN_APPLE;
                } else if (gapple.found()) item = Items.ENCHANTED_GOLDEN_APPLE;
            } else {
                if (gapple.found()) item = Items.ENCHANTED_GOLDEN_APPLE;
                else if (crapple.found()) item = Items.GOLDEN_APPLE;
            }
        } else {
            if (shield.found()) {
                if (mc.player.getHealth() + mc.player.getAbsorptionAmount() <= healthS.getValue()) {
                    if (gapple.found()) item = Items.ENCHANTED_GOLDEN_APPLE;
                    else if (crapple.found()) item = Items.GOLDEN_APPLE;
                } else {
                    if (!mc.player.getItemCooldownManager().isCoolingDown(Items.SHIELD))
                        item = Items.SHIELD;
                    else {
                        if (gapple.found()) item = Items.ENCHANTED_GOLDEN_APPLE;
                        else if (crapple.found()) item = Items.GOLDEN_APPLE;
                    }
                }
            } else if (crapple.found()) item = Items.GOLDEN_APPLE;
        }

        if ((mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= healthF.getValue() && InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING).found())
            item = Items.TOTEM_OF_UNDYING;

        if (rcGap.getValue() && (mc.player.getMainHandStack().getItem() instanceof SwordItem) && mc.options.useKey.isPressed()) {
            if (crapple.found() || mc.player.getOffHandStack().getItem() == Items.GOLDEN_APPLE)
                item = Items.GOLDEN_APPLE;
            if (gapple.found() || mc.player.getOffHandStack().getItem() == Items.ENCHANTED_GOLDEN_APPLE)
                item = Items.ENCHANTED_GOLDEN_APPLE;
        }

        if (onFall.getValue() && (mc.player.getHealth() + mc.player.getAbsorptionAmount()) - (((mc.player.fallDistance - 3) / 2F) + 3.5F) < 0.5)
            item = Items.TOTEM_OF_UNDYING;

        if (onElytra.getValue() && mc.player.isFallFlying())
            item = Items.TOTEM_OF_UNDYING;

        if (onCrystalInHand.getValue()) {
            for (PlayerEntity pl : ThunderHack.asyncManager.getAsyncPlayers()) {
                if (ThunderHack.friendManager.isFriend(pl)) continue;
                if (pl == mc.player) continue;
                if (mc.player.squaredDistanceTo(pl) < 36) {
                    if (pl.getMainHandStack().getItem() == Items.OBSIDIAN
                            || pl.getMainHandStack().getItem() == Items.END_CRYSTAL
                            || pl.getOffHandStack().getItem() == Items.OBSIDIAN
                            || pl.getOffHandStack().getItem() == Items.END_CRYSTAL)
                        item = Items.TOTEM_OF_UNDYING;
                }
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || !entity.isAlive()) continue;
            if (mc.player.squaredDistanceTo(entity) > 36) continue;

            if (onCrystal.getValue()) {
                if (!(entity instanceof EndCrystalEntity)) continue;
                if ((mc.player.getHealth() + mc.player.getAbsorptionAmount()) - ExplosionUtility.getSelfExplosionDamage(entity.getPos(), AutoCrystal.selfPredictTicks.getValue()) < 0.5) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }

            if (onTnt.getValue()) {
                if (!(entity instanceof TntEntity)) continue;
                if ((mc.player.getHealth() + mc.player.getAbsorptionAmount()) - ExplosionUtility.getSelfExplosionDamage(entity.getPos(), AutoCrystal.selfPredictTicks.getValue()) < 0.5) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }

            if (onMinecartTnt.getValue()) {
                if (!(entity instanceof TntMinecartEntity)) continue;
                if ((mc.player.getHealth() + mc.player.getAbsorptionAmount()) - ExplosionUtility.getSelfExplosionDamage(entity.getPos(), AutoCrystal.selfPredictTicks.getValue()) < 0.5) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }
        }

        for (int i = 9; i < 45; i++) {
            if (mc.player.getOffHandStack().getItem() == item) return -1;
            if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem().equals(item)) {
                itemSlot = i >= 36 ? i - 36 : i;
                break;
            }
        }
        if (item == mc.player.getMainHandStack().getItem() && mc.options.useKey.isPressed()) return -1;
        return itemSlot;
    }

    public static AutoTotem getInstance() {
        return instance;
    }
}

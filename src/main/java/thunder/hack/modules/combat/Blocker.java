package thunder.hack.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.*;
import thunder.hack.modules.Module;
import thunder.hack.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.setting.impl.Parent;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.BlockAnimationUtility;
import thunder.hack.utility.world.HoleUtility;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static thunder.hack.modules.client.MainSettings.isRu;

public final class Blocker extends Module {
    private final Setting<Integer> actionShift = new Setting<>("Place Per Tick", 1, 1, 5);
    private final Setting<Integer> actionInterval = new Setting<>("Delay", 0, 0, 5);
    private final Setting<Boolean> crystalBreaker = new Setting<>("Destroy Crystal", false);
    private final Setting<Boolean> antiWeakness = new Setting<>("Anti Weakness", false, v -> crystalBreaker.getValue());

    private final Setting<Boolean> newBlocks = new Setting<>("1.16 Blocks", true);
    private final Setting<Boolean> allowAnchors = new Setting<>("Allow Anchors", false, (value) -> newBlocks.getValue());

    private final Setting<Boolean> rotate = new Setting<>("Rotate", false);
    private final Setting<InteractionUtility.Interact> interactMode = new Setting<>("Interact Mode", InteractionUtility.Interact.Vanilla);
    private final Setting<InteractionUtility.PlaceMode> placeMode = new Setting<>("Place Mode", InteractionUtility.PlaceMode.Normal);
    private final Setting<Boolean> swing = new Setting<>("Swing", true);

    private final Setting<Parent> logic = new Setting<>("Logic", new Parent(false, 0));
    private final Setting<Boolean> antiCev = new Setting<>("Anti Cev", true).withParent(logic);
    private final Setting<Boolean> antiCiv = new Setting<>("Anti Civ", true).withParent(logic);
    private final Setting<Boolean> expand = new Setting<>("Expand", true).withParent(logic);
    private final Setting<Boolean> antiTntAura = new Setting<>("Anti TNT", false).withParent(logic);
    private final Setting<Boolean> antiAutoAnchor = new Setting<>("Anti Anchor", false).withParent(logic);

    private final Setting<Parent> detect = new Setting<>("Detect", new Parent(false, 1)).withParent(logic);
    private final Setting<Boolean> onPacket = new Setting<>("On Break Packet", true).withParent(detect);
    private final Setting<Boolean> onAttackBlock = new Setting<>("On Attack Block", false).withParent(detect);
    private final Setting<Boolean> onBreak = new Setting<>("On Break", true).withParent(detect);

    private final Setting<Parent> renderCategory = new Setting<>("Render", new Parent(false, 0));
    private final Setting<BlockAnimationUtility.BlockRenderMode> renderMode = new Setting<>("RenderMode", BlockAnimationUtility.BlockRenderMode.All).withParent(renderCategory);
    private final Setting<BlockAnimationUtility.BlockAnimationMode> animationMode = new Setting<>("AnimationMode", BlockAnimationUtility.BlockAnimationMode.Fade).withParent(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("RenderFillColor", new ColorSetting(HudEditor.getColor(0))).withParent(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("RenderLineColor", new ColorSetting(HudEditor.getColor(0))).withParent(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("RenderLineWidth", 2, 1, 5).withParent(renderCategory);

    private final List<BlockPos> placePositions = new CopyOnWriteArrayList<>();
    public static final Timer inactivityTimer = new Timer();
    private static Blocker instance;
    private int tickCounter = 0;

    public Blocker() {
        super("Blocker", Category.COMBAT);
        instance = this;
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        sendMessage(Formatting.RED + (isRu() ?
                "ВНИМАНИЕ!!! " + Formatting.RESET + "Использование блокера на серверах осуждается игроками, а в некоторых странах карается набутыливанием!" :
                "WARNING!!! " + Formatting.RESET + "The use of blocker on servers is condemned by players, and in some countries is punishable by jail!"
        ));
    }

    @EventHandler
    public void onPostSync(EventPostSync event) {
        if (tickCounter < actionInterval.getValue()) {
            tickCounter++;
            return;
        }
        if (tickCounter >= actionInterval.getValue()) {
            tickCounter = 0;
        }

        SearchInvResult searchResult = InventoryUtility.findInHotBar(stack -> {
            Item item = stack.getItem();
            return item == Items.OBSIDIAN || item == Items.ENDER_CHEST
                    || (newBlocks.getValue() && (item == Items.CRYING_OBSIDIAN || item == Items.NETHERITE_BLOCK || (allowAnchors.getValue() && item == Items.RESPAWN_ANCHOR)));
        });

        if (!searchResult.found()) return;

        int blocksPlaced = 0;

        if (placePositions.isEmpty()) return;

        while (blocksPlaced < actionShift.getValue()) {
            BlockPos pos = placePositions.stream()
                    .filter(p -> InteractionUtility.canPlaceBlock(p, interactMode.getValue(), false))
                    .min(Comparator.comparing(p -> mc.player.getPos().distanceTo(p.toCenterPos())))
                    .orElse(null);

            if (pos != null) {
                if (crystalBreaker.getValue())
                    for (Entity entity : mc.world.getNonSpectatingEntities(EndCrystalEntity.class, new Box(pos))) {
                        int preSlot = mc.player.getInventory().selectedSlot;
                        boolean wasEffect = false;
                        if (antiWeakness.getValue() && mc.player.hasStatusEffect(StatusEffects.WEAKNESS)) {
                            SearchInvResult result = InventoryUtility.getAntiWeaknessItem();
                            result.switchTo();
                            wasEffect = true;
                        }

                        if (placeMode.getValue() == InteractionUtility.PlaceMode.Packet)
                            sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                        else
                            mc.interactionManager.attackEntity(mc.player, entity);

                        if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
                        else sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                        if (antiWeakness.getValue() && wasEffect) {
                            InventoryUtility.switchTo(preSlot);
                        }
                    }

                if (InteractionUtility.placeBlock(pos, rotate.getValue(), interactMode.getValue(), placeMode.getValue(), searchResult, true, false)) {
                    if (swing.getValue())
                        mc.player.swingHand(Hand.MAIN_HAND);

                    blocksPlaced++;
                    BlockAnimationUtility.renderBlock(pos, renderLineColor.getValue().getColorObject(), renderLineWidth.getValue(), renderFillColor.getValue().getColorObject(), animationMode.getValue(), renderMode.getValue());
                    tickCounter = 0;
                    placePositions.remove(pos);
                    inactivityTimer.reset();
                    if (!mc.player.isOnGround()) return;
                } else break;
            } else break;
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onPacketReceive(PacketEvent.@NotNull Receive event) {
        if (event.getPacket() instanceof BlockBreakingProgressS2CPacket && onPacket.getValue()) {
            BlockBreakingProgressS2CPacket packet = event.getPacket();
            doLogic(packet.getPos());
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onAttackBlock(EventAttackBlock event) {
        if (!onAttackBlock.getValue()) return;
        doLogic(event.getBlockPos());
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onBreak(EventBreakBlock event) {
        if (!onBreak.getValue()) return;
        doLogic(event.getPos());
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onPlaceBlock(@NotNull EventPlaceBlock event) {
        if (event.getBlockPos().equals(mc.player.getBlockPos().up(2))
                && event.getBlock().equals(Blocks.TNT)
                && antiTntAura.getValue()) {
            placePositions.add(event.getBlockPos());
        }
        if (event.getBlockPos().equals(mc.player.getBlockPos().up(2))
                && event.getBlock().equals(Blocks.RESPAWN_ANCHOR)
                && antiAutoAnchor.getValue()) {
            placePositions.add(event.getBlockPos());
        }
    }

    public static Blocker getInstance() {
        return instance;
    }

    private void doLogic(BlockPos pos) {
        BlockPos playerPos = BlockPos.ofFloored(mc.player.getPos());

        if (antiCev.getValue()) {
            for (BlockPos checkPos : HoleUtility.getHolePoses(mc.player.getPos()).stream()
                    .map(bp -> bp.up(2))
                    .toList()) {
                if (checkPos.equals(pos)) {
                    placePositions.add(checkPos.up());
                    return;
                }
            }
        }

        if (HoleUtility.getSurroundPoses(mc.player.getPos()).contains(pos)) {
            if (mc.world.getBlockState(pos).getBlock() == Blocks.BEDROCK || mc.world.getBlockState(pos).isReplaceable())
                return;

            placePositions.add(pos.up());

            if (expand.getValue()) {
                for (Vec3i vec : HoleUtility.VECTOR_PATTERN) {
                    BlockPos checkPos = pos.add(vec);
                    if (InteractionUtility.canPlaceBlock(checkPos, interactMode.getValue(), true)) {
                        if (mc.world.getNonSpectatingEntities(PlayerEntity.class, new Box(checkPos)).isEmpty())
                            placePositions.add(checkPos);
                    }
                }
            }

            return;
        }

        if (antiCiv.getValue()) {
            for (BlockPos checkPos : HoleUtility.getSurroundPoses(mc.player.getPos())) {
                if (checkPos.up().equals(pos)) {
                    placePositions.add(playerPos.add(checkPos.up(2)));
                    return;
                }
            }
        }
    }
}

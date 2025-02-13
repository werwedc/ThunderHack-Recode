package thunder.hack.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import thunder.hack.core.impl.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IExplosionS2CPacket;
import thunder.hack.injection.accesors.ISPacketEntityVelocity;
import thunder.hack.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Parent;
import thunder.hack.utility.player.MovementUtility;

public class Velocity extends Module {

    /*
    TY <3
    https://github.com/SkidderMC/FDPClient/blob/main/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/velocitys/vanilla/JumpVelocity.kt
     */

    public Setting<Boolean> onlyAura = new Setting<>("OnlyAura", false);
    public Setting<Boolean> autoDisable = new Setting<>("DisableOnVerify", false);
    public Setting<Boolean> cc = new Setting<>("CC", false);
    public Setting<Boolean> fishingHook = new Setting<>("FishingHook", true);
    public static Setting<Parent> antiPush = new Setting<>("AntiPush", new Parent(false, 0));
    public Setting<Boolean> blocks = new Setting<>("Blocks", true).withParent(antiPush);
    public Setting<Boolean> players = new Setting<>("Players", true).withParent(antiPush);
    public Setting<Boolean> water = new Setting<>("Water", true).withParent(antiPush);
    private final Setting<modeEn> mode = new Setting<>("Mode", modeEn.Matrix);
    public Setting<Float> vertical = new Setting<>("Vertical", 0.0f, 0.0f, 100.0f, v -> mode.getValue() == modeEn.Custom);
    private final Setting<jumpModeEn> jumpMode = new Setting<>("JumpMode", jumpModeEn.Jump, v -> mode.getValue() == modeEn.Jump);
    public Setting<Float> horizontal = new Setting<>("Horizontal", 0.0f, 0.0f, 100.0f, v -> mode.getValue() == modeEn.Custom || mode.getValue() == modeEn.Jump);
    public Setting<Float> motion = new Setting<>("Motion", .42f, 0.4f, 0.5f, v -> mode.getValue() == modeEn.Jump);
    public Setting<Boolean> fail = new Setting<>("SmartFail", true, v -> mode.getValue() == modeEn.Jump);
    public Setting<Float> failRate = new Setting<>("FailRate", 0.3f, 0.0f, 1.0f, v -> mode.getValue() == modeEn.Jump && fail.getValue());
    public Setting<Float> jumpRate = new Setting<>("FailJumpRate", 0.25f, 0.0f, 1.0f, v -> mode.getValue() == modeEn.Jump && fail.getValue());

    public Velocity() {
        super("Velocity", Module.Category.MOVEMENT);
    }

    private boolean doJump, failJump, skip, flag;
    private int grimTicks, ccCooldown;

    @EventHandler
    public void onPacketReceived(PacketEvent.Receive e) {
        if (fullNullCheck()) return;

        if (ccCooldown > 0) {
            ccCooldown--;
            return;
        }

        if (e.getPacket() instanceof GameMessageS2CPacket && autoDisable.getValue()) {
            String text = ((GameMessageS2CPacket) e.getPacket()).content().getString();
            if (text.contains("Тебя проверяют на чит АКБ, ник хелпера - ")) disable(":^)");
        }

        if (e.getPacket() instanceof EntityStatusS2CPacket pac
                && pac.getStatus() == 31
                && pac.getEntity(mc.world) instanceof FishingBobberEntity
                && fishingHook.getValue()) {
            FishingBobberEntity fishHook = (FishingBobberEntity) pac.getEntity(mc.world);
            if (fishHook.getHookedEntity() == mc.player) {
                e.setCancelled(true);
            }
        }

        if (e.getPacket() instanceof ExplosionS2CPacket explosion) {
            if (mode.getValue() == modeEn.Custom) {
                ((IExplosionS2CPacket) explosion).setMotionX(((IExplosionS2CPacket) explosion).getMotionX() * horizontal.getValue() / 100f);
                ((IExplosionS2CPacket) explosion).setMotionZ(((IExplosionS2CPacket) explosion).getMotionZ() * horizontal.getValue() / 100f);
                ((IExplosionS2CPacket) explosion).setMotionY(((IExplosionS2CPacket) explosion).getMotionY() * vertical.getValue() / 100f);
            } else if (mode.getValue() == modeEn.Cancel) {
                ((IExplosionS2CPacket) explosion).setMotionX(0);
                ((IExplosionS2CPacket) explosion).setMotionY(0);
                ((IExplosionS2CPacket) explosion).setMotionZ(0);
            }
        }

        if (mode.getValue() == modeEn.OldGrim) {
            if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket) {
                EntityVelocityUpdateS2CPacket var4 = e.getPacket();
                if (var4.getId() == mc.player.getId()) {
                    e.cancel();
                    grimTicks = 6;
                }
            }
            if (e.getPacket() instanceof CommonPingS2CPacket && grimTicks > 0) {
                e.cancel();
                grimTicks--;
            }
        }

        if (onlyAura.getValue() && ModuleManager.aura.isDisabled())
            return;

        if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket pac) {
            if (pac.getId() == mc.player.getId()) {
                if (mode.getValue() == modeEn.Matrix) {
                    if (!flag) {
                        e.setCancelled(true);
                        flag = true;
                    } else {
                        flag = false;
                        ((ISPacketEntityVelocity) pac).setMotionX(((int) ((double) pac.getVelocityX() * -0.1)));
                        ((ISPacketEntityVelocity) pac).setMotionZ(((int) ((double) pac.getVelocityZ() * -0.1)));
                    }
                } else if (mode.getValue() == modeEn.Redirect) {
                    int vX = pac.getVelocityX();
                    int vZ = pac.getVelocityZ();
                    if (vX < 0) vX *= -1;
                    if (vZ < 0) vZ *= -1;

                    double[] motion = MovementUtility.forward((vX + vZ));

                    ((ISPacketEntityVelocity) pac).setMotionX((int) (motion[0]));
                    ((ISPacketEntityVelocity) pac).setMotionY(0);
                    ((ISPacketEntityVelocity) pac).setMotionZ((int) (motion[1]));
                } else if (mode.getValue() == modeEn.Custom) {
                    ((ISPacketEntityVelocity) pac).setMotionX((int) ((float) pac.getVelocityX() * horizontal.getValue() / 100f));
                    ((ISPacketEntityVelocity) pac).setMotionY((int) ((float) pac.getVelocityY() * vertical.getValue() / 100f));
                    ((ISPacketEntityVelocity) pac).setMotionZ((int) ((float) pac.getVelocityZ() * horizontal.getValue() / 100f));
                } else if (mode.getValue() == modeEn.Sunrise) {
                    e.setCancelled(true);
                    sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), -999.0, mc.player.getZ(), true));
                } else if (mode.getValue() == modeEn.Cancel) {
                    e.setCancelled(true);
                } else if (mode.getValue() == modeEn.Jump && mc.player.isOnGround()) {
                    ((ISPacketEntityVelocity) pac).setMotionX((int) ((float) pac.getVelocityX() * horizontal.getValue() / 100f));
                    ((ISPacketEntityVelocity) pac).setMotionZ((int) ((float) pac.getVelocityZ() * horizontal.getValue() / 100f));
                }
            }
        }
        if (e.getPacket() instanceof PlayerPositionLookS2CPacket && cc.getValue()) {
            ccCooldown = 5;
        }
    }


    @EventHandler
    public void onSync(EventSync e) {
        if (mode.getValue() == modeEn.Matrix) {
            if (mc.player.hurtTime > 0 && !mc.player.isOnGround()) {
                double var3 = mc.player.getYaw() * 0.017453292F;
                double var5 = Math.sqrt(mc.player.getVelocity().x * mc.player.getVelocity().x + mc.player.getVelocity().z * mc.player.getVelocity().z);
                mc.player.setVelocity(-Math.sin(var3) * var5, mc.player.getVelocity().y, Math.cos(var3) * var5);
                mc.player.setSprinting(mc.player.age % 2 != 0);
            }
        }
        if (mode.getValue() == modeEn.Jump) {
            if ((failJump || mc.player.hurtTime > 6) && mc.player.isOnGround()) {
                if (failJump) failJump = false;
                if (!doJump) skip = true;
                if (Math.random() <= failRate.getValue() && fail.getValue()) {
                    if (Math.random() <= jumpRate.getValue()) {
                        doJump = true;
                        failJump = true;
                    } else {
                        doJump = false;
                        failJump = false;
                    }
                } else {
                    doJump = true;
                    failJump = false;
                }
                if (skip) {
                    skip = false;
                    return;
                }
                switch (jumpMode.getValue()) {
                    case Jump -> mc.player.jump();
                    case Motion ->
                            mc.player.setVelocity(mc.player.getVelocity().getX(), motion.getValue(), mc.player.getVelocity().getZ());
                    case Both -> {
                        mc.player.jump();
                        mc.player.setVelocity(mc.player.getVelocity().getX(), motion.getValue(), mc.player.getVelocity().getZ());
                    }
                }
            }
        }
        if (grimTicks > 0)
            grimTicks--;
    }

    @Override
    public void onEnable() {
        grimTicks = 0;
    }

    public enum modeEn {
        Matrix, Cancel, Sunrise, Custom, Redirect, OldGrim, Jump
    }

    public enum jumpModeEn {
        Motion, Jump, Both
    }
}

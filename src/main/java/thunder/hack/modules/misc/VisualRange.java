package thunder.hack.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import thunder.hack.ThunderHack;
import thunder.hack.events.impl.EventEntityRemoved;
import thunder.hack.events.impl.EventEntitySpawn;
import thunder.hack.modules.Module;
import thunder.hack.notification.Notification;
import thunder.hack.setting.Setting;

import java.util.ArrayList;

public class VisualRange extends Module {
    private static final ArrayList<String> entities = new ArrayList<>();
    private final Setting<Boolean> leave = new Setting<>("Leave", true);
    private final Setting<Boolean> enter = new Setting<>("Enter", true);
    private final Setting<Boolean> friends = new Setting<>("Friends", true);
    private final Setting<Boolean> soundpl = new Setting<>("Sound", true);
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Notification);

    public VisualRange() {
        super("VisualRange", Category.MISC);
    }

    @EventHandler
    public void onEntityAdded(EventEntitySpawn event) {
        if (!isValid(event.getEntity())) return;

        if (!entities.contains(event.getEntity().getName().getString()))
            entities.add(event.getEntity().getName().getString());
        else return;

        if (enter.getValue()) notify(event.getEntity(), true);
    }

    @EventHandler
    public void onEntityRemoved(EventEntityRemoved event) {
        if (!isValid(event.entity)) return;

        if (entities.contains(event.entity.getName().getString()))
            entities.remove(event.entity.getName().getString());
        else return;

        if (leave.getValue()) notify(event.entity, false);
    }

    public void notify(Entity entity, boolean enter) {
        String message = "";
        if (ThunderHack.friendManager.isFriend(entity.getName().getString()))
            message = Formatting.AQUA + entity.getName().getString();
        else message = Formatting.GRAY + entity.getName().getString();

        if (enter) message += Formatting.GREEN + " was found!";
        else message += Formatting.RED + " left!";

        if (mode.getValue() == Mode.Chat) sendMessage(message);
        if (mode.getValue() == Mode.Notification)
            ThunderHack.notificationManager.publicity("VisualRange", message, 2, Notification.Type.WARNING);

        if (soundpl.getValue()) {
            try {
                if (enter)
                    mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1f, 1f);
                else
                    mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 1f, 1f);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isValid(Entity entity) {
        if (!(entity instanceof PlayerEntity)) return false;
        return entity != mc.player && (!ThunderHack.friendManager.isFriend(entity.getName().getString()) || friends.getValue());
    }

    public enum Mode {
        Chat, Notification
    }
}

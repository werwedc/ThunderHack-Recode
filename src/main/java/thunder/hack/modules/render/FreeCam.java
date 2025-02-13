package thunder.hack.modules.render;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import thunder.hack.events.impl.EventKeyboardInput;
import thunder.hack.events.impl.EventSync;
import thunder.hack.modules.Module;
import thunder.hack.modules.movement.HoleSnap;
import thunder.hack.setting.Setting;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.PlayerEntityCopy;
import thunder.hack.utility.render.Render2DEngine;

public class FreeCam extends Module {
    private final Setting<Float> speed = new Setting<>("HSpeed", 1f, 0.0f, 3f);
    private final Setting<Float> hspeed = new Setting<>("VSpeed", 0.42f, 0.0f, 3f);

    private float fakeYaw, fakePitch, prevFakeYaw, prevFakePitch;
    private double fakeX, fakeY, fakeZ, prevFakeX, prevFakeY, prevFakeZ;

    public FreeCam() {
        super("FreeCam", Category.RENDER);
    }

    @Override
    public void onEnable() {
        mc.chunkCullingEnabled = false;

        fakePitch = mc.player.getPitch();
        fakeYaw = mc.player.getYaw();

        prevFakePitch = fakePitch;
        prevFakeYaw = fakeYaw;

        fakeX = mc.player.getX();
        fakeY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
        fakeZ = mc.player.getZ();

        prevFakeX = mc.player.getX();
        prevFakeY = mc.player.getY();
        prevFakeZ = mc.player.getZ();
    }


    @Override
    public void onDisable() {
        if (fullNullCheck()) return;
        mc.chunkCullingEnabled = true;

    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSync(EventSync e){

        prevFakeYaw = fakeYaw;
        prevFakePitch = fakePitch;

        fakeYaw = mc.player.getYaw();
        fakePitch = mc.player.getPitch();
    }


    @EventHandler
    public void onKeyboardInput(EventKeyboardInput e) {
        if (mc.player == null) return;

        double[] motion = MovementUtility.forward(speed.getValue());

        prevFakeX = fakeX;
        prevFakeY = fakeY;
        prevFakeZ = fakeZ;

        fakeX += motion[0];
        fakeZ += motion[1];

        if (mc.options.jumpKey.isPressed())
            fakeY += hspeed.getValue();

        if (mc.options.sneakKey.isPressed())
            fakeY -= hspeed.getValue();

        mc.player.input.movementForward = 0;
        mc.player.input.movementSideways = 0;
        mc.player.input.jumping = false;
        mc.player.input.sneaking = false;
    }


    public float getFakeYaw() {
        return (float) Render2DEngine.interpolate(prevFakeYaw, fakeYaw, mc.getTickDelta());
    }

    public float getFakePitch() {
        return (float) Render2DEngine.interpolate(prevFakePitch, fakePitch, mc.getTickDelta());
    }

    public double getFakeX() {
        return Render2DEngine.interpolate(prevFakeX, fakeX, mc.getTickDelta());
    }

    public double getFakeY() {
        return Render2DEngine.interpolate(prevFakeY, fakeY, mc.getTickDelta());
    }

    public double getFakeZ() {
        return Render2DEngine.interpolate(prevFakeZ, fakeZ, mc.getTickDelta());
    }
}

package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Plynnie dociaga celownik do najblizszego gracza w polu widzenia. */
public class AimAssistModule extends Module {
    private final Setting.Num range = add(new Setting.Num("Zasięg", 4.5, 2.0, 8.0, 0.5));
    private final Setting.Num fov = add(new Setting.Num("Pole widzenia", 60, 10, 180, 5));
    private final Setting.Num speed = add(new Setting.Num("Siła", 6.0, 1.0, 20.0, 0.5));
    private final Setting.Bool onlyHold = add(new Setting.Bool("Tylko przy LPM", true));
    private final Setting.Bool skipFriends = add(new Setting.Bool("Pomiń znajomych", true));
    private final Setting.Bool head = add(new Setting.Bool("Celuj w głowę", false));
    private final Setting.Bool walls = add(new Setting.Bool("Przez ściany", false));

    private long last = 0;

    public AimAssistModule() {
        super("AimAssist", Category.COMBAT);
    }

    @Override
    public void onFrame(MinecraftClient mc, float delta) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : MathHelper.clamp((now - last) / 1_000_000_000f, 0f, 0.1f);
        last = now;

        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (onlyHold.value && !mc.options.attackKey.isPressed()) return;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        Vec3d eye = mc.player.getCameraPosVec(delta);

        double bestAng = Double.MAX_VALUE;
        float bestDYaw = 0, bestDPitch = 0;
        boolean found = false;

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            if (skipFriends.value && ModuleManager.isFriend(p.getName().getString())) continue;
            if (mc.player.distanceTo(p) > range.value) continue;
            if (!walls.value && !mc.player.canSee(p)) continue;

            double h = head.value ? p.getEyeHeight(p.getPose()) : p.getHeight() * 0.65;
            Vec3d t = p.getLerpedPos(delta).add(0, h, 0);
            double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
            double hd = Math.sqrt(dx * dx + dz * dz);

            float ty = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float tp = (float) -Math.toDegrees(Math.atan2(dy, hd));
            float dYaw = MathHelper.wrapDegrees(ty - yaw);
            float dPitch = tp - pitch;
            double ang = Math.hypot(dYaw, dPitch);
            if (ang > fov.value / 2.0) continue;
            if (ang < bestAng) {
                bestAng = ang;
                bestDYaw = dYaw;
                bestDPitch = dPitch;
                found = true;
            }
        }
        if (!found) return;

        float k = 1f - (float) Math.exp(-speed.value * dt);
        mc.player.setYaw(yaw + bestDYaw * k);
        mc.player.setPitch(MathHelper.clamp(pitch + bestDPitch * k, -90f, 90f));
    }
}

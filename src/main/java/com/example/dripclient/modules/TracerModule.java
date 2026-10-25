package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Draw;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Projection;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Linie od srodka ekranu (albo od dolu) do kazdego gracza, takze przez sciany. */
public class TracerModule extends Module {
    private final Setting.Num thickness = add(new Setting.Num("Grubość linii", 0.4, 0.2, 3.0, 0.1));
    private final Setting.Num hue = add(new Setting.Num("Odcień koloru", 0.0, 0.0, 1.0, 0.05));
    private final Setting.Num saturation = add(new Setting.Num("Nasycenie", 0.0, 0.0, 1.0, 0.05));
    private final Setting.Bool fromBottom = add(new Setting.Bool("Od dołu ekranu", false));
    private final Setting.Bool friendsGreen = add(new Setting.Bool("Znajomi zielono", true));
    private final Setting.Num maxDist = add(new Setting.Num("Maks. dystans", 256, 16, 512, 16));

    public TracerModule() {
        super("Tracer", Category.RENDER);
        hasFriends = true;
    }

    @Override
    public void onHud(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        double w = ctx.getScaledWindowWidth();
        double h = ctx.getScaledWindowHeight();
        double sx = w / 2.0;
        double sy = fromBottom.value ? h : h / 2.0;

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (mc.player.distanceTo(p) > maxDist.value) continue;

            String name = p.getName().getString();
            boolean friend = ModuleManager.isFriend(name);
            Vec3d pos = p.getLerpedPos(delta).add(0, p.getHeight() / 2.0, 0);
            Projection.P sp = Projection.project(mc, ctx, pos);

            int color = (friend && friendsGreen.value)
                    ? 0xFF55FF55
                    : 0xFF000000 | MathHelper.hsvToRgb((float) hue.value, (float) saturation.value, 1f);

            Draw.line(ctx, sx, sy, sp.x, sp.y, (float) thickness.value, color);
            if (!sp.behind) {
                ctx.fill((int) sp.x - 1, (int) sp.y - 1, (int) sp.x + 1, (int) sp.y + 1, color);
            }
        }
    }
}

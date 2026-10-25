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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Powieksza hitbox innych graczy po stronie klienta i rysuje go (przez sciany). */
public class HitboxModule extends Module {
    private final Setting.Num size = add(new Setting.Num("Powiększenie", 0.5, 0.1, 2.0, 0.05));
    private final Setting.Bool show = add(new Setting.Bool("Pokaż hitboxy", true));
    private final Setting.Bool skipFriends = add(new Setting.Bool("Pomiń znajomych", true));

    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    public HitboxModule() {
        super("Hitbox", Category.COMBAT);
    }

    private boolean skip(AbstractClientPlayerEntity p, MinecraftClient mc) {
        return p == mc.player || (skipFriends.value && ModuleManager.isFriend(p.getName().getString()));
    }

    private void apply(MinecraftClient mc) {
        if (mc.world == null) return;
        double s = size.value;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            Box base = p.getDimensions(p.getPose()).getBoxAt(p.getPos());
            p.setBoundingBox(skip(p, mc) ? base : base.expand(s, s, s));
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        apply(mc);
    }

    @Override
    public void onFrame(MinecraftClient mc, float delta) {
        apply(mc);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            p.setBoundingBox(p.getDimensions(p.getPose()).getBoxAt(p.getPos()));
        }
    }

    @Override
    public void onHud(DrawContext ctx, float delta) {
        if (!show.value) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            boolean friend = ModuleManager.isFriend(p.getName().getString());
            Vec3d off = p.getLerpedPos(delta).subtract(p.getPos());
            Box b = p.getBoundingBox().offset(off);

            Vec3d[] c = new Vec3d[8];
            int i = 0;
            for (int x = 0; x < 2; x++)
                for (int y = 0; y < 2; y++)
                    for (int z = 0; z < 2; z++)
                        c[i++] = new Vec3d(x == 0 ? b.minX : b.maxX, y == 0 ? b.minY : b.maxY, z == 0 ? b.minZ : b.maxZ);

            // indeks bitowy: x*4 + y*2 + z
            Projection.P[] pr = new Projection.P[8];
            for (int k = 0; k < 8; k++) pr[k] = Projection.project(mc, ctx, c[k]);

            int color = friend ? 0xFF55FF55 : 0xFFFFFFFF;
            for (int[] e : EDGES) {
                if (pr[e[0]].behind || pr[e[1]].behind) continue;
                Draw.line(ctx, pr[e[0]].x, pr[e[0]].y, pr[e[1]].x, pr[e[1]].y, 0.6f, color);
            }
        }
    }
}

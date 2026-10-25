package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Chowa to, co zasmieca trapa: obrazy, ramki, wagoniki, tabliczki i banery.
 * Byty sa usuwane lokalnie, a tabliczki/banery zamieniane lokalnie na powietrze (zeby sie przerysowaly).
 * Dzieki temu nie widzisz ich, nie lagujesz i klikniecia leca w bloki za nimi.
 */
public class AntiTrapModule extends Module {
    private final Setting.Bool paintings = add(new Setting.Bool("Obrazy i ramki", true));
    private final Setting.Bool minecarts = add(new Setting.Bool("Wagoniki", true));
    private final Setting.Bool signs = add(new Setting.Bool("Tabliczki, banery", true));
    private final Setting.Num range = add(new Setting.Num("Zasięg", 32, 8, 96, 8));
    private final Setting.Num interval = add(new Setting.Num("Odstęp (ticki)", 2, 1, 20, 1));

    private int cooldown = 0;

    public AntiTrapModule() {
        super("AntiTrap", Category.WORLD);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        if (++cooldown < interval.value) return;
        cooldown = 0;

        Vec3d me = mc.player.getPos();
        double r2 = range.value * range.value;

        if (paintings.value || minecarts.value) {
            List<Integer> ids = new ArrayList<>();
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || e.squaredDistanceTo(me) > r2) continue;
                boolean decor = paintings.value && e instanceof AbstractDecorationEntity && !(e instanceof LeashKnotEntity);
                boolean cart = minecarts.value && e instanceof AbstractMinecartEntity;
                if (decor || cart) ids.add(e.getId());
            }
            for (int id : ids) mc.world.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }

        if (signs.value) {
            int cr = (int) Math.ceil(range.value / 16.0) + 1;
            int pcx = mc.player.getBlockPos().getX() >> 4;
            int pcz = mc.player.getBlockPos().getZ() >> 4;
            List<BlockPos> hide = new ArrayList<>();
            for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
                for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof SignBlockEntity) && !(be instanceof BannerBlockEntity)) continue;
                        if (Vec3d.ofCenter(be.getPos()).squaredDistanceTo(me) > r2) continue;
                        hide.add(be.getPos().toImmutable());
                    }
                }
            }
            // zamiana na powietrze wymusza przerysowanie, wiec tabliczka/baner znika z widoku
            for (BlockPos pos : hide) mc.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        }
    }
}

package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.PlaceUtil;
import com.example.dripclient.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Gdy ktos wyleje wode obok Ciebie, mod stawia w tym miejscu dzwignie i zabiera zrodlo wody.
 * Dzwignia moze byc w hotbarze albo w drugiej rece. Ponawia proby, gdy serwer pierwsza odrzuci.
 */
public class AutoLeverModule extends Module {
    private static final int SCAN = 6;
    private static final int TRIES = 8;

    private final Setting.Num range = add(new Setting.Num("Zasięg", 4.5, 1.0, 5.0, 0.5));
    private final Setting.Bool ignoreBucket = add(new Setting.Bool("Pomiń z wiadrem", false));
    private final Setting.Bool notify = add(new Setting.Bool("Komunikaty", true));

    private Set<BlockPos> prev = new HashSet<>();
    private BlockPos prevCenter = null;
    private final Map<BlockPos, Integer> pending = new HashMap<>();
    private int msgCooldown = 0;

    public AutoLeverModule() {
        super("AutoLever", Category.WORLD);
    }

    @Override
    public void onDisable() {
        prev = new HashSet<>();
        prevCenter = null;
        pending.clear();
    }

    private static boolean isSource(BlockState st) {
        return st.isOf(Blocks.WATER) && st.get(FluidBlock.LEVEL) == 0;
    }

    private void say(MinecraftClient mc, String msg) {
        if (notify.value && mc.player != null && msgCooldown == 0) {
            mc.player.sendMessage(Text.literal("Veltrix AutoLever: " + msg), true);
            msgCooldown = 15;
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        if (msgCooldown > 0) msgCooldown--;

        BlockPos c = mc.player.getBlockPos();
        Set<BlockPos> now = new HashSet<>();
        for (BlockPos pos : BlockPos.iterate(c.add(-SCAN, -SCAN, -SCAN), c.add(SCAN, SCAN, SCAN))) {
            if (isSource(mc.world.getBlockState(pos))) now.add(pos.toImmutable());
        }

        if (prevCenter != null) {
            boolean holdsBucket = ignoreBucket.value && mc.player.isHolding(Items.WATER_BUCKET);
            for (BlockPos pos : now) {
                if (prev.contains(pos)) continue;
                if (Math.abs(pos.getX() - prevCenter.getX()) > SCAN
                        || Math.abs(pos.getY() - prevCenter.getY()) > SCAN
                        || Math.abs(pos.getZ() - prevCenter.getZ()) > SCAN) continue;
                if (!holdsBucket && pending.putIfAbsent(pos, TRIES) == null) {
                    say(mc, "wykryto wylaną wodę");
                }
            }
        }
        prev = now;
        prevCenter = c;

        if (pending.isEmpty()) return;

        int slot = PlaceUtil.findSlotOrOffhand(mc.player, s -> s.isOf(Items.LEVER));
        if (slot == PlaceUtil.NONE) {
            say(mc, "brak dźwigni w hotbarze ani w drugiej ręce");
            pending.clear();
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        int done = 0;
        Iterator<Map.Entry<BlockPos, Integer>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> e = it.next();
            BlockPos pos = e.getKey();
            if (!isSource(mc.world.getBlockState(pos))) {
                it.remove(); // zrodla juz nie ma (dzwignia postawiona)
                continue;
            }
            boolean inRange = Vec3d.ofCenter(pos).squaredDistanceTo(eye) <= range.value * range.value;
            if (inRange && done < 2) {
                if (!PlaceUtil.placeAt(mc, pos, slot)) say(mc, "brak podparcia dla dźwigni");
                done++;
            } else if (!inRange) {
                say(mc, "woda poza zasięgiem");
            }
            int left = e.getValue() - 1;
            if (left <= 0) it.remove();
            else e.setValue(left);
        }
    }
}

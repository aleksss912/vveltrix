package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.PlaceUtil;
import com.example.dripclient.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Dzwignia w zasiegu: mod wylewa na nia wode (zmywa dzwignie), a w jej miejsce
 * od razu stawia pajeczyne albo obsydian.
 * Uwaga: na chwile obraca kamere w strone dzwigni, bo wiadro wylewa wode tam, gdzie patrzysz.
 */
public class ErrorKillerModule extends Module {
    private final Setting.Bool water = add(new Setting.Bool("Wylewaj wodę", true));
    private final Setting.Bool web = add(new Setting.Bool("Pajęczyna", true));
    private final Setting.Bool obsidian = add(new Setting.Bool("Obsydian", true));
    private final Setting.Num range = add(new Setting.Num("Zasięg", 4.5, 1.0, 5.0, 0.5));
    private final Setting.Bool notify = add(new Setting.Bool("Komunikaty", true));

    private enum Phase { IDLE, AIMED, WAIT }

    private Phase phase = Phase.IDLE;
    private BlockPos target;
    private int timer;
    private float oldYaw, oldPitch;
    private int prevSlot;
    private int msgCooldown;
    private final Map<BlockPos, Integer> skip = new HashMap<>();

    public ErrorKillerModule() {
        super("ErrorKiller", Category.WORLD);
    }

    @Override
    public void onDisable() {
        phase = Phase.IDLE;
        target = null;
        skip.clear();
    }

    private void say(MinecraftClient mc, String msg) {
        if (notify.value && mc.player != null && msgCooldown == 0) {
            mc.player.sendMessage(Text.literal("Veltrix ErrorKiller: " + msg), true);
            msgCooldown = 20;
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (msgCooldown > 0) msgCooldown--;
        skip.replaceAll((k, v) -> v - 1);
        skip.values().removeIf(v -> v <= 0);

        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;

        switch (phase) {
            case IDLE -> {
                BlockPos lever = nearestLever(mc);
                if (lever == null) return;
                target = lever;
                timer = 0;

                if (!water.value) {
                    phase = Phase.WAIT; // tylko czekamy, az ktos zmyje dzwignie
                    return;
                }
                int bucket = PlaceUtil.findSlot(p, s -> s.isOf(Items.WATER_BUCKET));
                if (bucket == PlaceUtil.NONE) {
                    say(mc, "brak wiadra z wodą w hotbarze");
                    skip.put(lever, 60);
                    return;
                }
                // obroc sie w strone dzwigni i wybierz wiadro; wylanie w nastepnym ticku,
                // gdy serwer juz dostanie nowy obrot
                oldYaw = p.getYaw();
                oldPitch = p.getPitch();
                prevSlot = p.getInventory().selectedSlot;
                Vec3d eye = p.getEyePos();
                Vec3d t = Vec3d.ofCenter(lever);
                double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
                double hd = Math.sqrt(dx * dx + dz * dz);
                p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
                p.setPitch((float) -Math.toDegrees(Math.atan2(dy, hd)));
                p.getInventory().selectedSlot = bucket;
                phase = Phase.AIMED;
            }
            case AIMED -> {
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                p.setYaw(oldYaw);
                p.setPitch(oldPitch);
                p.getInventory().selectedSlot = prevSlot;
                phase = Phase.WAIT;
                timer = 0;
            }
            case WAIT -> {
                BlockState st = w.getBlockState(target);
                if (st.isOf(Blocks.COBWEB) || st.isOf(Blocks.OBSIDIAN)) {
                    skip.put(target, 40);
                    phase = Phase.IDLE;
                    return;
                }
                if (!st.isOf(Blocks.LEVER)) {
                    int slot = PlaceUtil.NONE;
                    if (web.value) slot = PlaceUtil.findSlotOrOffhand(p, s -> s.isOf(Items.COBWEB));
                    if (slot == PlaceUtil.NONE && obsidian.value) {
                        slot = PlaceUtil.findSlotOrOffhand(p, s -> s.isOf(Items.OBSIDIAN));
                    }
                    if (slot == PlaceUtil.NONE) {
                        say(mc, "brak pajęczyny / obsydianu");
                        skip.put(target, 100);
                        phase = Phase.IDLE;
                        return;
                    }
                    PlaceUtil.placeAt(mc, target, slot);
                    if (++timer > 10) {
                        skip.put(target, 60);
                        phase = Phase.IDLE;
                    }
                } else if (++timer > 14) {
                    skip.put(target, 100);
                    phase = Phase.IDLE;
                }
            }
        }
    }

    private BlockPos nearestLever(MinecraftClient mc) {
        Vec3d eye = mc.player.getEyePos();
        int r = (int) Math.ceil(range.value);
        BlockPos base = mc.player.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(base.add(-r, -r, -r), base.add(r, r, r))) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.LEVER)) continue;
            if (skip.containsKey(pos)) continue;
            double d = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
            if (d > range.value * range.value || d >= bestD) continue;
            bestD = d;
            best = pos.toImmutable();
        }
        return best;
    }
}

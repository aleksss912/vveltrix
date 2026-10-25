package com.example.dripclient;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

public final class PlaceUtil {
    public static final int NONE = -1;
    public static final int OFFHAND = -2;

    private PlaceUtil() {
    }

    /** Slot hotbara (0-8) z pasujacym przedmiotem albo NONE. */
    public static int findSlot(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && pred.test(s)) return i;
        }
        return NONE;
    }

    /** Najpierw druga reka, potem hotbar. */
    public static int findSlotOrOffhand(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty() && pred.test(off)) return OFFHAND;
        return findSlot(p, pred);
    }

    /**
     * Stawia przedmiot w komorce target, klikajac pelna sciane sasiedniego bloku.
     * Na czas klikniecia wysyla serwerowi "kucanie", zeby nie otworzyc skrzyni/pieca/drzwi.
     */
    public static boolean placeAt(MinecraftClient mc, BlockPos target, int slot) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null || mc.getNetworkHandler() == null
                || slot == NONE) {
            return false;
        }
        Vec3d eye = p.getEyePos();
        Hand hand = slot == OFFHAND ? Hand.OFF_HAND : Hand.MAIN_HAND;

        for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            BlockState ns = w.getBlockState(n);
            if (ns.isAir() || !ns.getFluidState().isEmpty()) continue;
            Direction face = d.getOpposite();
            if (!ns.isSideSolidFullSquare(w, n, face)) continue;

            Vec3d hitPos = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (hitPos.squaredDistanceTo(eye) > 5.0 * 5.0) continue;

            boolean wasSneaking = p.isSneaking();
            if (!wasSneaking) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            }
            int prev = p.getInventory().selectedSlot;
            if (slot >= 0) p.getInventory().selectedSlot = slot;
            mc.interactionManager.interactBlock(p, hand, new BlockHitResult(hitPos, face, n, false));
            if (slot >= 0) p.getInventory().selectedSlot = prev;
            if (!wasSneaking) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            }
            return true;
        }
        return false;
    }
}

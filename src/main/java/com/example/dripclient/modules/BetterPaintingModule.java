package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Obraz (i opcjonalnie ramka) klikany "przez" pajeczyne, tabliczke, tripwire itp.:
 * klikniecie trafia w pelna sciane za blokiem bez kolizji i tam stawia obraz.
 */
public class BetterPaintingModule extends Module {
    private final Setting.Bool frames = add(new Setting.Bool("Także ramki", true));

    private static boolean busy = false;

    public BetterPaintingModule() {
        super("BetterPainting", Category.WORLD);
    }

    /** Wywolywane z UseBlockCallback. */
    public static ActionResult onUse(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (busy || !world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        BetterPaintingModule m = ModuleManager.get(BetterPaintingModule.class);
        if (m == null || !m.enabled) return ActionResult.PASS;

        ItemStack st = player.getStackInHand(hand);
        boolean ok = st.isOf(Items.PAINTING)
                || (m.frames.value && (st.isOf(Items.ITEM_FRAME) || st.isOf(Items.GLOW_ITEM_FRAME)));
        if (!ok) return ActionResult.PASS;

        // trafiony blok ma kolizje (zwykla sciana)? wtedy nic nie zmieniamy
        if (!world.getBlockState(hit.getBlockPos()).getCollisionShape(world, hit.getBlockPos()).isEmpty()) {
            return ActionResult.PASS;
        }

        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(4.5));
        BlockHitResult r = world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (r.getType() != HitResult.Type.BLOCK) return ActionResult.PASS;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager == null || !(player instanceof ClientPlayerEntity cp)) return ActionResult.PASS;

        busy = true;
        try {
            ActionResult res = mc.interactionManager.interactBlock(cp, hand, r);
            return res.isAccepted() ? ActionResult.SUCCESS : ActionResult.FAIL;
        } finally {
            busy = false;
        }
    }
}

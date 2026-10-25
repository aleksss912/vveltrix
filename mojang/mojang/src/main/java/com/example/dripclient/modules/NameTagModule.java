package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Projection;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** Nick, zdrowie, dystans i zbroja nad kazdym graczem (widoczne przez sciany). */
public class NameTagModule extends Module {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private final Setting.Bool showHealth = add(new Setting.Bool("Zdrowie", true));
    private final Setting.Bool showDistance = add(new Setting.Bool("Dystans", true));
    private final Setting.Bool showArmor = add(new Setting.Bool("Zbroja i itemy", true));
    private final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.8, 0.4, 2.0, 0.1));
    private final Setting.Num maxDist = add(new Setting.Num("Maks. dystans", 128, 8, 256, 8));

    public NameTagModule() {
        super("NameTag", Category.RENDER);
    }

    @Override
    public void onHud(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            float dist = mc.player.distanceTo(p);
            if (dist > maxDist.value) continue;

            Vec3d pos = p.getLerpedPos(delta).add(0, p.getHeight() + 0.45, 0);
            Projection.P sp = Projection.project(mc, ctx, pos);
            if (sp.behind) continue;

            String name = p.getName().getString();
            boolean friend = ModuleManager.isFriend(name);
            float hp = p.getHealth() + p.getAbsorptionAmount();
            float ratio = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
            int hpColor = 0xFF000000 | ((int) ((1f - ratio) * 255) << 16) | ((int) (ratio * 255) << 8) | 0x40;

            String hpText = String.format(Locale.ROOT, "%.1f", hp);
            String distText = String.format(Locale.ROOT, "%.0fm", dist);

            int total = tr.getWidth(name);
            if (showHealth.value) total += 6 + tr.getWidth(hpText);
            if (showDistance.value) total += 6 + tr.getWidth(distText);

            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) sp.x, (float) sp.y, 0f);
            float s = (float) scale.value;
            m.scale(s, s, 1f);

            int x0 = -total / 2;
            int y0 = -10;
            ctx.fill(x0 - 3, y0 - 2, x0 + total + 3, y0 + 10, 0x99000000);
            ctx.drawTextWithShadow(tr, name, x0, y0, friend ? 0xFF55FF55 : 0xFFFFFFFF);
            int cx = x0 + tr.getWidth(name);
            if (showHealth.value) {
                cx += 6;
                ctx.drawTextWithShadow(tr, hpText, cx, y0, hpColor);
                cx += tr.getWidth(hpText);
            }
            if (showDistance.value) {
                cx += 6;
                ctx.drawTextWithShadow(tr, distText, cx, y0, 0xFFA0A4B0);
            }

            if (showArmor.value) {
                int sy = y0 - 20;
                int sx = -SLOTS.length * 8;
                for (int i = 0; i < SLOTS.length; i++) {
                    ItemStack st = p.getEquippedStack(SLOTS[i]);
                    if (st.isEmpty()) continue;
                    ctx.drawItem(st, sx + i * 16, sy);
                    ctx.drawStackOverlay(tr, st, sx + i * 16, sy);
                }
                String ar = "Pancerz " + p.getArmor();
                ctx.drawTextWithShadow(tr, ar, -tr.getWidth(ar) / 2, y0 + 12, 0xFF8FB8FF);
            }
            m.pop();
        }
    }
}

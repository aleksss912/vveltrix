package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** Atakuje automatycznie, gdy celownik jest na graczu (z uwzglednieniem cooldownu broni). */
public class TriggerBotModule extends Module {
    private final Setting.Num cooldown = add(new Setting.Num("Cooldown ataku", 0.9, 0.0, 1.0, 0.05));
    private final Setting.Bool skipFriends = add(new Setting.Bool("Pomiń znajomych", true));
    private final Setting.Bool onlyHold = add(new Setting.Bool("Tylko przy LPM", false));

    public TriggerBotModule() {
        super("TriggerBot", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (onlyHold.value && !mc.options.attackKey.isPressed()) return;

        HitResult hr = mc.crosshairTarget;
        if (!(hr instanceof EntityHitResult ehr)) return;
        Entity e = ehr.getEntity();
        if (!(e instanceof PlayerEntity target) || !target.isAlive() || target.isSpectator()) return;
        if (skipFriends.value && ModuleManager.isFriend(target.getName().getString())) return;
        if (mc.player.getAttackCooldownProgress(0f) < cooldown.value) return;

        mc.interactionManager.attackEntity(mc.player, e);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}

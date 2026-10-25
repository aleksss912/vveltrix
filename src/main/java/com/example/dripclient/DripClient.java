package com.example.dripclient;

import com.example.dripclient.gui.ClickGuiScreen;
import com.example.dripclient.modules.BetterPaintingModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class DripClient implements ClientModInitializer {
    private static KeyBinding guiKey;
    private static boolean middleWasDown = false;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();

        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dripclient.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.dripclient"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.wasPressed()) {
                mc.setScreen(new ClickGuiScreen());
            }
            handleMiddleClick(mc);
            for (Module m : ModuleManager.MODULES) {
                if (m.enabled) m.onTick(mc);
            }
        });

        HudRenderCallback.EVENT.register((ctx, counter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            float delta = counter.getTickDelta(true);
            for (Module m : ModuleManager.MODULES) {
                if (m.enabled) m.onFrame(mc, delta);
            }
            if (mc.options.hudHidden) return;
            for (Module m : ModuleManager.MODULES) {
                if (m.enabled) m.onHud(ctx, delta);
            }
        });

        UseBlockCallback.EVENT.register(BetterPaintingModule::onUse);
    }

    /** Srodkowy przycisk myszy na graczu dodaje/usuwa go ze znajomych. */
    private static void handleMiddleClick(MinecraftClient mc) {
        if (mc.player == null || mc.getWindow() == null) return;
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                == GLFW.GLFW_PRESS;
        if (down && !middleWasDown && mc.currentScreen == null
                && mc.targetedEntity instanceof PlayerEntity target) {
            String name = target.getName().getString();
            boolean added = ModuleManager.toggleFriend(name);
            ModuleManager.save();
            mc.player.sendMessage(Text.literal("Veltrix: " + name + (added ? " dodany do znajomych" : " usunięty ze znajomych")), true);
        }
        middleWasDown = down;
    }
}

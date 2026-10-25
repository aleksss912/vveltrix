package com.example.dripclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Dzwieki GUI i modulow z regulacja glosnosci i mozliwoscia wylaczenia. */
public final class Sounds {
    private Sounds() {
    }

    private static void emit(SoundEvent ev, float pitch) {
        if (UiConfig.volume <= 0.01) return;
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(ev, pitch, (float) UiConfig.volume));
    }

    // --- dzwieki GUI ---
    public static void gui(SoundEvent ev, float pitch) {
        if (UiConfig.guiSounds) emit(ev, pitch);
    }

    public static void gui(RegistryEntry<SoundEvent> ev, float pitch) {
        gui(ev.value(), pitch);
    }

    // --- dzwieki modulow ---
    public static void module(SoundEvent ev, float pitch) {
        if (UiConfig.moduleSounds) emit(ev, pitch);
    }

    public static void module(RegistryEntry<SoundEvent> ev, float pitch) {
        module(ev.value(), pitch);
    }

    public static void guiOpen() {
        gui(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f);
        gui(SoundEvents.UI_BUTTON_CLICK, 0.7f);
    }

    public static void guiClose() {
        gui(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f);
        gui(SoundEvents.UI_BUTTON_CLICK, 0.6f);
    }

    public static void guiCrack() {
        gui(SoundEvents.BLOCK_GLASS_BREAK, 1.0f);
        gui(SoundEvents.BLOCK_STONE_BREAK, 0.8f);
    }

    public static void click(float pitch) {
        gui(SoundEvents.UI_BUTTON_CLICK, pitch);
    }

    public static void moduleToggle(boolean on) {
        if (on) {
            module(SoundEvents.UI_BUTTON_CLICK, 1.6f);
            module(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.9f);
        } else {
            module(SoundEvents.UI_BUTTON_CLICK, 0.8f);
            module(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f);
        }
    }
}

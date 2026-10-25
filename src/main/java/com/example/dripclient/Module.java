package com.example.dripclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public abstract class Module {
    public final String name;
    public final Category category;
    public boolean enabled;
    /** Czy w rozwinietych ustawieniach pokazac liste znajomych. */
    public boolean hasFriends = false;
    public final List<Setting> settings = new ArrayList<>();

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    public void toggle() {
        enabled = !enabled;
        Sounds.moduleToggle(enabled);
        if (enabled) onEnable();
        else onDisable();
    }

    public void onEnable() {
    }

    /** Dodatkowe dane modulu zapisywane w pliku ustawien. */
    public void saveExtra(Properties p) {
    }

    public void loadExtra(Properties p) {
    }

    public void onDisable() {
    }

    /** Co tick klienta, gdy modul jest wlaczony. */
    public void onTick(MinecraftClient mc) {
    }

    /** Co klatke (takze przy ukrytym HUD), gdy modul jest wlaczony. */
    public void onFrame(MinecraftClient mc, float delta) {
    }

    /** Co klatke (HUD), gdy modul jest wlaczony. */
    public void onHud(DrawContext ctx, float delta) {
    }
}

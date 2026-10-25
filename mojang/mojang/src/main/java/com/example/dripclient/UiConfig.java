package com.example.dripclient;

import net.minecraft.util.math.MathHelper;

import java.util.Properties;

/** Ustawienia wygladu i dzwiekow GUI (zapisywane razem z modulami). */
public final class UiConfig {
    // kolor 0 = glowny, kolor 1 = dodatkowy (HSV, kazda skladowa 0..1)
    public static final float[] H = {0.70f, 0.57f};
    public static final float[] S = {0.64f, 0.74f};
    public static final float[] V = {1.00f, 0.96f};

    public static double panelAlpha = 0.80;
    public static boolean crackClose = true;
    public static boolean guiSounds = true;
    public static boolean moduleSounds = true;
    public static double volume = 0.8;

    private UiConfig() {
    }

    public static int color(int idx) {
        return 0xFF000000 | MathHelper.hsvToRgb(H[idx], S[idx], V[idx]);
    }

    public static int primary() {
        return color(0);
    }

    public static int secondary() {
        return color(1);
    }

    public static void resetTheme() {
        H[0] = 0.70f; S[0] = 0.64f; V[0] = 1.00f;
        H[1] = 0.57f; S[1] = 0.74f; V[1] = 0.96f;
        panelAlpha = 0.80;
    }

    public static void save(Properties p) {
        for (int i = 0; i < 2; i++) {
            p.setProperty("ui.h" + i, String.valueOf(H[i]));
            p.setProperty("ui.s" + i, String.valueOf(S[i]));
            p.setProperty("ui.v" + i, String.valueOf(V[i]));
        }
        p.setProperty("ui.panelAlpha", String.valueOf(panelAlpha));
        p.setProperty("ui.crackClose", String.valueOf(crackClose));
        p.setProperty("ui.guiSounds", String.valueOf(guiSounds));
        p.setProperty("ui.moduleSounds", String.valueOf(moduleSounds));
        p.setProperty("ui.volume", String.valueOf(volume));
    }

    public static void load(Properties p) {
        for (int i = 0; i < 2; i++) {
            H[i] = f(p, "ui.h" + i, H[i]);
            S[i] = f(p, "ui.s" + i, S[i]);
            V[i] = f(p, "ui.v" + i, V[i]);
        }
        panelAlpha = MathHelper.clamp(d(p, "ui.panelAlpha", panelAlpha), 0.3, 1.0);
        crackClose = Boolean.parseBoolean(p.getProperty("ui.crackClose", String.valueOf(crackClose)));
        guiSounds = Boolean.parseBoolean(p.getProperty("ui.guiSounds", String.valueOf(guiSounds)));
        moduleSounds = Boolean.parseBoolean(p.getProperty("ui.moduleSounds", String.valueOf(moduleSounds)));
        volume = MathHelper.clamp(d(p, "ui.volume", volume), 0.0, 1.0);
    }

    private static float f(Properties p, String key, float def) {
        try {
            return MathHelper.clamp(Float.parseFloat(p.getProperty(key)), 0f, 1f);
        } catch (Exception e) {
            return def;
        }
    }

    private static double d(Properties p, String key, double def) {
        try {
            return Double.parseDouble(p.getProperty(key));
        } catch (Exception e) {
            return def;
        }
    }
}

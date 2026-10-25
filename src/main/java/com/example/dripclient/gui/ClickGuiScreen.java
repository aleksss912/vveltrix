package com.example.dripclient.gui;

import com.example.dripclient.Category;
import com.example.dripclient.Draw;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Setting;
import com.example.dripclient.Sounds;
import com.example.dripclient.UiConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ciemny, polprzezroczysty panel: menu kategorii po lewej, dwie kolumny modulow po prawej,
 * zakladka Ustawienia (color picker, przezroczystosc, dzwieki, animacje).
 */
public class ClickGuiScreen extends Screen {

    private static final int GW = 396, GH = 240, SIDE = 96;
    private static final int R = 9; // promien zaokraglenia rogow
    private static final long OPEN_MS = 240, CLOSE_MS = 200;
    private static final float CRACK_SEC = 0.30f, FALL_SEC = 0.80f;
    private static final int TXT = 0xFFFFFFFF, DIM = 0xFF9AA0B2, SOFT = 0xFFB4B9CA;

    private static int lastTab = 0;

    private interface Drag {
        void apply(double mx, double my);
    }

    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right, Drag drag, boolean scrolled) {
    }

    private record Shard(float x, float y, float vx, float vy, float rot, float vrot, int color) {
    }

    private record Crack(float x1, float y1, float x2, float y2, float t0, float t1) {
    }

    private final List<Category> cats = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> animMap = new HashMap<>();
    private final Random rng = new Random();
    private final List<Shard> shards = new ArrayList<>();
    private final List<Crack> cracks = new ArrayList<>();
    private final float[] scrollTarget = new float[16];
    private final float[] scrollCur = new float[16];

    private int tab;
    private float tabFade = 1f;
    private int colorTarget = 0;
    private Drag dragging;
    private Setting.Key listeningKey;
    private boolean inputFocused;
    private final StringBuilder input = new StringBuilder();

    private final long openStart;
    private long closeStart, lastFrame;
    private boolean closing, crackMode, ready;
    private float alpha = 0f;
    private float frameDt = 0.016f;
    private int gx, gy;
    private int cX, cY, cW, cH; // prostokat zawartosci
    private float impactX, impactY;

    public ClickGuiScreen() {
        super(Text.literal("Veltrix"));
        for (Category c : Category.values()) {
            for (Module m : ModuleManager.MODULES) {
                if (m.category == c) {
                    cats.add(c);
                    break;
                }
            }
        }
        tab = Math.min(lastTab, cats.size());
        openStart = Util.getMeasuringTimeMs();
        lastFrame = openStart;
        Sounds.guiOpen();
    }

    private boolean settingsTab() {
        return tab >= cats.size();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ pomocnicze

    private int a(int c) {
        int al = (int) (((c >>> 24) & 0xFF) * alpha);
        return (al << 24) | (c & 0xFFFFFF);
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int al = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (al << 24) | (r << 16) | (g << 8) | b;
    }

    private void text(DrawContext ctx, String s, int x, int y, int color) {
        if (((color >>> 24) & 0xFF) * alpha < 8) return; // alfa < 4 w MC znaczy "pelna"
        ctx.drawTextWithShadow(textRenderer, s, x, y, a(color));
    }

    private void textScaled(DrawContext ctx, String s, int x, int y, int color, float scale) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate((float) x, (float) y, 0f);
        m.scale(scale, scale, 1f);
        text(ctx, s, 0, 0, color);
        m.pop();
    }

    private float anim(String key, boolean target) {
        float t = target ? 1f : 0f;
        Float cur = animMap.get(key);
        float v = cur == null ? t : cur;
        v += (t - v) * Math.min(1f, frameDt * 14f);
        if (Math.abs(t - v) < 0.003f) v = t;
        animMap.put(key, v);
        return v;
    }

    private void hGradient(DrawContext ctx, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i += 3) {
            int e = Math.min(i + 3, w);
            ctx.fill(x + i, y, x + e, y + h, a(lerpColor(c1, c2, i / (float) Math.max(1, w))));
        }
    }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy)));
    }

    /** Prostokat z zaokraglonymi rogami (osobno lewa i prawa strona). */
    private static void roundedFill(DrawContext ctx, int x, int y, int w, int h, int r,
                                    boolean roundLeft, boolean roundRight, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            int l = roundLeft ? in : 0;
            int rt = roundRight ? in : 0;
            ctx.fill(x + l, y + i, x + w - rt, y + i + 1, color);
            ctx.fill(x + l, y + h - 1 - i, x + w - rt, y + h - i, color);
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
    }

    private static void roundedBorder(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        int a0 = inset(r, 0);
        ctx.fill(x + a0, y, x + w - a0, y + 1, color);
        ctx.fill(x + a0, y + h - 1, x + w - a0, y + h, color);
        for (int i = 1; i < r; i++) {
            int ai = inset(r, i);
            int run = Math.max(1, inset(r, i - 1) - ai);
            ctx.fill(x + ai, y + i, x + ai + run, y + i + 1, color);
            ctx.fill(x + w - ai - run, y + i, x + w - ai, y + i + 1, color);
            ctx.fill(x + ai, y + h - 1 - i, x + ai + run, y + h - i, color);
            ctx.fill(x + w - ai - run, y + h - 1 - i, x + w - ai, y + h - i, color);
        }
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    private static String keyName(int code) {
        if (code <= 0) return "brak";
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float trackT(double mx, int x, int w) {
        return MathHelper.clamp((float) ((mx - x) / Math.max(1, w)), 0f, 1f);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = Util.getMeasuringTimeMs();
        frameDt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        gx = (width - GW) / 2;
        gy = (height - GH) / 2;
        cX = gx + SIDE + 10;
        cY = gy + 38;
        cW = GW - SIDE - 20;
        cH = GH - 38 - 10;
        tabFade = Math.min(1f, tabFade + frameDt * 5f);
        hits.clear();

        float scale = 1f, shakeX = 0f, shakeY = 0f, crackP = 0f;
        boolean showCracks = false;
        ready = false;

        if (!closing) {
            float p = MathHelper.clamp((now - openStart) / (float) OPEN_MS, 0f, 1f);
            float e = 1f - (1f - p) * (1f - p) * (1f - p);
            alpha = e;
            scale = 0.85f + 0.15f * e;
            ready = p >= 1f;
        } else if (!crackMode) {
            float q = MathHelper.clamp((now - closeStart) / (float) CLOSE_MS, 0f, 1f);
            float e = 1f - q * q * q;
            alpha = e;
            scale = 0.85f + 0.15f * e;
            if (q >= 1f) {
                finish();
                return;
            }
        } else {
            float t = (now - closeStart) / 1000f;
            if (t < CRACK_SEC) {
                alpha = 1f;
                crackP = t / CRACK_SEC;
                showCracks = true;
                float amp = crackP * 2.2f;
                shakeX = (rng.nextFloat() - 0.5f) * 2f * amp;
                shakeY = (rng.nextFloat() - 0.5f) * 2f * amp;
            } else if (t < CRACK_SEC + FALL_SEC) {
                renderShards(ctx, t - CRACK_SEC);
                return;
            } else {
                finish();
                return;
            }
        }

        ctx.fill(0, 0, width, height, a(0x70000000));

        MatrixStack m = ctx.getMatrices();
        m.push();
        float cx = gx + GW / 2f, cy = gy + GH / 2f;
        m.translate(cx + shakeX, cy + shakeY, 0f);
        m.scale(scale, scale, 1f);
        m.translate(-cx, -cy, 0f);
        drawGui(ctx, mx, my);
        if (showCracks) drawCracks(ctx, crackP);
        m.pop();
    }

    private void drawGui(DrawContext ctx, int mx, int my) {
        int pri = UiConfig.primary(), sec = UiConfig.secondary();
        float pa = (float) UiConfig.panelAlpha;

        // miekki cien (zaokraglony), panel, pasek boczny i ramka
        for (int i = 1; i <= 6; i++) {
            roundedFill(ctx, gx - i, gy - i, GW + 2 * i, GH + 2 * i, R + i, true, true, a(0x0C000000));
        }
        roundedFill(ctx, gx, gy, GW, GH, R, true, true, a(Draw.withAlpha(0xFF0D0F15, pa)));
        roundedFill(ctx, gx, gy, SIDE, GH, R, true, false, a(Draw.withAlpha(0xFF07080C, pa)));
        ctx.fill(gx + SIDE, gy + 1, gx + SIDE + 1, gy + GH - 1, a(Draw.withAlpha(pri, 0.35f)));
        roundedBorder(ctx, gx, gy, GW, GH, R, a(Draw.withAlpha(pri, 0.55f)));
        hGradient(ctx, gx + R, gy, GW - 2 * R, 2, pri, sec);

        // tytul
        textScaled(ctx, "Veltrix", gx + 16, gy + 14, TXT, 1.5f);
        text(ctx, "klient 1.21.4", gx + 16, gy + 30, DIM);

        // zakladki po lewej
        int total = cats.size() + 1;
        for (int i = 0; i < total; i++) {
            boolean isSettings = i == cats.size();
            String label = isSettings ? "Ustawienia" : cats.get(i).label;
            int tx = gx + 8, tw = SIDE - 16, th = 22;
            int ty = isSettings ? gy + GH - 58 : gy + 46 + i * 26;
            boolean hov = ready && in(mx, my, tx, ty, tw, th);
            float hv = anim("th" + i, hov);
            float sel = anim("ts" + i, tab == i);
            ctx.fill(tx, ty, tx + tw, ty + th, a(Draw.withAlpha(pri, 0.28f * sel + 0.12f * hv)));
            int bar = Math.round(th * sel);
            ctx.fill(tx, ty + (th - bar) / 2, tx + 3, ty + (th + bar) / 2, a(pri));
            text(ctx, label, tx + 12, ty + 7, lerpColor(DIM, TXT, Math.max(sel, hv)));
            final int idx = i;
            hits.add(new Hit(tx, ty, tw, th, () -> switchTab(idx), null, null, false));
        }
        text(ctx, "P / Esc - zamknij", gx + 12, gy + GH - 18, DIM);

        // zawartosc
        String title = settingsTab() ? "Ustawienia" : cats.get(tab).label;
        textScaled(ctx, title, cX, gy + 14, TXT, 1.3f);
        hGradient(ctx, cX, gy + 30, 40, 2, pri, sec);

        float saved = alpha;
        alpha = saved * MathHelper.clamp(tabFade, 0f, 1f);
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(0f, (1f - tabFade) * 6f, 0f);
        if (settingsTab()) drawSettingsTab(ctx, mx, my);
        else drawModulesTab(ctx, mx, my, cats.get(tab));
        m.pop();
        alpha = saved;
    }

    private void switchTab(int i) {
        if (i == tab) return;
        tab = i;
        lastTab = i;
        tabFade = 0f;
        inputFocused = false;
        Sounds.click(1.2f);
    }

    // ------------------------------------------------------------------ zakladka modulow

    private int friendsHeight() {
        return 48 + 14 * ModuleManager.FRIENDS.size();
    }

    private int settingsHeight(Module m) {
        int h = 0;
        for (Setting s : m.settings) {
            if (s instanceof Setting.Num) h += 24;
            else if (s instanceof Setting.Action || s instanceof Setting.Key) h += 20;
            else h += 18;
        }
        if (m.hasFriends) h += friendsHeight();
        return h;
    }

    private int cardHeight(Module m) {
        return 24 + (expanded.contains(m) ? settingsHeight(m) + 4 : 0);
    }

    private void toggleExpand(Module m) {
        if (!expanded.remove(m)) expanded.add(m);
        Sounds.click(1.1f);
    }

    private void drawModulesTab(DrawContext ctx, int mx, int my, Category cat) {
        List<Module> mods = new ArrayList<>();
        for (Module m : ModuleManager.MODULES) if (m.category == cat) mods.add(m);

        int colW = (cW - 8) / 2;
        List<List<Module>> cols = new ArrayList<>();
        cols.add(new ArrayList<>());
        cols.add(new ArrayList<>());
        int[] colH = new int[2];
        for (Module m : mods) {
            int c = colH[0] <= colH[1] ? 0 : 1;
            cols.get(c).add(m);
            colH[c] += cardHeight(m) + 6;
        }
        int maxH = Math.max(colH[0], colH[1]);
        float maxScroll = Math.max(0, maxH - cH);
        scrollTarget[tab] = MathHelper.clamp(scrollTarget[tab], 0f, maxScroll);
        scrollCur[tab] += (scrollTarget[tab] - scrollCur[tab]) * Math.min(1f, frameDt * 14f);
        int off = Math.round(scrollCur[tab]);

        ctx.enableScissor(cX, cY, cX + cW, cY + cH);
        for (int c = 0; c < 2; c++) {
            int y = cY - off;
            for (Module m : cols.get(c)) {
                y = drawCard(ctx, m, cX + c * (colW + 8), y, colW, mx, my) + 6;
            }
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int bh = Math.max(20, (int) (cH * (cH / (float) maxH)));
            int by = cY + (int) ((cH - bh) * (scrollCur[tab] / maxScroll));
            ctx.fill(cX + cW + 3, by, cX + cW + 5, by + bh, a(Draw.withAlpha(UiConfig.primary(), 0.8f)));
        }
    }

    private int drawCard(DrawContext ctx, Module m, int x, int y, int w, int mx, int my) {
        int pri = UiConfig.primary(), sec = UiConfig.secondary();
        int h = cardHeight(m);
        boolean inView = in(mx, my, cX, cY, cW, cH);
        boolean hov = ready && inView && in(mx, my, x, y, w, 24);
        float hv = anim("c:" + m.name, hov);
        float en = anim("e:" + m.name, m.enabled);

        ctx.fill(x, y, x + w, y + h, a(0xC8141720));
        if (en > 0.01f) {
            ctx.fill(x, y, x + w, y + h, a(Draw.withAlpha(pri, 0.13f * en)));
            ctx.fillGradient(x, y, x + 3, y + h, a(Draw.withAlpha(pri, en)), a(Draw.withAlpha(sec, en)));
        }
        if (hv > 0.01f) ctx.fill(x, y, x + w, y + 24, a(Draw.withAlpha(0xFFFFFFFF, 0.08f * hv)));
        ctx.drawBorder(x, y, w, h, a(Draw.withAlpha(0xFFFFFFFF, 0.06f + 0.07f * hv)));

        text(ctx, m.name, x + 10, y + 8, lerpColor(SOFT, TXT, Math.max(en, hv)));
        boolean hasSettings = !m.settings.isEmpty() || m.hasFriends;
        if (hasSettings) text(ctx, expanded.contains(m) ? "-" : "+", x + w - 46, y + 8, DIM);

        int sx = x + w - 32, sy = y + 7;
        ctx.fill(sx, sy, sx + 22, sy + 10, a(lerpColor(0xFF343948, pri, en)));
        ctx.drawBorder(sx, sy, 22, 10, a(0x55000000));
        int kx = sx + 1 + Math.round(12 * en);
        ctx.fill(kx, sy + 1, kx + 8, sy + 9, a(0xFFFFFFFF));

        hits.add(new Hit(x, y, w, 24, m::toggle, () -> toggleExpand(m), null, true));
        if (hasSettings) {
            hits.add(new Hit(x + w - 52, y, 18, 24, () -> toggleExpand(m), () -> toggleExpand(m), null, true));
        }

        int cy = y + 26;
        if (expanded.contains(m)) {
            for (Setting s : m.settings) cy = drawSetting(ctx, m, s, x, cy, w, mx, my);
            if (m.hasFriends) drawFriends(ctx, x, cy, w, mx, my);
        }
        return y + h;
    }

    private int drawSetting(DrawContext ctx, Module m, Setting s, int x, int cy, int w, int mx, int my) {
        int x0 = x + 8, w0 = w - 16;
        if (s instanceof Setting.Bool b) {
            return switchRow(ctx, b.name, x0, cy, w0, b.value, "b:" + m.name + b.name,
                    () -> {
                        b.value = !b.value;
                        Sounds.click(b.value ? 1.4f : 0.9f);
                    }, true, mx, my);
        }
        if (s instanceof Setting.Num n) {
            String v = (n.step >= 1) ? String.valueOf((int) n.value) : String.format(Locale.ROOT, "%.2f", n.value);
            float t = (float) ((n.value - n.min) / (n.max - n.min));
            final int fx = x0, fw = w0;
            return sliderRow(ctx, n.name, v, x0, cy, w0, t,
                    (mx2, my2) -> n.set(n.min + trackT(mx2, fx, fw) * (n.max - n.min)), true);
        }
        if (s instanceof Setting.Action act) {
            boolean hov = ready && in(mx, my, cX, cY, cW, cH) && in(mx, my, x0, cy + 1, w0, 17);
            float hv = anim("a:" + m.name + act.name, hov);
            ctx.fill(x0, cy + 1, x0 + w0, cy + 18, a(lerpColor(0xFF232734, UiConfig.primary(), 0.25f + 0.45f * hv)));
            ctx.drawBorder(x0, cy + 1, w0, 17, a(0x33FFFFFF));
            text(ctx, act.name, x0 + w0 / 2 - textRenderer.getWidth(act.name) / 2, cy + 6, TXT);
            hits.add(new Hit(x0, cy + 1, w0, 17, () -> {
                act.run.run();
                Sounds.click(1.3f);
            }, null, null, true));
            return cy + 20;
        }
        if (s instanceof Setting.Key k) {
            boolean hov = ready && in(mx, my, cX, cY, cW, cH) && in(mx, my, x0, cy + 1, w0, 17);
            float hv = anim("k:" + m.name + k.name, hov);
            boolean listening = listeningKey == k;
            int bg = listening ? Draw.withAlpha(UiConfig.primary(), 0.55f)
                    : lerpColor(0xFF232734, UiConfig.primary(), 0.2f + 0.4f * hv);
            ctx.fill(x0, cy + 1, x0 + w0, cy + 18, a(bg));
            ctx.drawBorder(x0, cy + 1, w0, 17, a(listening ? Draw.withAlpha(0xFFFFFFFF, 0.7f) : 0x33FFFFFF));
            String lab = listening ? "Naciśnij klawisz..." : k.name + ": " + keyName(k.code);
            text(ctx, lab, x0 + w0 / 2 - textRenderer.getWidth(lab) / 2, cy + 6, TXT);
            hits.add(new Hit(x0, cy + 1, w0, 17, () -> {
                listeningKey = k;
                Sounds.click(1.3f);
            }, null, null, true));
            return cy + 20;
        }
        return cy;
    }

    private int switchRow(DrawContext ctx, String label, int x, int y, int w, boolean value, String key,
                          Runnable onClick, boolean scrolled, int mx, int my) {
        boolean inView = !scrolled || in(mx, my, cX, cY, cW, cH);
        boolean hov = ready && inView && in(mx, my, x, y, w, 18);
        float hv = anim("h:" + key, hov);
        float v = anim("v:" + key, value);
        int pri = UiConfig.primary();
        text(ctx, label, x, y + 5, lerpColor(SOFT, TXT, hv));
        int sx = x + w - 22, sy = y + 4;
        ctx.fill(sx, sy, sx + 20, sy + 9, a(lerpColor(0xFF343948, pri, v)));
        ctx.drawBorder(sx, sy, 20, 9, a(0x55000000));
        ctx.fill(sx + 1 + Math.round(11 * v), sy + 1, sx + 8 + Math.round(11 * v), sy + 8, a(0xFFFFFFFF));
        hits.add(new Hit(x, y, w, 18, onClick, null, null, scrolled));
        return y + 18;
    }

    private int sliderRow(DrawContext ctx, String label, String value, int x, int y, int w, float t,
                          Drag drag, boolean scrolled) {
        int pri = UiConfig.primary(), sec = UiConfig.secondary();
        t = MathHelper.clamp(t, 0f, 1f);
        text(ctx, label, x, y + 2, SOFT);
        text(ctx, value, x + w - textRenderer.getWidth(value), y + 2, sec);
        int ty = y + 14;
        ctx.fill(x, ty, x + w, ty + 5, a(0xFF0B0C11));
        int fw = (int) (w * t);
        if (fw > 0) hGradient(ctx, x, ty, fw, 5, pri, sec);
        ctx.fill(x + fw - 2, ty - 2, x + fw + 2, ty + 7, a(0xFFFFFFFF));
        hits.add(new Hit(x - 2, y, w + 4, 24, null, null, drag, scrolled));
        return y + 24;
    }

    private void drawFriends(DrawContext ctx, int x, int cy, int w, int mx, int my) {
        int pri = UiConfig.primary(), sec = UiConfig.secondary();
        int x0 = x + 8, w0 = w - 16;
        text(ctx, "Znajomi", x0, cy + 2, sec);

        ctx.fill(x0, cy + 14, x0 + w0, cy + 30, a(0xFF0A0B10));
        ctx.drawBorder(x0, cy + 14, w0, 16, a(inputFocused ? Draw.withAlpha(pri, 0.9f) : 0x33FFFFFF));
        boolean blink = (Util.getMeasuringTimeMs() / 500) % 2 == 0;
        if (input.length() == 0 && !inputFocused) text(ctx, "nick + Enter", x0 + 4, cy + 18, DIM);
        else text(ctx, input + (inputFocused && blink ? "_" : ""), x0 + 4, cy + 18, TXT);
        hits.add(new Hit(x0, cy + 14, w0, 16, () -> inputFocused = true, null, null, true));

        int ry = cy + 34;
        for (String n : new ArrayList<>(ModuleManager.FRIENDS)) {
            boolean hov = ready && in(mx, my, cX, cY, cW, cH) && in(mx, my, x0, ry, w0, 14);
            if (hov) ctx.fill(x0, ry, x0 + w0, ry + 14, a(0x22FFFFFF));
            text(ctx, n, x0 + 4, ry + 3, 0xFF55FF55);
            text(ctx, "x", x0 + w0 - 10, ry + 3, 0xFFFF6666);
            hits.add(new Hit(x0, ry, w0, 14, () -> {
                ModuleManager.removeFriend(n);
                Sounds.click(0.9f);
            }, null, null, true));
            ry += 14;
        }
        text(ctx, "MMB na graczu = znajomy", x0, ry + 3, DIM);
    }

    // ------------------------------------------------------------------ zakladka ustawien

    private void drawSettingsTab(DrawContext ctx, int mx, int my) {
        int pri = UiConfig.primary(), sec = UiConfig.secondary();
        int x0 = cX, y0 = cY;
        final int t = colorTarget;

        // --- color picker ---
        text(ctx, "Motyw", x0, y0, sec);
        for (int i = 0; i < 2; i++) {
            int rx = x0 + i * 66, ry = y0 + 14;
            boolean sel = colorTarget == i;
            boolean hov = ready && in(mx, my, rx, ry, 62, 16);
            float hv = anim("sw" + i, hov);
            ctx.fill(rx, ry, rx + 62, ry + 16, a(UiConfig.color(i)));
            ctx.drawBorder(rx, ry, 62, 16, a(sel ? 0xFFFFFFFF : Draw.withAlpha(0xFFFFFFFF, 0.2f + 0.3f * hv)));
            String lab = "Kolor " + (i + 1);
            text(ctx, lab, rx + 31 - textRenderer.getWidth(lab) / 2, ry + 4, TXT);
            final int idx = i;
            hits.add(new Hit(rx, ry, 62, 16, () -> {
                colorTarget = idx;
                Sounds.click(1.2f);
            }, null, null, false));
        }

        final int bx = x0, by = y0 + 36, bw = 128, bh = 84;
        for (int i = 0; i < bw; i++) {
            int top = 0xFF000000 | MathHelper.hsvToRgb(UiConfig.H[t], i / (bw - 1f), 1f);
            ctx.fillGradient(bx + i, by, bx + i + 1, by + bh, a(top), a(0xFF000000));
        }
        ctx.drawBorder(bx - 1, by - 1, bw + 2, bh + 2, a(0x66FFFFFF));
        int mkx = bx + Math.round(UiConfig.S[t] * (bw - 1));
        int mky = by + Math.round((1f - UiConfig.V[t]) * (bh - 1));
        ctx.fill(mkx - 4, mky - 4, mkx + 4, mky + 4, a(0xFF000000));
        ctx.fill(mkx - 3, mky - 3, mkx + 3, mky + 3, a(0xFFFFFFFF));
        ctx.fill(mkx - 2, mky - 2, mkx + 2, mky + 2, a(UiConfig.color(t)));
        hits.add(new Hit(bx, by, bw, bh, null, null, (mx2, my2) -> {
            UiConfig.S[t] = trackT(mx2, bx, bw - 1);
            UiConfig.V[t] = 1f - trackT(my2, by, bh - 1);
        }, false));

        final int hx = x0, hy = by + bh + 6, hh = 10;
        for (int i = 0; i < bw; i++) {
            ctx.fill(hx + i, hy, hx + i + 1, hy + hh, a(0xFF000000 | MathHelper.hsvToRgb(i / (float) bw, 1f, 1f)));
        }
        ctx.drawBorder(hx - 1, hy - 1, bw + 2, hh + 2, a(0x66FFFFFF));
        int hmx = hx + Math.round(UiConfig.H[t] * (bw - 1));
        ctx.fill(hmx - 2, hy - 3, hmx + 2, hy + hh + 3, a(0xFF000000));
        ctx.fill(hmx - 1, hy - 2, hmx + 1, hy + hh + 2, a(0xFFFFFFFF));
        hits.add(new Hit(hx, hy, bw, hh, null, null, (mx2, my2) ->
                UiConfig.H[t] = Math.min(0.999f, trackT(mx2, hx, bw - 1)), false));

        String hex = String.format(Locale.ROOT, "#%06X", UiConfig.color(t) & 0xFFFFFF);
        text(ctx, hex, x0, hy + hh + 6, DIM);

        // --- prawa kolumna ---
        final int x1 = x0 + 138, w1 = cW - 138;
        int y = y0;
        text(ctx, "Wygląd", x1, y, sec);
        y += 14;

        String pct = Math.round(UiConfig.panelAlpha * 100) + "%";
        y = sliderRow(ctx, "Przezroczystość panelu", pct, x1, y, w1,
                (float) ((UiConfig.panelAlpha - 0.3) / 0.7),
                (mx2, my2) -> UiConfig.panelAlpha = 0.3 + 0.7 * trackT(mx2, x1, w1), false) + 2;
        y = switchRow(ctx, "Rozpad GUI (Crack)", x1, y, w1, UiConfig.crackClose, "crack", () -> {
            UiConfig.crackClose = !UiConfig.crackClose;
            Sounds.click(UiConfig.crackClose ? 1.4f : 0.9f);
        }, false, mx, my) + 8;

        text(ctx, "Dźwięki", x1, y, sec);
        y += 14;
        y = switchRow(ctx, "Dźwięki GUI", x1, y, w1, UiConfig.guiSounds, "sgui", () -> {
            UiConfig.guiSounds = !UiConfig.guiSounds;
            Sounds.click(1.4f);
        }, false, mx, my);
        y = switchRow(ctx, "Dźwięki modułów", x1, y, w1, UiConfig.moduleSounds, "smod", () -> {
            UiConfig.moduleSounds = !UiConfig.moduleSounds;
            Sounds.click(1.4f);
        }, false, mx, my) + 2;
        y = sliderRow(ctx, "Głośność", Math.round(UiConfig.volume * 100) + "%", x1, y, w1,
                (float) UiConfig.volume,
                (mx2, my2) -> UiConfig.volume = trackT(mx2, x1, w1), false) + 6;

        boolean hov = ready && in(mx, my, x1, y, w1, 18);
        float hv = anim("reset", hov);
        ctx.fill(x1, y, x1 + w1, y + 18, a(lerpColor(0xFF232734, pri, 0.25f + 0.45f * hv)));
        ctx.drawBorder(x1, y, w1, 18, a(0x33FFFFFF));
        String rl = "Reset motywu";
        text(ctx, rl, x1 + w1 / 2 - textRenderer.getWidth(rl) / 2, y + 5, TXT);
        hits.add(new Hit(x1, y, w1, 18, () -> {
            UiConfig.resetTheme();
            Sounds.click(1.0f);
        }, null, null, false));
    }

    // ------------------------------------------------------------------ zamykanie / rozpad

    private void startClose() {
        if (closing) return;
        closing = true;
        closeStart = Util.getMeasuringTimeMs();
        inputFocused = false;
        dragging = null;
        crackMode = UiConfig.crackClose;
        if (crackMode) {
            buildCracks();
            buildShards();
            Sounds.guiCrack();
        } else {
            Sounds.guiClose();
        }
    }

    private void finish() {
        ModuleManager.save();
        if (client != null) client.setScreen(null);
    }

    private void buildCracks() {
        cracks.clear();
        impactX = gx + GW / 2f + (rng.nextFloat() - 0.5f) * 120f;
        impactY = gy + GH / 2f + (rng.nextFloat() - 0.5f) * 80f;
        int n = 11;
        for (int k = 0; k < n; k++) {
            float ang = (float) (k * (2 * Math.PI / n) + (rng.nextFloat() - 0.5f) * 0.5f);
            float px = impactX, py = impactY;
            int segs = 6 + rng.nextInt(3);
            for (int s = 0; s < segs; s++) {
                ang += (rng.nextFloat() - 0.5f) * 0.9f;
                float len = 16f + rng.nextFloat() * 20f;
                float nx = px + (float) Math.cos(ang) * len;
                float ny = py + (float) Math.sin(ang) * len;
                if (nx < gx || nx > gx + GW || ny < gy || ny > gy + GH) break;
                cracks.add(new Crack(px, py, nx, ny, s / (float) segs, (s + 1) / (float) segs));
                px = nx;
                py = ny;
            }
        }
    }

    private void drawCracks(DrawContext ctx, float p) {
        for (Crack c : cracks) {
            if (p <= c.t0()) continue;
            float f = MathHelper.clamp((p - c.t0()) / (c.t1() - c.t0()), 0f, 1f);
            float ex = c.x1() + (c.x2() - c.x1()) * f;
            float ey = c.y1() + (c.y2() - c.y1()) * f;
            Draw.line(ctx, c.x1() + 1, c.y1() + 1, ex + 1, ey + 1, 1.2f, a(0xAA000000));
            Draw.line(ctx, c.x1(), c.y1(), ex, ey, 1.0f, a(0xEEFFFFFF));
        }
        // blysk w miejscu uderzenia
        int flash = (int) (20 * (1f - p));
        ctx.fill((int) impactX - flash, (int) impactY - 1, (int) impactX + flash, (int) impactY + 1, a(0x55FFFFFF));
    }

    private void buildShards() {
        shards.clear();
        int tile = 14;
        int pri = UiConfig.primary(), sec = UiConfig.secondary();
        for (int ty = 0; ty < GH; ty += tile) {
            for (int tx = 0; tx < GW; tx += tile) {
                float cx = gx + tx + tile / 2f, cy = gy + ty + tile / 2f;
                int color;
                if (tx < SIDE) {
                    color = 0xFF07080C;
                } else if (ty < 3) {
                    color = lerpColor(pri, sec, tx / (float) GW);
                } else {
                    int j = rng.nextInt(16);
                    color = 0xFF000000 | ((0x0D + j) << 16) | ((0x0F + j) << 8) | (0x15 + j);
                }
                float vx = (cx - impactX) * 1.5f + (rng.nextFloat() - 0.5f) * 60f;
                float vy = (cy - impactY) * 0.6f - 20f - rng.nextFloat() * 160f;
                shards.add(new Shard(cx, cy, vx, vy, 0f, (rng.nextFloat() - 0.5f) * 12f, color));
            }
        }
    }

    private void renderShards(DrawContext ctx, float t) {
        float fade = 1f - t / FALL_SEC;
        ctx.fill(0, 0, width, height, Draw.withAlpha(0x70000000, fade));
        MatrixStack m = ctx.getMatrices();
        float pa = (float) UiConfig.panelAlpha;
        for (Shard s : shards) {
            float px = s.x() + s.vx() * t;
            float py = s.y() + s.vy() * t + 0.5f * 800f * t * t;
            float rot = s.rot() + s.vrot() * t;
            m.push();
            m.translate(px, py, 0f);
            m.multiply(RotationAxis.POSITIVE_Z.rotation(rot));
            ctx.fill(-7, -7, 7, 7, Draw.withAlpha(0xFF000000, fade * 0.8f));
            ctx.fill(-6, -6, 6, 6, Draw.withAlpha(s.color(), Math.max(0.35f, pa) * fade));
            ctx.fill(-6, -6, 6, -5, Draw.withAlpha(0xFFFFFFFF, 0.25f * fade));
            m.pop();
        }
    }

    // ------------------------------------------------------------------ wejscie

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing || !ready) return true;
        inputFocused = false;
        listeningKey = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx < h.x() || mx >= h.x() + h.w() || my < h.y() || my >= h.y() + h.h()) continue;
            if (h.scrolled() && !in((int) mx, (int) my, cX, cY, cW, cH)) continue;
            if (button == 0 && h.drag() != null) {
                dragging = h.drag();
                dragging.apply(mx, my);
                return true;
            }
            if (button == 0 && h.left() != null) {
                h.left().run();
                return true;
            }
            if (button == 1 && h.right() != null) {
                h.right().run();
                return true;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null && !closing) {
            dragging.apply(mx, my);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            ModuleManager.save();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!closing && !settingsTab()) {
            scrollTarget[tab] -= (float) vertical * 30f;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (listeningKey != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // anuluj
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                listeningKey.code = -1;
                Sounds.click(0.9f);
            } else {
                listeningKey.code = keyCode;
                Sounds.click(1.4f);
            }
            listeningKey = null;
            ModuleManager.save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startClose();
            return true;
        }
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                ModuleManager.addFriend(input.toString());
                input.setLength(0);
                Sounds.click(1.4f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && input.length() > 0) {
                input.setLength(input.length() - 1);
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_P) {
            startClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused && !closing && (Character.isLetterOrDigit(chr) || chr == '_') && input.length() < 16) {
            input.append(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        startClose();
    }
}

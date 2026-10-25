package com.example.dripclient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class Draw {
    private Draw() {
    }

    /** Linia o dowolnej (takze ulamkowej) grubosci: jeden prostokat obrocony i sciesniony macierza. */
    public static void line(DrawContext ctx, double x1, double y1, double x2, double y2, float thickness, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 0.5) return;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate((float) x1, (float) y1, 0f);
        m.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        m.scale(1f, thickness, 1f);
        m.translate(0f, -0.5f, 0f);
        ctx.fill(0, 0, (int) Math.ceil(len), 1, color);
        m.pop();
    }

    public static int withAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }
}

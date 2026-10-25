package com.example.dripclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Rzutuje punkt ze swiata na wspolrzedne ekranu (GUI), zeby rysowac nicki i linie bez zabawy w OpenGL. */
public final class Projection {
    public static final class P {
        public double x, y;
        public boolean behind;
    }

    private Projection() {
    }

    public static P project(MinecraftClient mc, DrawContext ctx, Vec3d world) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());

        // wektory kamery: przod, prawo, gora
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        double dx = world.x - cp.x, dy = world.y - cp.y, dz = world.z - cp.z;
        double cx = dx * rx + dy * ry + dz * rz;
        double cy = dx * ux + dy * uy + dz * uz;
        double cz = dx * fx + dy * fy + dz * fz;

        double w = ctx.getScaledWindowWidth();
        double h = ctx.getScaledWindowHeight();
        double tan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = w / h;

        P p = new P();
        if (cz > 0.05) {
            p.x = (cx / (cz * tan * aspect) + 1.0) / 2.0 * w;
            p.y = (1.0 - cy / (cz * tan)) / 2.0 * h;
            p.behind = false;
        } else {
            // za plecami: kieruj linie w strone, w ktora trzeba sie obrocic
            double len = Math.hypot(cx, cy);
            double nx = len < 1e-6 ? 0 : cx / len;
            double ny = len < 1e-6 ? -1 : cy / len;
            double big = Math.max(w, h);
            p.x = w / 2.0 + nx * big;
            p.y = h / 2.0 - ny * big;
            p.behind = true;
        }
        return p;
    }
}

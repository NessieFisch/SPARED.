package com.myclient.hud;

import com.myclient.ClientConfig;
import com.myclient.music.MusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class HudRenderer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final float[] speedHist = new float[40];
    private static final float[] memHist = new float[40];
    private static double lx, lz;
    private static boolean has;
    private static float speed;
    private static int fps, frames;
    private static long fpsStamp = System.nanoTime();

    private static void push(float[] a, float v) {
        System.arraycopy(a, 1, a, 0, a.length - 1);
        a[a.length - 1] = v;
    }

    /** Called every client tick (20/s). */
    public static void tick(Minecraft mc) {
        if (mc.player == null) { has = false; return; }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (has) speed = (float) (Math.hypot(x - lx, z - lz) * 20.0);
        lx = x; lz = z; has = true;
        push(speedHist, speed);
        Runtime rt = Runtime.getRuntime();
        push(memHist, (rt.totalMemory() - rt.freeMemory()) / (float) rt.maxMemory());
    }

    // ---------- drawing helpers ----------
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (i < r) inset = r - (int) Math.sqrt(r * r - (r - i) * (r - i));
            else if (i >= h - r) { int j = h - 1 - i; inset = r - (int) Math.sqrt(r * r - (r - j) * (r - j)); }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        roundRect(g, x, y, w, h, 4, (accent & 0x00FFFFFF) | 0x88000000);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, 3, 0xB0141006);
    }

    private static void graph(GuiGraphicsExtractor g, int x, int y, int w, int h, float[] hist, float max, int color) {
        int colW = Math.max(1, w / hist.length);
        for (int i = 0; i < hist.length; i++) {
            int bh = (int) Math.min(h, Math.max(1, hist[i] / max * h));
            g.fill(x + i * colW, y + h - bh, x + (i + 1) * colW, y + h, color);
        }
    }

    private static String fit(Font tr, String s, int max) {
        while (s.length() > 1 && tr.width(s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ---------- main render ----------
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfig c = ClientConfig.get();
        Font tr = mc.font;
        frames++;
        long nowNs = System.nanoTime();
        if (nowNs - fpsStamp >= 1_000_000_000L) { fps = frames; frames = 0; fpsStamp = nowNs; }
        int W = g.guiWidth();
        int accent = c.accent;
        int dim = (accent & 0x00FFFFFF) | 0x66000000;

        if (c.showTopBar) {
            String mode = mc.getSingleplayerServer() != null ? "Singleplayer"
                    : (mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Multiplayer");
            String[] parts = { mc.getUser().getName(), fps + " FPS", mode, LocalTime.now().format(FMT) };
            int sep = 12, total = 16 + sep * (parts.length - 1);
            for (String p : parts) total += tr.width(p);
            int x = (W - total) / 2, cx = x + 8;
            panel(g, x, 5, total, 16, accent);
            for (int i = 0; i < parts.length; i++) {
                g.text(tr, parts[i], cx, 9, 0xFFFFFFFF, true);
                cx += tr.width(parts[i]);
                if (i < parts.length - 1) { g.fill(cx + sep / 2, 8, cx + sep / 2 + 1, 18, dim); cx += sep; }
            }
        }

        if (c.showSpeed) {
            panel(g, 8, 28, 80, 28, accent);
            g.text(tr, String.format("Speed - %.1f b/s", speed), 13, 32, 0xFFFFFFFF, true);
            graph(g, 13, 43, 70, 9, speedHist, 12f, accent);
        }

        if (c.showMusic) {
            int mw = 150, mx = (W - mw) / 2;
            panel(g, mx, 26, mw, 34, accent);
            roundRect(g, mx + 5, 31, 24, 24, 3, dim);
            g.text(tr, MusicPlayer.isPlaying() ? "\u266A" : "||", mx + 13, 39, 0xFFFFFFFF, true);
            g.text(tr, fit(tr, MusicPlayer.title(), mw - 42), mx + 34, 31, 0xFFFFFFFF, true);
            g.text(tr, fit(tr, MusicPlayer.artist(), mw - 42), mx + 34, 41, 0xFFAAAAAA, false);
            g.fill(mx + 34, 52, mx + mw - 8, 54, 0x55FFFFFF);
            g.fill(mx + 34, 52, mx + 34 + (int) ((mw - 42) * MusicPlayer.progress()), 54, accent);
        }

        if (c.showCoords && mc.player != null) {
            String s = String.format("x %d  y %d  z %d", (int) Math.floor(mc.player.getX()),
                    (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
            int w = tr.width(s) + 14;
            panel(g, (W - w) / 2, 64, w, 14, accent);
            g.text(tr, s, (W - w) / 2 + 7, 67, 0xFFFFFFFF, true);
        }

        if (c.showMemory) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L, max = rt.maxMemory() / 1048576L;
            panel(g, W - 88, 28, 80, 28, accent);
            g.text(tr, "RAM - " + (used * 100 / max) + "%", W - 83, 32, 0xFFFFFFFF, true);
            graph(g, W - 83, 43, 70, 9, memHist, 1f, accent);
        }
    }
}

package com.myclient.gui;

import com.myclient.ClientConfig;
import com.myclient.hud.HudRenderer;
import com.myclient.music.MusicPlayer;
import com.myclient.sky.CursorRenderer;
import com.myclient.sky.CursorStyle;
import com.myclient.sky.SkyEffect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class ClickGuiScreen extends Screen {
    private static final int PW = 380, PH = 250;
    private static final String[] TABS = { "Sky", "Cursor", "HUD", "Music" };
    private static final int[] COLORS = {
            0xFFE8B931, 0xFFFFFFFF, 0xFFFF5555, 0xFF55FF88, 0xFF55AAFF, 0xFFB066FF, 0xFFFF77CC, 0xFF00E5E5
    };

    private record Hit(int x, int y, int w, int h, DoubleConsumer fn, boolean slider) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Hit dragging;
    private int tab = 0;
    private int trackScroll = 0;

    public ClickGuiScreen() { super(Component.literal("Client")); }

    @Override
    protected void init() {
        // hide the OS cursor, we draw our own
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        ClientConfig.get().save();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- widgets ----------
    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean active, Runnable r) {
        ClientConfig c = ClientConfig.get();
        int bg = active ? ((c.accent & 0x00FFFFFF) | 0xCC000000) : 0xAA222222;
        HudRenderer.roundRect(g, x, y, w, h, 3, bg);
        int tc = active ? 0xFF111111 : 0xFFDDDDDD;
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, tc, false);
        hits.add(new Hit(x, y, w, h, mx -> r.run(), false));
    }

    private void slider(GuiGraphicsExtractor g, String label, int x, int y, int w, float min, float max, float val, Consumer<Float> set) {
        ClientConfig c = ClientConfig.get();
        g.text(font, label + ": " + String.format("%.2f", val), x, y, 0xFFFFFFFF, true);
        int ty = y + 12;
        g.fill(x, ty, x + w, ty + 4, 0xFF333333);
        int fillW = (int) (w * (val - min) / (max - min));
        g.fill(x, ty, x + fillW, ty + 4, c.accent);
        g.fill(x + fillW - 2, ty - 2, x + fillW + 2, ty + 6, 0xFFFFFFFF);
        hits.add(new Hit(x, ty - 4, w, 14, mx -> {
            float t = (float) Math.max(0, Math.min(1, (mx - x) / w));
            set.accept(min + t * (max - min));
        }, true));
    }

    private void swatches(GuiGraphicsExtractor g, int x, int y, int current, Consumer<Integer> set) {
        for (int i = 0; i < COLORS.length; i++) {
            int sx = x + i * 22, col = COLORS[i];
            if (col == current) g.fill(sx - 1, y - 1, sx + 17, y + 17, 0xFFFFFFFF);
            g.fill(sx, y, sx + 16, y + 16, col);
            hits.add(new Hit(sx, y, 16, 16, mx -> set.accept(col), false));
        }
    }

    // ---------- render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();
        ClientConfig c = ClientConfig.get();
        int x0 = (width - PW) / 2, y0 = (height - PH) / 2;

        g.fillGradient(0, 0, width, height, 0x66000000, 0x99000000);
        HudRenderer.roundRect(g, x0 - 1, y0 - 1, PW + 2, PH + 2, 6, (c.accent & 0x00FFFFFF) | 0xAA000000);
        HudRenderer.roundRect(g, x0, y0, PW, PH, 5, 0xF0121008);

        g.text(font, "MY CLIENT", x0 + 12, y0 + 11, c.accent, true);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            button(g, TABS[i], x0 + PW - 12 - (TABS.length - i) * 62 + 6, y0 + 6, 56, 18, tab == i, () -> tab = idx);
        }
        g.fill(x0 + 10, y0 + 28, x0 + PW - 10, y0 + 29, (c.accent & 0x00FFFFFF) | 0x66000000);

        int cx = x0 + 14, cy = y0 + 38;
        switch (tab) {
            case 0 -> skyTab(g, cx, cy);
            case 1 -> cursorTab(g, cx, cy);
            case 2 -> hudTab(g, cx, cy);
            case 3 -> musicTab(g, cx, cy);
        }

        g.text(font, "Right Shift to close", x0 + 12, y0 + PH - 14, 0xFF777777, false);
        CursorRenderer.draw(g, mouseX, mouseY, c.cursor, c.cursorColor, c.cursorSize);
    }

    private void skyTab(GuiGraphicsExtractor g, int x, int y) {
        ClientConfig c = ClientConfig.get();
        g.text(font, "Sky effect", x, y, 0xFFFFFFFF, true);
        SkyEffect[] all = SkyEffect.values();
        for (int i = 0; i < all.length; i++) {
            SkyEffect e = all[i];
            button(g, e.label, x + (i % 2) * 176, y + 14 + (i / 2) * 24, 168, 20, c.sky == e, () -> c.sky = e);
        }
        int sy = y + 14 + ((all.length + 1) / 2) * 24 + 8;
        slider(g, "Intensity", x, sy, 340, 0.2f, 2f, c.skyIntensity, v -> c.skyIntensity = v);
        slider(g, "Tint strength", x, sy + 28, 340, 0f, 1.5f, c.skyTint, v -> c.skyTint = v);
    }

    private void cursorTab(GuiGraphicsExtractor g, int x, int y) {
        ClientConfig c = ClientConfig.get();
        g.text(font, "Style", x, y, 0xFFFFFFFF, true);
        CursorStyle[] all = CursorStyle.values();
        for (int i = 0; i < all.length; i++) {
            CursorStyle s = all[i];
            button(g, s.label, x + i * 86, y + 14, 80, 20, c.cursor == s, () -> c.cursor = s);
        }
        g.text(font, "Color", x, y + 48, 0xFFFFFFFF, true);
        swatches(g, x, y + 62, c.cursorColor, col -> c.cursorColor = col);
        slider(g, "Size", x, y + 90, 340, 0.5f, 3f, c.cursorSize, v -> c.cursorSize = v);
        button(g, "Custom crosshair in-game: " + (c.customCrosshair ? "ON" : "OFF"), x, y + 124, 200, 20,
                c.customCrosshair, () -> c.customCrosshair = !c.customCrosshair);
        g.text(font, "Preview:", x, y + 156, 0xFFAAAAAA, false);
        CursorRenderer.draw(g, x + 70, y + 160, c.cursor, c.cursorColor, c.cursorSize);
    }

    private void hudTab(GuiGraphicsExtractor g, int x, int y) {
        ClientConfig c = ClientConfig.get();
        g.text(font, "Accent color", x, y, 0xFFFFFFFF, true);
        swatches(g, x, y + 14, c.accent, col -> c.accent = col);
        g.text(font, "Elements", x, y + 42, 0xFFFFFFFF, true);
        button(g, "Top bar", x, y + 56, 110, 20, c.showTopBar, () -> c.showTopBar = !c.showTopBar);
        button(g, "Speed", x + 116, y + 56, 110, 20, c.showSpeed, () -> c.showSpeed = !c.showSpeed);
        button(g, "Music", x + 232, y + 56, 110, 20, c.showMusic, () -> c.showMusic = !c.showMusic);
        button(g, "Coords", x, y + 82, 110, 20, c.showCoords, () -> c.showCoords = !c.showCoords);
        button(g, "RAM", x + 116, y + 82, 110, 20, c.showMemory, () -> c.showMemory = !c.showMemory);
        g.text(font, "Track text: edit trackTitle / trackArtist in", x, y + 118, 0xFFAAAAAA, false);
        g.text(font, "config/myclient.json", x, y + 130, 0xFFAAAAAA, false);
    }

    private void musicTab(GuiGraphicsExtractor g, int x, int y) {
        ClientConfig c = ClientConfig.get();
        g.text(font, "Put .mp3 / .wav files in  .minecraft/myclient_music  then press Rescan", x, y, 0xFFAAAAAA, false);
        button(g, "|<", x, y + 14, 50, 20, false, MusicPlayer::prev);
        button(g, MusicPlayer.isPlaying() ? "Pause" : "Play", x + 56, y + 14, 80, 20, MusicPlayer.isPlaying(), MusicPlayer::toggle);
        button(g, ">|", x + 142, y + 14, 50, 20, false, MusicPlayer::next);
        button(g, "Rescan", x + 198, y + 14, 70, 20, false, () -> { MusicPlayer.rescan(); trackScroll = 0; });
        slider(g, "Volume", x, y + 42, 340, 0f, 1f, c.musicVolume, v -> { c.musicVolume = v; MusicPlayer.setVolume(v); });

        java.util.List<String> names = MusicPlayer.names();
        if (names.isEmpty()) {
            g.text(font, "No songs found yet.", x, y + 78, 0xFFFFFFFF, true);
            return;
        }
        trackScroll = Math.max(0, Math.min(trackScroll, names.size() - 1));
        for (int i = 0; i < 5; i++) {
            final int k = trackScroll + i;
            if (k >= names.size()) break;
            String n = names.get(k);
            while (n.length() > 1 && font.width(n) > 270) n = n.substring(0, n.length() - 1);
            button(g, n, x, y + 74 + i * 22, 290, 20, k == MusicPlayer.currentIndex(), () -> MusicPlayer.play(k));
        }
        button(g, "^", x + 296, y + 74, 44, 20, false, () -> trackScroll = Math.max(0, trackScroll - 1));
        button(g, "v", x + 296, y + 98, 44, 20, false, () -> trackScroll++);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) {
                h.fn().accept(mx);
                if (h.slider()) dragging = h;
                return true;
            }
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) { dragging.fn().accept(e.x()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        return super.mouseReleased(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; }
        return super.keyPressed(e);
    }
}

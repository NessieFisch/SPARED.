package com.myclient.hud;

import com.myclient.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Lunar-style extras: keystrokes, CPS, armor, potions, ping. */
public final class ModHud {
    private static final ArrayDeque<Long> leftClicks = new ArrayDeque<>(), rightClicks = new ArrayDeque<>();
    private static boolean lastL, lastR;
    private static final String[] ROMAN = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };

    private static int cps(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static void key(GuiGraphicsExtractor g, Font tr, int x, int y, int w, int h, String label, boolean down, int accent) {
        int bg = down ? ((accent & 0x00FFFFFF) | 0xDD000000) : 0x99000000;
        HudRenderer.roundRect(g, x, y, w, h, 3, bg);
        g.text(tr, label, x + (w - tr.width(label)) / 2, y + (h - 8) / 2, down ? 0xFF111111 : 0xFFFFFFFF, false);
    }

    public static void render(GuiGraphicsExtractor g, Minecraft mc, ClientConfig c) {
        if (mc.player == null) return;
        Font tr = mc.font;
        int W = g.guiWidth(), H = g.guiHeight(), accent = c.accent;
        long now = System.currentTimeMillis();

        boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
        if (l && !lastL) leftClicks.addLast(now);
        if (r && !lastR) rightClicks.addLast(now);
        lastL = l; lastR = r;
        int cl = cps(leftClicks, now), cr = cps(rightClicks, now);

        // ---- keystrokes (bottom-left) ----
        if (c.showKeys) {
            int x = 8, y = H - 96;
            key(g, tr, x + 24, y, 22, 22, "W", mc.options.keyUp.isDown(), accent);
            key(g, tr, x, y + 24, 22, 22, "A", mc.options.keyLeft.isDown(), accent);
            key(g, tr, x + 24, y + 24, 22, 22, "S", mc.options.keyDown.isDown(), accent);
            key(g, tr, x + 48, y + 24, 22, 22, "D", mc.options.keyRight.isDown(), accent);
            key(g, tr, x, y + 48, 34, 22, "", l, accent);
            key(g, tr, x + 36, y + 48, 34, 22, "", r, accent);
            int lc = l ? 0xFF111111 : 0xFFFFFFFF, rc = r ? 0xFF111111 : 0xFFFFFFFF;
            g.text(tr, "LMB", x + (34 - tr.width("LMB")) / 2, y + 51, lc, false);
            g.text(tr, "" + cl, x + (34 - tr.width("" + cl)) / 2, y + 61, lc, false);
            g.text(tr, "RMB", x + 36 + (34 - tr.width("RMB")) / 2, y + 51, rc, false);
            g.text(tr, "" + cr, x + 36 + (34 - tr.width("" + cr)) / 2, y + 61, rc, false);
            key(g, tr, x, y + 72, 70, 12, "", mc.options.keyJump.isDown(), accent);
            boolean j = mc.options.keyJump.isDown();
            g.fill(x + 22, y + 77, x + 48, y + 79, j ? 0xFF111111 : 0xFFFFFFFF);
        }

        // ---- CPS panel ----
        if (c.showCps) {
            HudRenderer.panel(g, 8, 60, 80, 16, accent);
            g.text(tr, "CPS " + cl + " | " + cr, 14, 64, 0xFFFFFFFF, true);
        }

        // ---- ping ----
        if (c.showPing) {
            int ping = 0;
            var conn = mc.getConnection();
            if (conn != null) {
                var info = conn.getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency();
            }
            HudRenderer.panel(g, W - 88, 60, 80, 16, accent);
            g.text(tr, "Ping " + ping + " ms", W - 82, 64, 0xFFFFFFFF, true);
        }

        // ---- armor status (right side) ----
        if (c.showArmor) {
            EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            String[] names = { "Helm", "Chest", "Legs", "Boots" };
            List<String> lines = new ArrayList<>();
            List<Integer> cols = new ArrayList<>();
            for (int i = 0; i < slots.length; i++) {
                ItemStack s = mc.player.getItemBySlot(slots[i]);
                if (s.isEmpty()) continue;
                int max = s.getMaxDamage();
                if (max > 0) {
                    int left = max - s.getDamageValue();
                    int pct = left * 100 / max;
                    lines.add(names[i] + " " + pct + "%");
                    cols.add(pct > 50 ? 0xFF55FF55 : pct > 25 ? 0xFFFFFF55 : 0xFFFF5555);
                } else {
                    lines.add(names[i] + " -");
                    cols.add(0xFFFFFFFF);
                }
            }
            if (!lines.isEmpty()) {
                int w = 74, h = lines.size() * 11 + 8, x = W - w - 8, y = H / 2 - h / 2;
                HudRenderer.panel(g, x, y, w, h, accent);
                for (int i = 0; i < lines.size(); i++) g.text(tr, lines.get(i), x + 7, y + 5 + i * 11, cols.get(i), true);
            }
        }

        // ---- potion effects (left side) ----
        if (c.showPotions) {
            List<String> lines = new ArrayList<>();
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                String name = e.getEffect().value().getDisplayName().getString();
                int amp = e.getAmplifier() + 1;
                if (amp > 1) name += " " + (amp < ROMAN.length ? ROMAN[amp] : "" + amp);
                String time;
                if (e.isInfiniteDuration()) time = "inf";
                else { int sec = e.getDuration() / 20; time = (sec / 60) + ":" + String.format("%02d", sec % 60); }
                lines.add(name + "  " + time);
                if (lines.size() >= 6) break;
            }
            if (!lines.isEmpty()) {
                int w = 20;
                for (String s : lines) w = Math.max(w, tr.width(s) + 14);
                int h = lines.size() * 11 + 8, x = 8, y = 82;
                HudRenderer.panel(g, x, y, w, h, accent);
                for (int i = 0; i < lines.size(); i++) g.text(tr, lines.get(i), x + 7, y + 5 + i * 11, 0xFFFFFFFF, true);
            }
        }
    }
}

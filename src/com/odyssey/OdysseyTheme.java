package com.odyssey;

import com.badlogic.gdx.graphics.Color;

public final class OdysseyTheme {
    private OdysseyTheme() {}

    // ── Base palette ──
    public static final Color SPACE_BG     = new Color(0.031f, 0.031f, 0.063f, 1f);  // #080810
    public static final Color PANEL_BG     = new Color(0.043f, 0.051f, 0.098f, 1f);  // #0B0D1A
    public static final Color PANEL_BORDER = new Color(0.071f, 0.094f, 0.165f, 1f);  // #12182A

    public static final Color ACCENT_E     = new Color(0.133f, 0.333f, 0.800f, 1f);  // #2255CC
    public static final Color ACCENT_E_DIM = new Color(0.067f, 0.165f, 0.400f, 1f);  // #112266
    public static final Color ACCENT_SP    = new Color(0.800f, 0.600f, 0.000f, 1f);  // #CC9900
    public static final Color ACCENT_GO    = new Color(0.000f, 1.000f, 0.267f, 1f);  // #00FF44
    public static final Color ACCENT_GO_DIM= new Color(0.000f, 0.400f, 0.107f, 1f);  // #006633
    public static final Color ACCENT_WARN  = new Color(1.000f, 0.420f, 0.000f, 1f);  // #FF6B00

    public static final Color TEXT_PRI     = new Color(0.784f, 0.847f, 0.941f, 1f);  // #C8D8F0
    public static final Color TEXT_DIM     = new Color(0.227f, 0.290f, 0.416f, 1f);  // #3A4A6A
    public static final Color TEXT_INVIS   = new Color(0.071f, 0.094f, 0.165f, 1f);  // matches panel

    // ── Button state colors (apply via TextButton.setColor()) ──
    // These are TINTS applied to the white base drawable.
    // Use setColor() on the button — the drawable stays white, tint produces the final color.
    public static final Color BTN_LOCKED    = new Color(0.06f, 0.06f, 0.12f, 1f);    // near-black
    public static final Color BTN_AVAILABLE = new Color(0.20f, 0.22f, 0.38f, 1f);    // visible dark grey-blue
    public static final Color BTN_BUYABLE   = new Color(0.13f, 0.30f, 0.72f, 1f);    // clear blue
    public static final Color BTN_ACTIVE    = new Color(0.22f, 0.42f, 0.88f, 1f);    // bright blue
    public static final Color BTN_GO        = new Color(0.04f, 0.52f, 0.20f, 1f);    // vivid green
    public static final Color BTN_GO_LOCKED = new Color(0.06f, 0.18f, 0.09f, 1f);    // dark green

    // ── Float number colors (by source) ──
    public static final Color FLOAT_SP      = new Color(0.800f, 0.600f, 0.000f, 1f);  // gold — SP
    public static final Color FLOAT_E       = new Color(0.267f, 0.533f, 1.000f, 1f);  // blue — Energy
    public static final Color FLOAT_SPECIAL = new Color(0.000f, 0.800f, 0.667f, 1f);  // teal — gravity/special
    public static final Color FLOAT_BUMPER  = new Color(0.600f, 0.400f, 1.000f, 1f);  // violet — bumper/attractor
    public static final Color FLOAT_HARVEST = new Color(1.000f, 0.850f, 0.100f, 1f);  // gold — harvest joules
    public static final Color FLOAT_GEMS    = new Color(0.350f, 1.000f, 0.900f, 1f);  // cyan — harvest gems
}
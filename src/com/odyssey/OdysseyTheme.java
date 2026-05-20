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
    public static final Color BTN_LOCKED    = new Color(0.031f, 0.031f, 0.063f, 1f);  // nearly invisible
    public static final Color BTN_AVAILABLE = new Color(0.12f, 0.14f, 0.22f, 1f);    // dim blue-grey
    public static final Color BTN_BUYABLE   = new Color(0.16f, 0.24f, 0.48f, 1f);    // blue tint
    public static final Color BTN_ACTIVE    = new Color(0.20f, 0.35f, 0.70f, 1f);    // brighter blue
    public static final Color BTN_GO        = new Color(0.00f, 0.35f, 0.14f, 1f);    // dark green
    public static final Color BTN_GO_LOCKED = new Color(0.03f, 0.10f, 0.04f, 1f);    // near-black green

    // ── Float number colors (by source) ──
    public static final Color FLOAT_SP      = new Color(0.800f, 0.600f, 0.000f, 1f);  // gold — SP
    public static final Color FLOAT_E       = new Color(0.267f, 0.533f, 1.000f, 1f);  // blue — Energy
    public static final Color FLOAT_SPECIAL = new Color(0.000f, 0.800f, 0.667f, 1f);  // teal — gravity/special
    public static final Color FLOAT_BUMPER  = new Color(0.600f, 0.400f, 1.000f, 1f);  // violet — bumper/attractor
}
package com.cosmiclogic.dbshots;

/** While true, respawn requests are blocked, so the real death screen stays open for the pictures. */
public final class Hold {
    public static volatile boolean death;

    private Hold() {}
}

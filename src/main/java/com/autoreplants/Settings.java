package com.autoreplants;

public class Settings {
    public boolean enabled = true;
    public boolean requireHoe = true;
    public boolean matureOnly = false;
    public int replantDelay = 2;
    public boolean sneakBypass = false;

    public Settings copy() {
        Settings copy = new Settings();
        copy.enabled = enabled;
        copy.requireHoe = requireHoe;
        copy.matureOnly = matureOnly;
        copy.replantDelay = replantDelay;
        copy.sneakBypass = sneakBypass;
        return copy;
    }

    public void validate() {
        replantDelay = Math.clamp(replantDelay, 0, 10);
    }
}

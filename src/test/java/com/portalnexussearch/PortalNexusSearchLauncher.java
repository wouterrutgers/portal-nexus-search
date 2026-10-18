package com.portalnexussearch;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PortalNexusSearchLauncher {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(PortalNexusSearchPlugin.class);
        RuneLite.main(args);
    }
}

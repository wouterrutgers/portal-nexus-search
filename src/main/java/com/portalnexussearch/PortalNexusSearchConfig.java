package com.portalnexussearch;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.util.OSType;

@ConfigGroup("portalnexussearch")
public interface PortalNexusSearchConfig extends Config {
    @ConfigItem(
            keyName = "searchHotkey",
            name = "Search hotkey",
            description = "Open or close search while the portal nexus teleport menu is open")
    default Keybind searchHotkey() {
        return new Keybind(
                KeyEvent.VK_F,
                OSType.getOSType() == OSType.MacOS ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);
    }
}

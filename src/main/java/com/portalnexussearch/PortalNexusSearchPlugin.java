package com.portalnexussearch;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@PluginDescriptor(
        name = "Portal nexus search",
        description = "Search the destinations in your POH portal nexus using a configurable hotkey",
        tags = {"poh", "house", "portal", "nexus", "teleport", "search", "filter", "hotkey"})
public class PortalNexusSearchPlugin extends Plugin {
    @Inject
    protected Client client;

    @Inject
    protected ClientThread clientThread;

    @Inject
    protected KeyManager keyManager;

    @Inject
    protected ChatboxPanelManager chatboxPanelManager;

    @Inject
    protected PortalNexusSearchConfig config;

    @Inject
    protected PluginManager pluginManager;

    protected final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.searchHotkey()) {
        @Override
        public void keyPressed(KeyEvent event) {
            if (nexusOpen) {
                super.keyPressed(event);
            }
        }

        @Override
        public void hotkeyPressed() {
            clientThread.invokeLater(PortalNexusSearchPlugin.this::toggleSearch);
        }
    };

    protected final List<DestinationRow> rows = new ArrayList<>();
    protected volatile boolean nexusOpen;
    protected ChatboxTextInput searchInput;
    protected Plugin teleportMenuPlugin;
    protected Widget scrolling;
    protected int originalScrollHeight;
    protected int originalScrollY;

    @Provides
    protected PortalNexusSearchConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PortalNexusSearchConfig.class);
    }

    @Override
    protected void startUp() {
        keyManager.registerKeyListener(hotkeyListener);
    }

    @Override
    protected void shutDown() {
        nexusOpen = false;
        keyManager.unregisterKeyListener(hotkeyListener);
        hotkeyListener.focusLost();
        clientThread.invokeLater(() -> {
            closeSearch();
            restoreRows();
        });
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        Widget currentScrolling = client.getWidget(InterfaceID.TelenexusTeleport.SCROLLING1);
        nexusOpen = currentScrolling != null && !currentScrolling.isHidden();
        if (searchInput != null && (!nexusOpen || currentScrolling != scrolling)) {
            closeSearch();
        }
    }

    protected void toggleSearch() {
        if (!nexusOpen) {
            return;
        }

        if (searchInput != null) {
            closeSearch();
            return;
        }

        scrolling = client.getWidget(InterfaceID.TelenexusTeleport.SCROLLING1);
        if (scrolling == null || scrolling.isHidden()) {
            return;
        }

        originalScrollHeight = scrolling.getScrollHeight();
        originalScrollY = scrolling.getScrollY();

        for (Plugin plugin : pluginManager.getPlugins()) {
            if (!plugin.getName().equals("Better Teleport Menu") || !pluginManager.isPluginActive(plugin)) {
                continue;
            }

            teleportMenuPlugin = plugin;
            keyManager.unregisterKeyListener((KeyListener) plugin);
            break;
        }

        captureRows(
                InterfaceID.TelenexusTeleport.TEXT1,
                InterfaceID.TelenexusTeleport.ROWS1,
                InterfaceID.TelenexusTeleport.GFX1,
                InterfaceID.TelenexusTeleport.KEY_LISTENERS);
        captureRows(
                InterfaceID.TelenexusTeleport.EXTRAS,
                InterfaceID.TelenexusTeleport.ROWS2,
                InterfaceID.TelenexusTeleport.GFX2,
                InterfaceID.TelenexusTeleport.EXTRA_KEY_LISTENERS);
        rows.sort(Comparator.comparingInt(row -> row.originalY));

        searchInput = chatboxPanelManager
                .openTextInput("Search portal nexus")
                .onChanged(value -> clientThread.invokeLater(this::filterRows))
                .onDone(value -> false)
                .onClose(this::restoreRows)
                .build();
    }

    protected void captureRows(int textLayer, int backgroundLayer, int iconLayer, int keyLayer) {
        Widget[] children = client.getWidget(textLayer).getChildren();
        if (children == null) {
            return;
        }

        for (Widget text : children) {
            if (text.isSelfHidden()) {
                continue;
            }

            rows.add(new DestinationRow(
                    text,
                    client.getWidget(backgroundLayer).getChild(text.getIndex()),
                    client.getWidget(iconLayer).getChild(text.getIndex()),
                    client.getWidget(keyLayer).getChild(text.getIndex())));
        }
    }

    protected void filterRows() {
        if (searchInput == null) {
            return;
        }

        String query = searchInput.getValue().strip().toLowerCase(Locale.ROOT);
        int height = 0;
        for (DestinationRow row : rows) {
            boolean matches = row.name.contains(query);
            row.filter(matches, height);
            if (matches) {
                height += row.text.getOriginalHeight();
            }
        }

        scrolling.setScrollHeight(height);
        updateScrollbar(0);
    }

    protected void closeSearch() {
        if (searchInput != null && chatboxPanelManager.getCurrentInput() == searchInput) {
            chatboxPanelManager.close();
        }
    }

    protected void restoreRows() {
        searchInput = null;
        for (DestinationRow row : rows) {
            row.restore();
        }
        rows.clear();

        if (scrolling != null) {
            scrolling.setScrollHeight(originalScrollHeight);
            updateScrollbar(originalScrollY);
            scrolling = null;
        }

        if (teleportMenuPlugin != null && pluginManager.isPluginActive(teleportMenuPlugin)) {
            keyManager.registerKeyListener((KeyListener) teleportMenuPlugin);
        }
        teleportMenuPlugin = null;
    }

    protected void updateScrollbar(int scrollY) {
        scrolling.setScrollY(scrollY);
        if (client.getWidget(InterfaceID.TelenexusTeleport.SCROLLING1) != scrolling || scrolling.isHidden()) {
            return;
        }

        client.runScript(
                ScriptID.UPDATE_SCROLLBAR,
                InterfaceID.TelenexusTeleport.SCROLLBAR1,
                InterfaceID.TelenexusTeleport.SCROLLING1,
                scrollY);
    }

    protected static class DestinationRow {
        protected final Widget text;
        protected final Widget background;
        protected final Widget icon;
        protected final Widget keyListener;
        protected final Object[] originalKeyListener;
        protected final int originalY;
        protected final String name;

        protected DestinationRow(Widget text, Widget background, Widget icon, Widget keyListener) {
            this.text = text;
            this.background = background;
            this.icon = icon;
            this.keyListener = keyListener;
            originalY = text.getOriginalY();
            name = Text.removeTags(text.getText())
                    .replaceFirst("^[^:]*:\\s*", "")
                    .toLowerCase(Locale.ROOT);
            originalKeyListener = keyListener.getOnKeyListener();
            keyListener.setOnKeyListener((Object[]) null);
        }

        protected void filter(boolean matches, int y) {
            for (Widget widget : new Widget[] {text, background, icon}) {
                widget.setHidden(!matches);
                if (matches) {
                    widget.setOriginalY(y);
                    widget.revalidate();
                }
            }
        }

        protected void restore() {
            filter(true, originalY);
            keyListener.setOnKeyListener(originalKeyListener);
        }
    }
}

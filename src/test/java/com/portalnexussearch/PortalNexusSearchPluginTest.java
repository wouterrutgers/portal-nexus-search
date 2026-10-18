package com.portalnexussearch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.google.inject.Guice;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.junit.Before;
import org.junit.Test;

public class PortalNexusSearchPluginTest {
    protected final PortalNexusSearchPlugin plugin = new PortalNexusSearchPlugin();
    protected final Widget scrolling = mock(Widget.class);
    protected final ChatboxTextInput input = mock(ChatboxTextInput.class, RETURNS_SELF);
    protected final Object[] mainShortcut = {2685, 0};
    protected final Object[] alternativeShortcut = {2685, 1};
    protected Widget[] mainRow;
    protected Widget[] alternativeRow;
    protected Consumer<String> onChanged;
    protected Runnable onClose;

    @Before
    public void setUp() {
        plugin.client = mock(Client.class);
        plugin.clientThread = mock(ClientThread.class);
        plugin.keyManager = mock(KeyManager.class);
        plugin.chatboxPanelManager = mock(ChatboxPanelManager.class);
        plugin.config = mock(PortalNexusSearchConfig.class);
        plugin.pluginManager = mock(PluginManager.class);
        when(plugin.config.searchHotkey()).thenReturn(new Keybind(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        doAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                })
                .when(plugin.clientThread)
                .invokeLater(any(Runnable.class));

        when(plugin.client.getWidget(InterfaceID.TelenexusTeleport.SCROLLING1)).thenReturn(scrolling);
        when(scrolling.getScrollHeight()).thenReturn(600);
        when(scrolling.getScrollY()).thenReturn(120);
        when(plugin.chatboxPanelManager.openTextInput(anyString())).thenReturn(input);
        when(plugin.chatboxPanelManager.getCurrentInput()).thenReturn(input);
        doAnswer(invocation -> {
                    onChanged = invocation.getArgument(0);
                    return input;
                })
                .when(input)
                .onChanged(any());
        doAnswer(invocation -> {
                    onClose = invocation.getArgument(0);
                    return input;
                })
                .when(input)
                .onClose(any());
        doAnswer(invocation -> {
                    onClose.run();
                    return null;
                })
                .when(plugin.chatboxPanelManager)
                .close();

        mainRow = createRow(
                InterfaceID.TelenexusTeleport.TEXT1,
                InterfaceID.TelenexusTeleport.ROWS1,
                InterfaceID.TelenexusTeleport.GFX1,
                InterfaceID.TelenexusTeleport.KEY_LISTENERS,
                "<col=ffffff>1</col> :  Varrock",
                0,
                mainShortcut);
        alternativeRow = createRow(
                InterfaceID.TelenexusTeleport.EXTRAS,
                InterfaceID.TelenexusTeleport.ROWS2,
                InterfaceID.TelenexusTeleport.GFX2,
                InterfaceID.TelenexusTeleport.EXTRA_KEY_LISTENERS,
                "<col=ffffff>2</col> :  Grand Exchange",
                24,
                alternativeShortcut);
    }

    @Test
    public void searchDisablesDestinationShortcutsAndRestoresTheMenuOnClose() {
        plugin.onClientTick(new ClientTick());
        plugin.hotkeyListener.keyPressed(searchHotkey());

        verify(mainRow[3]).setOnKeyListener((Object[]) null);
        verify(alternativeRow[3]).setOnKeyListener((Object[]) null);

        when(input.getValue()).thenReturn(" exchange ");
        onChanged.accept(input.getValue());
        for (int index = 0; index < 3; index++) {
            verify(mainRow[index]).setHidden(true);
            verify(alternativeRow[index]).setHidden(false);
            verify(alternativeRow[index]).setOriginalY(0);
        }

        onClose.run();
        verify(mainRow[3]).setOnKeyListener(mainShortcut);
        verify(alternativeRow[3]).setOnKeyListener(alternativeShortcut);
        verify(mainRow[0]).setHidden(false);
        verify(alternativeRow[0]).setOriginalY(24);
        verify(scrolling).setScrollHeight(600);
        verify(scrolling).setScrollY(120);
    }

    @Test
    public void hotkeyDoesNotConsumeInputOutsideTheNexus() {
        when(scrolling.isHidden()).thenReturn(true);
        plugin.onClientTick(new ClientTick());

        KeyEvent event = searchHotkey();
        plugin.hotkeyListener.keyPressed(event);

        verify(event, never()).consume();
        verify(plugin.chatboxPanelManager, never()).openTextInput(anyString());
    }

    @Test
    public void disablingThePluginClosesSearchAndRestoresShortcuts() {
        plugin.startUp();
        plugin.onClientTick(new ClientTick());
        plugin.hotkeyListener.keyPressed(searchHotkey());

        plugin.shutDown();

        verify(plugin.chatboxPanelManager).close();
        verify(mainRow[3]).setOnKeyListener(mainShortcut);
        verify(alternativeRow[3]).setOnKeyListener(alternativeShortcut);
        verify(plugin.keyManager).unregisterKeyListener(plugin.hotkeyListener);
    }

    @Test
    public void searchPausesBetterTeleportMenuHotkeysUntilItCloses() {
        Plugin teleportMenu = mock(Plugin.class, withSettings().extraInterfaces(KeyListener.class));
        when(teleportMenu.getName()).thenReturn("Better Teleport Menu");
        when(plugin.pluginManager.getPlugins()).thenReturn(Collections.singletonList(teleportMenu));
        when(plugin.pluginManager.isPluginActive(teleportMenu)).thenReturn(true);
        plugin.keyManager = Guice.createInjector(binder -> {
                    binder.bind(Client.class).toInstance(plugin.client);
                    binder.bind(EventBus.class).toInstance(new EventBus());
                })
                .getInstance(KeyManager.class);
        plugin.keyManager.registerKeyListener((KeyListener) teleportMenu);
        plugin.startUp();
        plugin.onClientTick(new ClientTick());
        plugin.hotkeyListener.keyPressed(searchHotkey());

        KeyEvent event = mock(KeyEvent.class);
        when(event.getExtendedKeyCode()).thenReturn(KeyEvent.VK_G);
        when(event.getKeyCode()).thenReturn(KeyEvent.VK_G);
        when(event.getKeyChar()).thenReturn('g');
        plugin.keyManager.registerKeyListener(input);
        plugin.keyManager.processKeyPressed(event);

        verify((KeyListener) teleportMenu, never()).keyPressed(event);
        verify(input).keyPressed(event);

        onClose.run();
        plugin.keyManager.processKeyPressed(event);

        verify((KeyListener) teleportMenu).keyPressed(event);
    }

    protected KeyEvent searchHotkey() {
        KeyEvent event = mock(KeyEvent.class);
        when(event.getExtendedKeyCode()).thenReturn(KeyEvent.VK_F);
        when(event.getKeyCode()).thenReturn(KeyEvent.VK_F);
        when(event.getModifiersEx()).thenReturn(InputEvent.CTRL_DOWN_MASK);
        return event;
    }

    protected Widget[] createRow(
            int textLayer, int backgroundLayer, int iconLayer, int keyLayer, String name, int y, Object[] shortcut) {
        Widget[] row = {mock(Widget.class), mock(Widget.class), mock(Widget.class), mock(Widget.class)};
        Widget textParent = mock(Widget.class);
        when(plugin.client.getWidget(textLayer)).thenReturn(textParent);
        when(textParent.getChildren()).thenReturn(new Widget[] {row[0]});
        when(row[0].getText()).thenReturn(name);
        when(row[0].getOriginalY()).thenReturn(y);
        when(row[0].getOriginalHeight()).thenReturn(24);
        when(row[3].getOnKeyListener()).thenReturn(shortcut);

        int[] layers = {backgroundLayer, iconLayer, keyLayer};
        for (int index = 0; index < layers.length; index++) {
            Widget parent = mock(Widget.class);
            when(plugin.client.getWidget(layers[index])).thenReturn(parent);
            when(parent.getChild(0)).thenReturn(row[index + 1]);
        }
        return row;
    }
}

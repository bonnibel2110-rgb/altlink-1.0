package com.example.altlink;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.logging.Level;

/**
 * Intercepts the player login process. If the connecting account's real UUID
 * is registered as a linked "alt" account, its UUID is replaced with the
 * linked "main" account's UUID before the rest of the server processes the login.
 *
 * As a result, the server (and all other plugins) treat the alt account as if
 * it were the main account: shared inventory, playerdata, permissions
 * (permission plugins key by UUID), protections/claims, and statistics.
 */
public final class PreLoginListener implements Listener {

    private final AltLinkPlugin plugin;
    private final LinkStore linkStore;

    public PreLoginListener(AltLinkPlugin plugin, LinkStore linkStore) {
        this.plugin = plugin;
        this.linkStore = linkStore;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        var linkOpt = linkStore.getLinkByAltUuid(event.getUniqueId());
        if (linkOpt.isEmpty()) {
            return;
        }

        var link = linkOpt.get();

        plugin.getLogger().log(Level.INFO, "Spoofing UUID for alt '" + event.getName()
                + "' (" + event.getUniqueId() + ") -> main UUID " + link.mainUuid()
                + " (" + link.mainName() + ")");

        // Replace the UUID the server will use for this connection with the main account's UUID.
        event.setPlayerProfile(event.getPlayerProfile().withUniqueId(link.mainUuid()));
    }
}

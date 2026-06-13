package com.example.altlink;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persists alt-account links to links.yml.
 *
 * Structure of links.yml:
 *
 * links:
 *   <alt-uuid-as-string>:
 *     main-uuid: <main-uuid-as-string>
 *     main-name: <main account name at time of linking>
 *     alt-name: <alt account name at time of linking>
 *
 * The alt UUID is the *real* UUID the alt account would normally connect with.
 * When that real UUID is seen in AsyncPlayerPreLoginEvent, it is replaced
 * with the main account's UUID, so the server treats the alt as the main.
 */
public final class LinkStore {

    private final AltLinkPlugin plugin;
    private final File file;

    // realAltUuid -> LinkEntry
    private final Map<UUID, LinkEntry> linksByAltUuid = new ConcurrentHashMap<>();

    public LinkStore(AltLinkPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "links.yml");
    }

    public record LinkEntry(UUID mainUuid, String mainName, UUID altUuid, String altName) {
    }

    public synchronized void load() {
        linksByAltUuid.clear();

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            // Nothing to load yet; will be created on first save.
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        var linksSection = config.getConfigurationSection("links");
        if (linksSection == null) {
            return;
        }

        for (String altUuidString : linksSection.getKeys(false)) {
            try {
                UUID altUuid = UUID.fromString(altUuidString);
                String mainUuidString = linksSection.getString(altUuidString + ".main-uuid");
                if (mainUuidString == null) {
                    continue;
                }
                UUID mainUuid = UUID.fromString(mainUuidString);
                String mainName = linksSection.getString(altUuidString + ".main-name", "");
                String altName = linksSection.getString(altUuidString + ".alt-name", "");

                linksByAltUuid.put(altUuid, new LinkEntry(mainUuid, mainName, altUuid, altName));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid entry in links.yml: " + altUuidString, ex);
            }
        }
    }

    public synchronized void save() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration config = new YamlConfiguration();
        Map<String, Object> root = new HashMap<>();

        for (LinkEntry entry : linksByAltUuid.values()) {
            String path = "links." + entry.altUuid().toString();
            config.set(path + ".main-uuid", entry.mainUuid().toString());
            config.set(path + ".main-name", entry.mainName());
            config.set(path + ".alt-name", entry.altName());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save links.yml", e);
        }
    }

    /**
     * Adds (or replaces) a link entry and persists immediately.
     */
    public synchronized void addLink(UUID mainUuid, String mainName, UUID altUuid, String altName) {
        linksByAltUuid.put(altUuid, new LinkEntry(mainUuid, mainName, altUuid, altName));
        save();
    }

    /**
     * Removes the link entry associated with the given alt UUID, if any.
     */
    public synchronized boolean removeLinkByAltUuid(UUID altUuid) {
        boolean removed = linksByAltUuid.remove(altUuid) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Looks up a link entry by the alt account's real UUID.
     */
    public Optional<LinkEntry> getLinkByAltUuid(UUID altUuid) {
        return Optional.ofNullable(linksByAltUuid.get(altUuid));
    }

    /**
     * Returns true if the given UUID is already registered as a linked alt account.
     */
    public boolean isAlt(UUID uuid) {
        return linksByAltUuid.containsKey(uuid);
    }

    /**
     * Returns true if the given UUID is currently used as a "main" target by any link.
     */
    public boolean isMain(UUID uuid) {
        for (LinkEntry entry : linksByAltUuid.values()) {
            if (entry.mainUuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return linksByAltUuid.size();
    }
}

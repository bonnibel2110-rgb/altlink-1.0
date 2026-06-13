package com.example.altlink;

import org.bukkit.plugin.java.JavaPlugin;

public final class AltLinkPlugin extends JavaPlugin {

    private LinkStore linkStore;
    private PendingLinkManager pendingLinkManager;

    @Override
    public void onEnable() {
        // Load (or create) links.yml storage
        this.linkStore = new LinkStore(this);
        this.linkStore.load();

        // In-memory manager for pending confirmation codes
        this.pendingLinkManager = new PendingLinkManager();

        // Register the /link command
        LinkCommand linkCommand = new LinkCommand(this, linkStore, pendingLinkManager);
        getCommand("link").setExecutor(linkCommand);
        getCommand("link").setTabCompleter(linkCommand);

        // Register the /unlink command
        UnlinkCommand unlinkCommand = new UnlinkCommand(this, linkStore);
        getCommand("unlink").setExecutor(unlinkCommand);
        getCommand("unlink").setTabCompleter(unlinkCommand);

        // Register the pre-login listener that performs UUID spoofing
        getServer().getPluginManager().registerEvents(new PreLoginListener(this, linkStore), this);

        getLogger().info("AltLink enabled. " + linkStore.size() + " link(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (linkStore != null) {
            linkStore.save();
        }
        getLogger().info("AltLink disabled.");
    }

    public LinkStore getLinkStore() {
        return linkStore;
    }

    public PendingLinkManager getPendingLinkManager() {
        return pendingLinkManager;
    }
}

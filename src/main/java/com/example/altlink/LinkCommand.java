package com.example.altlink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Handles the /link command in its two forms:
 *
 * 1. /link <main_nick> <alt_nick>   -- run by the main account, sends a code to the alt
 * 2. /link <code>                   -- run by the alt account, confirms the link
 */
public final class LinkCommand implements CommandExecutor, TabCompleter {

    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("^\\d{6}$");

    private final AltLinkPlugin plugin;
    private final LinkStore linkStore;
    private final PendingLinkManager pendingLinkManager;

    public LinkCommand(AltLinkPlugin plugin, LinkStore linkStore, PendingLinkManager pendingLinkManager) {
        this.plugin = plugin;
        this.linkStore = linkStore;
        this.pendingLinkManager = pendingLinkManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && SIX_DIGIT_CODE.matcher(args[0]).matches()) {
            return handleConfirm(player, args[0]);
        }

        if (args.length == 2) {
            return handleInitiate(player, args[0], args[1]);
        }

        player.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/link <main_nick> <alt_nick>", NamedTextColor.GRAY)
                .append(Component.text("  - send a confirmation code from your main account", NamedTextColor.DARK_GRAY)));
        player.sendMessage(Component.text("/link <code>", NamedTextColor.GRAY)
                .append(Component.text("  - confirm linking on the alt account using the received code", NamedTextColor.DARK_GRAY)));
        return true;
    }

    /**
     * Step 1: /link <main_nick> <alt_nick>
     * The sender must be the main account (their name must match main_nick),
     * and the alt account must currently be online.
     */
    private boolean handleInitiate(Player sender, String mainNick, String altNick) {
        if (!sender.getName().equalsIgnoreCase(mainNick)) {
            sender.sendMessage(Component.text(
                    "The first argument must be your own (main) account name.", NamedTextColor.RED));
            return true;
        }

        if (sender.getName().equalsIgnoreCase(altNick)) {
            sender.sendMessage(Component.text("You cannot link an account to itself.", NamedTextColor.RED));
            return true;
        }

        Player altPlayer = Bukkit.getPlayerExact(altNick);
        if (altPlayer == null) {
            sender.sendMessage(Component.text(
                    "Player '" + altNick + "' must be online to receive the confirmation code.", NamedTextColor.RED));
            return true;
        }

        UUID mainUuid = sender.getUniqueId();
        UUID altUuid = altPlayer.getUniqueId();

        // Prevent chaining: an account that is itself already an alt cannot become a "main",
        // and a main account that is itself a linked alt should use its real (spoofed-to) identity.
        if (linkStore.isAlt(mainUuid)) {
            sender.sendMessage(Component.text(
                    "Your account is already linked as an alt of another account and cannot be used as a main.",
                    NamedTextColor.RED));
            return true;
        }

        if (linkStore.isAlt(altUuid)) {
            sender.sendMessage(Component.text(
                    "'" + altNick + "' is already linked as an alt of another account.", NamedTextColor.RED));
            return true;
        }

        if (linkStore.getLinkByAltUuid(altUuid).isPresent()) {
            sender.sendMessage(Component.text(
                    "'" + altNick + "' is already linked.", NamedTextColor.RED));
            return true;
        }

        String code = pendingLinkManager.createPending(mainUuid, sender.getName(), altUuid, altPlayer.getName());

        sender.sendMessage(Component.text("A confirmation code has been sent to '" + altPlayer.getName()
                + "'. Ask them to run:", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/link " + code, NamedTextColor.AQUA));

        altPlayer.sendMessage(Component.text("'" + sender.getName()
                + "' wants to link this account as an alt of theirs.", NamedTextColor.GREEN));
        altPlayer.sendMessage(Component.text("If this is correct, run: ", NamedTextColor.GREEN)
                .append(Component.text("/link " + code, NamedTextColor.AQUA)));
        altPlayer.sendMessage(Component.text(
                "Warning: this will merge this account's inventory, permissions and statistics with '"
                        + sender.getName() + "'.", NamedTextColor.YELLOW));

        return true;
    }

    /**
     * Step 2: /link <code>
     * Must be run by the alt account that received the code.
     */
    private boolean handleConfirm(Player sender, String code) {
        var pendingOpt = pendingLinkManager.confirm(sender.getUniqueId(), code);

        if (pendingOpt.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Invalid or expired code.", NamedTextColor.RED));
            return true;
        }

        var pending = pendingOpt.get();

        linkStore.addLink(pending.mainUuid(), pending.mainName(), pending.altUuid(), pending.altName());

        sender.sendMessage(Component.text("Linked successfully! This account is now linked to '"
                + pending.mainName() + "'.", NamedTextColor.GREEN));
        sender.sendMessage(Component.text(
                "Reconnect for the change to take full effect (shared inventory, permissions, and statistics).",
                NamedTextColor.YELLOW));

        Player mainPlayer = Bukkit.getPlayer(pending.mainUuid());
        if (mainPlayer != null && mainPlayer.isOnline()) {
            mainPlayer.sendMessage(Component.text("'" + pending.altName()
                    + "' has been linked to your account as an alt.", NamedTextColor.GREEN));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            // Suggest the player's own name as the first argument for the initiate form.
            return Collections.singletonList(player.getName());
        }
        if (args.length == 2) {
            List<String> names = new java.util.ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}

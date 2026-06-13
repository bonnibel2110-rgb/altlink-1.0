package com.example.altlink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Handles /unlink <alt_nick>
 *
 * Може виконати:
 *   - Основний гравець (main) — відв'язує свій альт.
 *   - Адмін з правом altlink.admin — може відв'язати будь-кого.
 */
public final class UnlinkCommand implements CommandExecutor, TabCompleter {

    private final AltLinkPlugin plugin;
    private final LinkStore linkStore;

    public UnlinkCommand(AltLinkPlugin plugin, LinkStore linkStore) {
        this.plugin = plugin;
        this.linkStore = linkStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Використання: /unlink <нік_альта>", NamedTextColor.YELLOW));
            return true;
        }

        String altNick = args[0];

        // Шукаємо альт по імені — спочатку серед онлайн гравців, потім через Bukkit.getOfflinePlayer
        UUID altUuid = resolveUuid(altNick);
        if (altUuid == null) {
            sender.sendMessage(Component.text(
                    "Гравець '" + altNick + "' не знайдений.", NamedTextColor.RED));
            return true;
        }

        var linkOpt = linkStore.getLinkByAltUuid(altUuid);
        if (linkOpt.isEmpty()) {
            sender.sendMessage(Component.text(
                    "'" + altNick + "' не є прив'язаним альт-акаунтом.", NamedTextColor.RED));
            return true;
        }

        var link = linkOpt.get();

        // Перевірка прав: або сам основний гравець, або адмін
        boolean isAdmin = sender.hasPermission("altlink.admin");
        boolean isOwner = (sender instanceof Player player)
                && player.getUniqueId().equals(link.mainUuid());

        if (!isAdmin && !isOwner) {
            sender.sendMessage(Component.text(
                    "Ти можеш відв'язати лише свій власний альт-акаунт.", NamedTextColor.RED));
            return true;
        }

        linkStore.removeLinkByAltUuid(altUuid);

        sender.sendMessage(Component.text(
                "Акаунт '" + altNick + "' успішно відв'язано від '" + link.mainName() + "'.",
                NamedTextColor.GREEN));

        // Повідомити основного гравця, якщо він онлайн (і це не він сам виконав команду)
        Player mainOnline = Bukkit.getPlayer(link.mainUuid());
        if (mainOnline != null && mainOnline.isOnline() && !mainOnline.equals(sender)) {
            mainOnline.sendMessage(Component.text(
                    "Альт-акаунт '" + altNick + "' було відв'язано від твого акаунту.",
                    NamedTextColor.YELLOW));
        }

        // Повідомити альт-гравця, якщо він зараз онлайн
        Player altOnline = Bukkit.getPlayer(altUuid);
        if (altOnline != null && altOnline.isOnline()) {
            altOnline.sendMessage(Component.text(
                    "Твій акаунт було відв'язано від '" + link.mainName() + "'. "
                    + "Перезайди на сервер, щоб зміни набрали чинності.",
                    NamedTextColor.YELLOW));
        }

        plugin.getLogger().info("Unlinked alt '" + altNick + "' (" + altUuid
                + ") from main '" + link.mainName() + "' (" + link.mainUuid() + ")"
                + " by " + sender.getName());

        return true;
    }

    private UUID resolveUuid(String name) {
        // Спочатку онлайн
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        // Потім офлайн (кешований UUID, якщо гравець хоча б раз заходив)
        @SuppressWarnings("deprecation")
        var offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) {
            return offline.getUniqueId();
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Підказуємо нікнейми онлайн гравців
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}

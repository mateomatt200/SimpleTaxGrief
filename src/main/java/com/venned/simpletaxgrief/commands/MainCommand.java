package com.venned.simpletaxgrief.commands;

import com.venned.simpletaxgrief.Main;
import com.venned.simpletaxgrief.build.ClaimTax;
import com.venned.simpletaxgrief.manager.ClaimTaxManager;
import com.venned.simpletaxgrief.task.TaxTask;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.*;


public class MainCommand implements CommandExecutor {

    private final TaxTask taxTask;
    private final Main plugin;
    ClaimTaxManager claimTaxManager;

    public MainCommand(Main plugin, TaxTask taxTask, ClaimTaxManager claimTaxManager) {
        this.plugin = plugin;
        this.taxTask = taxTask;
        this.claimTaxManager = claimTaxManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {

        if (sender instanceof Player player) {

            if (args.length == 0) {
                player.sendMessage("Usage: /daisytax next | /daisytax bankrupt");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    if(!player.isOp()) return true;
                    plugin.reloadConfig();
                    player.sendMessage("§cReload Plugin!");
                }
                case "next" -> {
                    if(player.hasPermission("daisytax.next")) {
                        long lastTax = taxTask.getLast_tax_charge(); // Último cobro
                        int intervalTax = taxTask.getIntervalTax(); // Intervalo en segundos
                        long currentTime = System.currentTimeMillis(); // Tiempo actual

                        // Calcular el tiempo restante hasta el próximo cobro
                        long timeElapsed = currentTime - lastTax;
                        long timeRemaining = (intervalTax * 1000L) - timeElapsed;
                        int minutesRemaining = (int) (timeRemaining / 60000); // Convertir a minutos

                        if (minutesRemaining <= 0) {
                            player.sendMessage("§cThe tax is being collected at this time.");
                        } else {
                            String message = plugin.getConfig().getString("messages.tax_upcoming")
                                    .replace("{minutes}", String.valueOf(minutesRemaining))
                                    .replace("&", "§");
                            player.sendMessage(message);
                        }
                    }
                }

                case "member" -> {
                    if(player.hasPermission("daisytax.member")) {
                        if (args.length < 2) {
                            player.sendMessage("Usage: /daisytax member <player>");
                            return true;
                        }

                        String targetName = args[1];
                        Player playerFind = Bukkit.getPlayer(targetName);
                        if (playerFind == null) {
                            player.sendMessage("Player no Online");
                            return true;
                        }

                        ClaimTax claimTax = claimTaxManager.getClaimTaxes().stream()
                                .filter(p -> p.getClaim().ownerID.equals(player.getUniqueId()))
                                .findFirst().orElse(null);

                        if (claimTax == null) {
                            player.sendMessage("You no have claim");
                            return true;
                        }

                        Map<String, ClaimPermission> claim = getClaimPermissions(claimTax.getClaim());

                        assert claim != null;

                        if(!claim.containsKey(playerFind.getUniqueId().toString())){
                            player.sendMessage("Player no trust in your claim");
                            return true;
                        }

                        if (claimTax.getMemberTax().contains(playerFind.getUniqueId())) {
                            player.sendMessage("You have already member of this player");
                            return true;
                        }

                        claimTax.getMemberTax().add(playerFind.getUniqueId());
                        player.sendMessage("Player add your claim tax");
                    }



                }

                case "bankrupt" -> {
                    if(player.hasPermission("daisytax.bankrupt")) {
                        List<OfflinePlayer> playerList = new ArrayList<>();

                        for (ClaimTax claimTax : claimTaxManager.getClaimTaxes()) {
                            if (claimTax.getClaim().ownerID == null) continue;
                            if (claimTax.isBankrupt()) {
                                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(claimTax.getClaim().ownerID);
                                playerList.add(offlinePlayer);
                            }
                        }

                        List<String> message = new ArrayList<>();
                        message.add("Bankrupt Players: ");
                        for (OfflinePlayer offlinePlayer : playerList) {
                            message.add(offlinePlayer.getName());
                        }

                        for (String m : message) {
                            player.sendMessage(m);
                        }
                    }
                }

                default -> player.sendMessage("§cWrong command. Usage: /daisytax next | /daisytax bankrupt");
            }

            return true;
        }


        return true;
    }

    public static HashMap<String, ClaimPermission> getClaimPermissions(Claim claim) {
        try {
            Field field = Claim.class.getDeclaredField("playerIDToClaimPermissionMap");
            field.setAccessible(true);
            return (HashMap<String, ClaimPermission>) field.get(claim);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}

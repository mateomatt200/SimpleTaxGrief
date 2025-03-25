package com.venned.simpletaxgrief.task;


import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.venned.simpletaxgrief.Main;
import com.venned.simpletaxgrief.build.ClaimTax;
import com.venned.simpletaxgrief.manager.ClaimTaxManager;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.ess3.api.IUser;
import net.ess3.api.MaxMoneyException;
import net.ess3.api.events.UserBalanceUpdateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Level;


public class TaxTask extends BukkitRunnable {

    private final ClaimTaxManager taxManager;
    private final Map<Integer, Integer> taxRates;
    private final int bankruptcyDays;
    private final int memberTaxPercentage;
    private final int intervalTax;
    private long last_tax_charge;
    private Main plugin;

    public TaxTask(ClaimTaxManager taxManager, Main plugin) {
        this.taxManager = taxManager;
        intervalTax = plugin.getConfig().getInt("tax_interval");
        bankruptcyDays = plugin.getConfig().getInt("bankruptcy_days", 90);
        memberTaxPercentage = plugin.getConfig().getInt("member_tax_percentage", 20);

        taxRates = new HashMap<>();
        List<String> taxRateList = plugin.getConfig().getStringList("tax_rates");
        for (String entry : taxRateList) {
            try {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    int claimBlocks = Integer.parseInt(parts[0]);
                    int taxAmount = Integer.parseInt(parts[1]);
                    taxRates.put(claimBlocks, taxAmount);
                } else {
                    Bukkit.getLogger().warning("Invalid tax rate entry: " + entry);
                }
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("Invalid tax rate format: " + entry);
            }
        }

        this.plugin = plugin;

    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - last_tax_charge;
        long timeRemaining = (intervalTax * 1000L) - timeElapsed;
        int minutesRemaining = (int) (timeRemaining / 60000); // Convertir a minutos

        List<Integer> reminderIntervals = plugin.getConfig().getIntegerList("tax_reminder_intervals");


        if (reminderIntervals.contains(minutesRemaining) && timeRemaining % 60000 <= 1000) {
            String message = plugin.getConfig().getString("messages.tax_upcoming")
                    .replace("{minutes}", String.valueOf(minutesRemaining))
                    .replace("&", "§");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }

        if (timeElapsed < intervalTax * 1000L) {
            return;
        }

        Set<Claim> activeClaims = new HashSet<>(GriefPrevention.instance.dataStore.getClaims());

        taxManager.getClaimTaxes().removeIf(claimTax -> !activeClaims.contains(claimTax.getClaim()));

        for (Claim claim : GriefPrevention.instance.dataStore.getClaims()) {
            ClaimTax claimTax = taxManager.getClaimTaxes()
                    .stream()
                    .filter(t -> t.getClaim().equals(claim))
                    .findFirst()
                    .orElseGet(() -> {
                        ClaimTax newClaimTax = new ClaimTax(claim, System.currentTimeMillis(), new ArrayList<>(), false);
                        taxManager.getClaimTaxes().add(newClaimTax);
                        return newClaimTax;
                    });



            if (claim.ownerID != null) {
                int claimSize = claim.getArea();
                BigDecimal taxAmount = calculateTax(claimSize);
                takeTax(claim.ownerID, taxAmount, claimTax);
            }
        }

        last_tax_charge = currentTime;

    }

    private BigDecimal calculateTax(int claimSize) {
        return taxRates.entrySet()
                .stream()
                .filter(entry -> claimSize >= entry.getKey())
                .map(Map.Entry::getValue)
                .max(Integer::compare)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO);
    }

    private void takeTax(UUID ownerUUID, BigDecimal totalTaxAmount, ClaimTax claimTax) {
        if (ownerUUID == null) return;

        Essentials essentials = Essentials.getPlugin(Essentials.class);
        User owner = essentials.getUser(ownerUUID);
        if (owner == null) return;

        List<UUID> members = claimTax.getMemberTax(); // Obtener los miembros
        List<User> payers = new ArrayList<>();
        for (UUID memberUUID : members) {
            User member = essentials.getUser(memberUUID);
            if (member != null) payers.add(member);
        }

        int totalMembers = payers.size();
        int totalTaxPercentage = Math.min(this.memberTaxPercentage, 200); // Evitar exceder el 200%

        // Si hay miembros, dividir el impuesto equitativamente
        BigDecimal memberTaxAmount = totalMembers > 0
                ? totalTaxAmount.multiply(BigDecimal.valueOf(totalTaxPercentage / 100.0))
                .divide(BigDecimal.valueOf(totalMembers), RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalCollected = BigDecimal.ZERO;

        // Cobrar impuestos a los miembros
        for (User member : payers) {
            BigDecimal memberBalance = member.getMoney();
            BigDecimal minMoney = getMaxNegativeMoney();
            BigDecimal amountToPay = memberTaxAmount.min(memberBalance.subtract(minMoney)); // Solo pagar lo posible

            if (amountToPay.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    member.setMoney(memberBalance.subtract(amountToPay), UserBalanceUpdateEvent.Cause.API);
                    totalCollected = totalCollected.add(amountToPay);

                    Player player = Bukkit.getPlayer(member.getUUID());
                    if (player != null) {
                        String message = plugin.getConfig().getString("messages.tax_deducted")
                                .replace("{amount}", amountToPay.toString())
                                .replace("&", "§");
                        player.sendMessage(message);
                    }
                } catch (MaxMoneyException e) {
                    Bukkit.getLogger().log(Level.WARNING, "Error al cobrar impuestos a " + member.getName(), e);
                }
            }

            // Si ya recolectamos todo, terminamos
            if (totalCollected.compareTo(totalTaxAmount) >= 0) break;
        }

        // Cobrar el impuesto restante al dueño
        BigDecimal remainingTax = totalTaxAmount.subtract(totalCollected);
        if (remainingTax.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ownerBalance = owner.getMoney();
            BigDecimal minMoney = getMaxNegativeMoney();
            BigDecimal amountToPay = remainingTax.min(ownerBalance.subtract(minMoney));

            if (amountToPay.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    owner.setMoney(ownerBalance.subtract(amountToPay), UserBalanceUpdateEvent.Cause.API);
                    totalCollected = totalCollected.add(amountToPay);

                    Player player = Bukkit.getPlayer(ownerUUID);
                    if (player != null) {
                        String message = plugin.getConfig().getString("messages.tax_deducted")
                                .replace("{amount}", amountToPay.toString())
                                .replace("&", "§");
                        player.sendMessage(message);
                    }
                } catch (MaxMoneyException e) {
                    Bukkit.getLogger().log(Level.WARNING, "Error al cobrar impuestos al dueño " + owner.getName(), e);
                }
            }
        }

        // Si aún falta dinero, marcar como bancarrota
        if (totalCollected.compareTo(totalTaxAmount) < 0) {
            warnBankruptcy(owner, claimTax);
        } else {
            claimTax.setLast_tax(System.currentTimeMillis());
            claimTax.setBankrupt(false);
        }
    }

    private void warnBankruptcy(User user, ClaimTax claimTax) {
        Player player = Bukkit.getPlayer(user.getUUID());
        if (player != null) {
            long lastTakeTax = claimTax.getLast_tax(); // Última vez que se cobró el impuesto
            long currentTime = System.currentTimeMillis(); // Tiempo actual

            // Convertir tiempo transcurrido en días
            long daysElapsed = (currentTime - lastTakeTax) / (1000 * 60 * 60 * 24);
            long daysRemaining = Math.max(bankruptcyDays - daysElapsed, 0); // Evitar valores negativos


            String message = plugin.getConfig().getString("messages.bankrupt_warning")
                    .replace("{days}", String.valueOf(daysRemaining))
                    .replace("&", "§");

            claimTax.setBankrupt(true);

            player.sendMessage(message);
        }
    }
    private BigDecimal getMaxNegativeMoney() {
        return Essentials.getPlugin(Essentials.class).getSettings().getMinMoney();
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

    public int getIntervalTax() {
        return intervalTax;
    }

    public long getLast_tax_charge() {
        return last_tax_charge;
    }
}

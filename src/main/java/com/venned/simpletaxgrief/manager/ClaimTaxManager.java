package com.venned.simpletaxgrief.manager;

import com.venned.simpletaxgrief.Main;
import com.venned.simpletaxgrief.build.ClaimTax;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class ClaimTaxManager {

    private final Set<ClaimTax> claimTaxes;
    private final File file;
    private final FileConfiguration config;

    public ClaimTaxManager(Main plugin) {
        this.claimTaxes = new HashSet<>();
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.file = new File(folder, "taxes.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);

        loadAll();
    }

    public void loadAll() {
        if (!file.exists()) return;

        List<Map<?, ?>> taxesList = config.getMapList("taxes");
        for (Map<?, ?> taxData : taxesList) {
            int id = (int) taxData.get("id");
            int lastTax = (int) taxData.get("last_tax");
            boolean bankrupt = (boolean) taxData.get("bankrupt");
            List<String> memberTaxUUIDs = (List<String>) taxData.get("memberTax");
            List<UUID> memberTax = new ArrayList<>();

            for (String uuid : memberTaxUUIDs) {
                memberTax.add(UUID.fromString(uuid));
            }

            Claim claim = GriefPrevention.instance.dataStore.getClaim(id);

            if(claim == null) return;


            ClaimTax claimTax = new ClaimTax(claim, lastTax, memberTax, bankrupt);
            claimTaxes.add(claimTax);
        }
    }

    public void saveAll() {

        config.set("taxes", null);

        List<Map<String, Object>> taxesList = new ArrayList<>();

        for (ClaimTax claimTax : claimTaxes) {
            Map<String, Object> taxData = new HashMap<>();
            taxData.put("id", claimTax.getClaim().getID());
            taxData.put("last_tax", (int) claimTax.getLast_tax());
            taxData.put("bankrupt", claimTax.isBankrupt());

            List<String> memberTaxUUIDs = new ArrayList<>();
            for (UUID uuid : claimTax.getMemberTax()) {
                memberTaxUUIDs.add(uuid.toString());
            }
            taxData.put("memberTax", memberTaxUUIDs);

            taxesList.add(taxData);
        }

        config.set("taxes", taxesList);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<ClaimTax> getClaimTaxes() {
        return claimTaxes;
    }
}


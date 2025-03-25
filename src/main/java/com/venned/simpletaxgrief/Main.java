package com.venned.simpletaxgrief;

import com.venned.simpletaxgrief.commands.MainCommand;
import com.venned.simpletaxgrief.manager.ClaimTaxManager;
import com.venned.simpletaxgrief.task.TaxTask;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    ClaimTaxManager claimTaxManager;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        claimTaxManager = new ClaimTaxManager(this);

        TaxTask taxTask =  new TaxTask(claimTaxManager, this);
        taxTask.runTaskTimer(this, 0, 20);

        getCommand("daisytax").setExecutor(new MainCommand(this, taxTask, claimTaxManager));

    }

    @Override
    public void onDisable() {
        claimTaxManager.saveAll();
    }
}

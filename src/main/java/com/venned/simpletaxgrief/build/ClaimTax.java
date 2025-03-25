package com.venned.simpletaxgrief.build;

import me.ryanhamshire.GriefPrevention.Claim;

import java.util.List;
import java.util.UUID;

public class ClaimTax {

    Claim claim;
    long last_tax;
    List<UUID> memberTax;
    boolean bankrupt;

    public ClaimTax(Claim claim, long last_tax, List<UUID> memberTax, boolean bankrupt) {
        this.claim = claim;
        this.last_tax = last_tax;
        this.memberTax = memberTax;
        this.bankrupt = bankrupt;
    }

    public boolean isBankrupt() {
        return bankrupt;
    }

    public void setBankrupt(boolean bankrupt) {
        this.bankrupt = bankrupt;
    }

    public void setLast_tax(long last_tax) {
        this.last_tax = last_tax;
    }

    public Claim getClaim() {
        return claim;
    }

    public List<UUID> getMemberTax() {
        return memberTax;
    }

    public long getLast_tax() {
        return last_tax;
    }
}

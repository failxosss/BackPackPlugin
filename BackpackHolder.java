package cz.tvojepackage.backpack.utils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder, který si pamatuje, komu batoh patří a pod jakým indexem.
 * Díky tomu listener (InventoryCloseEvent) pozná, který batoh přesně uložit,
 * aniž by musel cokoliv parsovat z titulku okna.
 */
public class BackpackHolder implements InventoryHolder {

    private final UUID owner;
    private final int index;
    private Inventory inventory;

    public BackpackHolder(UUID owner, int index) {
        this.owner = owner;
        this.index = index;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getIndex() {
        return index;
    }
}

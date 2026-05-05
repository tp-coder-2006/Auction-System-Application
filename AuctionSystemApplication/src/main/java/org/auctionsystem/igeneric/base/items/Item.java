package org.auctionsystem.igeneric.base.items;

import org.auctionsystem.igeneric.base.Entity;

public abstract class Item extends Entity {
    private String description;
    private boolean isAvailable = true;
    // --- CONSTRUCTORS ---
    public Item(String name, String description) {
        super(name);
        this.description = description;
    }
    public Item() {
        super();
    }

    // --- GETTERS ---
    public String getDescription() {
        return this.description;
    }

    public boolean isAvailable() {
        return this.isAvailable;
    }

    // --- SETTERS ---
    public void setDescription(String description) {
        this.description = description;
    }

    public void setAvailable(boolean available) {
        this.isAvailable = available;
    }
}
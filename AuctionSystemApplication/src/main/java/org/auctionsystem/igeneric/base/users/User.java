package org.auctionsystem.igeneric.base.users;

import org.auctionsystem.igeneric.base.Entity;

public abstract class User extends Entity {
    private boolean isActive = true;

    public User(String name) {
        super(name);
    }

    public User() {}

    // --- GETTER & SETTER ---

    public boolean isActive() {
        return this.isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }
}
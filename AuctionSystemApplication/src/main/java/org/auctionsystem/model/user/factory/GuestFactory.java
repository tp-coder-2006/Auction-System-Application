package org.auctionsystem.model.user.factory;

import org.auctionsystem.igeneric.base.users.User;
import org.auctionsystem.igeneric.interfaces.user.UserFactory;
import org.auctionsystem.model.user.Guest;

public class GuestFactory implements UserFactory {
    public User createUser() {
        return new Guest();
    }
}

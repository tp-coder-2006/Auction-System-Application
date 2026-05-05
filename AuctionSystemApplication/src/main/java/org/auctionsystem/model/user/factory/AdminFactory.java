package org.auctionsystem.model.user.factory;

import org.auctionsystem.igeneric.base.users.User;
import org.auctionsystem.igeneric.interfaces.user.UserFactory;
import org.auctionsystem.model.user.Admin;

public class AdminFactory implements UserFactory {
    private String name;
    private String email;
    private String password;
    public AdminFactory(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }
    public User createUser() {
        return new Admin(name, email, password);
    }
}

package org.auctionsystem.model.user.factory;

import org.auctionsystem.igeneric.interfaces.user.UserFactory;
import org.auctionsystem.igeneric.base.users.User;
import org.auctionsystem.model.user.SystemUser;

public class SystemUserFactory implements UserFactory {
    public String name;
    public String email;
    public String password;
    public SystemUserFactory(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }
    public User createUser() {
        return new SystemUser(name, email, password);
    }
}

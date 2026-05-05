package org.auctionsystem.model.user;

import org.auctionsystem.igeneric.base.users.User;
import org.auctionsystem.igeneric.interfaces.user.Email;
import org.auctionsystem.igeneric.interfaces.user.Password;

public class Admin extends User implements Password, Email {
    private String email;
    private String password;

    public Admin(String name, String email, String password) {
        super(name);
        this.email = email;
        this.password = password;
    }

    public Admin() {
        super();
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }
}
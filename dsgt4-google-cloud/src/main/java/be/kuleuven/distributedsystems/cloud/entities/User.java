package be.kuleuven.distributedsystems.cloud.entities;

import com.google.gson.JsonObject;

public class User {

    private String email;
    private String role;

    public User(String email, String role) {
        this.email = email;
        this.role = role;
        if (this.role == null) this.role = "user";
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isManager() {
        return this.role != null && this.role.equals("manager");
    }
}

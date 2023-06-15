package be.kuleuven.distributedsystems.cloud.entities;

import com.google.gson.JsonObject;

public class User {

    private final String email;
    private String role;
    private String uid;

    public User(String email, String role, String uid) {
        this.email = email;
        this.uid = uid;
        if (role == null) {this.role = "user"; }
        else this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getUid() {
        return uid;
    }
    public boolean isManager() {
        return this.role != null && this.role.equals("manager");
    }
}

package be.kuleuven.distributedsystems.cloud.entities;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Booking {
    private UUID id;
    private LocalDateTime time;
    private List<Ticket> tickets;
    private String customer;

    public Booking(UUID id, LocalDateTime time, List<Ticket> tickets, String customer) {
        this.id = id;
        this.time = time;
        this.tickets = tickets;
        this.customer = customer;
    }

    public UUID getId() {
        return this.id;
    }

    public LocalDateTime getTime() {
        return this.time;
    }

    public List<Ticket> getTickets() {
        return this.tickets;
    }

    public void setTickets(List<Ticket> tickets) {
        this.tickets = tickets;
    }

    public String getCustomer() {
        return this.customer;
    }

    public JsonObject getJsonObject(String email){
        // Create a JSONObject for the booking
        JsonObject bookingObject = new JsonObject();
        bookingObject.addProperty("id", id.toString());
        bookingObject.addProperty("time", time.toString());
        bookingObject.addProperty("customer", email);

        // Create a JSONArray for the tickets
        JsonArray ticketsArray = new JsonArray();
        for (Ticket ticket : tickets) {
            JsonObject ticketObject = ticket.getJsonObject(email);
            ticketsArray.add(ticketObject);
        }

        bookingObject.add("tickets", ticketsArray);
        return bookingObject;
    }
}

package be.kuleuven.distributedsystems.cloud.entities;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public String getFormattedTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        return time.format(formatter);
    }

    public JsonObject getJsonObject(String email) {
        // Create a JSONObject for the booking
        JsonObject bookingObject = new JsonObject();
        bookingObject.addProperty("id", id.toString());
        bookingObject.addProperty("time", getFormattedTime());

        // Create a JsonArray for the tickets
        JsonArray ticketsArray = new JsonArray();
        for (Ticket ticket : tickets) {
            JsonObject ticketObject = ticket.getJsonObject(email);
            ticketsArray.add(ticketObject);
        }
        bookingObject.addProperty("customer", email);
        bookingObject.add("tickets", ticketsArray);
        return bookingObject;
    }

    public Map<String, Object> getBookingMap() {
        Map<String, Object> bookingMap = new HashMap<>();
        bookingMap.put("time", getFormattedTime());
        bookingMap.put("customer", customer);
        return bookingMap;
    }
}
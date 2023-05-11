package be.kuleuven.distributedsystems.cloud.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
}

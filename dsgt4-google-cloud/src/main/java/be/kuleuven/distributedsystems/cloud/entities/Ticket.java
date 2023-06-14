package be.kuleuven.distributedsystems.cloud.entities;

import com.google.gson.JsonObject;
import java.util.UUID;

public class Ticket {
    private String airline;
    private UUID flightId;
    private UUID seatId;
    private UUID ticketId;
    private String customer;
    private String bookingReference;

    public Ticket() {
    }

    public Ticket(String airline, UUID flightId, UUID seatId, UUID ticketId, String customer, String bookingReference) {
        this.airline = airline;
        this.flightId = flightId;
        this.seatId = seatId;
        this.ticketId = ticketId;
        this.customer = customer;
        this.bookingReference = bookingReference;
    }

    public String getAirline() {
        return airline;
    }

    public UUID getFlightId() {
        return flightId;
    }

    public UUID getSeatId() {
        return this.seatId;
    }

    public UUID getTicketId() {
        return this.ticketId;
    }

    public String getCustomer() {
        return this.customer;
    }

    public String getBookingReference() {
        return this.bookingReference;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Ticket)) {
            return false;
        }
        var other = (Ticket) o;
        return this.ticketId.equals(other.ticketId)
                && this.seatId.equals(other.seatId)
                && this.flightId.equals(other.flightId)
                && this.airline.equals(other.airline);
    }

    @Override
    public int hashCode() {
        return this.airline.hashCode() * this.flightId.hashCode() * this.seatId.hashCode() * this.ticketId.hashCode();
    }

    public JsonObject getJsonObject(String email){
        JsonObject ticketObject = new JsonObject();
        ticketObject.addProperty("airline", airline);
        ticketObject.addProperty("flightId", flightId.toString());
        ticketObject.addProperty("seatId", seatId.toString());
        ticketObject.addProperty("ticketId", ticketId.toString());
        ticketObject.addProperty("customer", email);

        return ticketObject;
    }
}

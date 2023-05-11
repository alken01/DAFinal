package be.kuleuven.distributedsystems.cloud.entities;

import java.io.Serializable;
import java.util.UUID;

public class Quote {

    private String airline;
    private UUID flightId;
    private UUID seatId;

    public Quote() {
    }

    public Quote(String airline, UUID flightId, UUID seatId) {
        this.airline = airline;
        this.flightId = flightId;
        this.seatId = seatId;
    }

    public String getAirline() {
        return airline;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public UUID getFlightId() {
        return flightId;
    }

    public void setFlightId(UUID flightId) {
        this.flightId = flightId;
    }

    public UUID getSeatId() {
        return this.seatId;
    }

    public void setSeatId(UUID seatId) {
        this.seatId = seatId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Quote)) {
            return false;
        }
        var other = (Quote) o;
        return this.airline.equals(other.airline)
                && this.flightId.equals(other.flightId)
                && this.seatId.equals(other.seatId);
    }

    @Override
    public int hashCode() {
        return this.airline.hashCode() * this.flightId.hashCode() * this.seatId.hashCode();
    }
}

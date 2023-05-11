package be.kuleuven.distributedsystems.cloud.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Seat {
    private String airline;
    private UUID flightId;
    private UUID seatId;
    private LocalDateTime time;
    private String type;
    private String name;
    private double price;

    public Seat() {
    }

    public Seat(String airline, UUID flightId, UUID seatId, LocalDateTime time, String type, String name, double price) {
        this.airline = airline;
        this.flightId = flightId;
        this.seatId = seatId;
        this.time = time;
        this.type = type;
        this.name = name;
        this.price = price;
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

    public LocalDateTime getTime() {
        return this.time;
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }

    public double getPrice() {
        return this.price;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Seat)) {
            return false;
        }
        var other = (Seat) o;
        return this.airline.equals(other.airline)
                && this.flightId.equals(other.flightId)
                && this.seatId.equals(other.seatId);
    }

    @Override
    public int hashCode() {
        return this.airline.hashCode() * this.flightId.hashCode() * this.seatId.hashCode();
    }
}

package be.kuleuven.distributedsystems.cloud.entities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Flight {
    private String airline;
    private UUID flightId;
    private String name;
    private String location;
    private String image;

    public Flight() {
    }

    public Flight(String airline, UUID flightId, String name, String location, String image) {
        this.airline = airline;
        this.flightId = flightId;
        this.name = name;
        this.location = location;
        this.image = image;
    }

    public String getAirline() {
        return airline;
    }

    public UUID getFlightId() {
        return flightId;
    }

    public String getName() {
        return this.name;
    }

    public String getLocation() {
        return this.location;
    }

    public String getImage() {
        return this.image;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Flight)) {
            return false;
        }
        var other = (Flight) o;
        return this.airline.equals(other.airline)
                && this.flightId.equals(other.flightId);
    }

    @Override
    public int hashCode() {
        return this.airline.hashCode() * this.flightId.hashCode();
    }
}

package be.kuleuven.distributedsystems.cloud.repository;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.List;

public interface AirlineRepository{
    boolean containsAirline(String airline);
    JsonArray getFlights();
    JsonObject getFlight(String airline, String flightId);
    String[] getFlightTimes(String airline, String flightId);
    JsonArray getAvailableSeats(String airline, String flightId, String time);
    JsonObject getSeat(String airline, String flightId, String seatId);
    Booking confirmQuotes(List<Quote> quotes, String email, String bookingReference, LocalDateTime time);
    boolean ticketsAvailable(List<Quote> quotes);
    Booking bookTickets(List<Quote> quotes, String email, String bookingReference, LocalDateTime time);
}

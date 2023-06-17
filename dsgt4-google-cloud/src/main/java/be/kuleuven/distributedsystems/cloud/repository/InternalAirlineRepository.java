package be.kuleuven.distributedsystems.cloud.repository;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.google.cloud.firestore.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class InternalAirlineRepository implements AirlineRepository {
    private final Firestore firestore;
    private final CollectionReference internalFlightsRef;

    @Autowired
    public InternalAirlineRepository(Firestore firestore) {
        this.firestore = firestore;
        internalFlightsRef = firestore.collection(FirestoreConstants.AIRLINE_COLLECTION)
                .document(FirestoreConstants.INTERNAL_AIRLINE_COLLECTION)
                .collection(FirestoreConstants.FLIGHTS_AIRLINE_COLLECTION);
    }

    @Override
    public boolean containsAirline(String airline) {
        return airline.equals(FirestoreConstants.INTERNAL_AIRLINE_COLLECTION);
    }

    @Override
    public JsonArray getFlights() {
        // Get all flights from internalAirline
        List<DocumentSnapshot> documents;
        try {
            documents = internalFlightsRef.get().get().getDocuments().stream()
                    .filter(document -> !document.getId().equals("internalAirline"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.out.println("Error getting flights from Firestore");
            return new JsonArray();
        }

        // Create JsonArray of flights
        JsonArray flightsArray = new JsonArray();
        for (DocumentSnapshot document : documents) {
            flightsArray.add(createFlightJsonObject(document));
        }

        return flightsArray;
    }

    @Override
    public JsonObject getFlight(String airline, String flightId) {
        // Get flight from internalAirline
        DocumentSnapshot snapshot;
        try {
            snapshot = internalFlightsRef.document(flightId).get().get();
        } catch (Exception e) {
            System.out.println("Error getting flight: " + flightId);
            return new JsonObject();
        }
        return createFlightJsonObject(snapshot);
    }

    private JsonObject createFlightJsonObject(DocumentSnapshot snapshot) {
        JsonObject flightObject = new JsonObject();
        flightObject.addProperty("airline", FirestoreConstants.INTERNAL_AIRLINE_COLLECTION);
        flightObject.addProperty("flightId", snapshot.getId());
        flightObject.addProperty("name", snapshot.getString("name"));
        flightObject.addProperty("location", snapshot.getString("location"));
        flightObject.addProperty("image", snapshot.getString("image"));
        return flightObject;
    }

    @Override
    public String[] getFlightTimes(String airline, String flightId) {
        // Get all the flight times from the flight
        List<QueryDocumentSnapshot> documents;
        try {
            documents = internalFlightsRef.document(flightId)
                    .collection(FirestoreConstants.SEATS_COLLECTION).select("time").get().get().getDocuments();
        } catch (Exception e) {
            System.out.println("Error getting flight times for flight: " + flightId);
            return new String[0];
        }
        Set<String> flightTimesSet = new HashSet<>();
        for (DocumentSnapshot document : documents) {
            flightTimesSet.add(document.getString("time"));
        }
        return flightTimesSet.toArray(new String[0]);
    }

    @Override
    public JsonArray getAvailableSeats(String airline, String flightId, String time) {
        // Get all seats from the flight with the given time that are not booked
        Query query = internalFlightsRef.document(flightId).collection(FirestoreConstants.SEATS_COLLECTION)
                .whereEqualTo("time", time).whereEqualTo("bookingReference", "");

        // Get all the seats
        List<QueryDocumentSnapshot> documents;
        try {
            documents = query.get().get().getDocuments();
        } catch (Exception e) {
            System.out.println("Error getting available seats for flight: " + flightId + " at time: " + time);
            return new JsonArray();
        }
        // Create JsonArray of seats
        JsonArray seatsArray = new JsonArray();
        for (DocumentSnapshot document : documents) {
            seatsArray.add(createSeatJsonObject(document));
        }
        return seatsArray;
    }

    @Override
    public JsonObject getSeat(String airline, String flightId, String seatId) {
        try {
            // Get the seat from the flight
            DocumentSnapshot snapshot = internalFlightsRef.document(flightId)
                    .collection(FirestoreConstants.SEATS_COLLECTION).document(seatId).get().get();
            if (snapshot.exists()) {
                return createSeatJsonObject(snapshot);
            }
        } catch (Exception e) {
            System.out.println("Error getting seat");
        }
        return new JsonObject();
    }

    private JsonObject createSeatJsonObject(DocumentSnapshot document) {
        JsonObject seatObject = new JsonObject();
        seatObject.addProperty("airline", document.getString("airline"));
        seatObject.addProperty("flightId", document.getString("flightId"));
        seatObject.addProperty("seatId", document.getId());
        seatObject.addProperty("name", document.getString("name"));
        seatObject.addProperty("time", document.getString("time"));
        seatObject.addProperty("type", document.getString("type"));
        seatObject.addProperty("price", document.getString("price"));
        seatObject.addProperty("bookingReference", document.getString("bookingReference"));
        seatObject.addProperty("customer", document.getString("customer"));
        return seatObject;
    }

    @Override
    public Booking confirmQuotes(List<Quote> quotes, String email, String bookingReference, LocalDateTime time) {
        // Check if ALL the tickets are still available
        if (!ticketsAvailable(quotes)) return null;
        System.out.println("All tickets available");
        // Reserve all the tickets
        return bookTickets(quotes, email, bookingReference, time);
    }

    public boolean ticketsAvailable(List<Quote> quotes) {
        for (Quote quote : quotes) {
            //    check if the booking reference is empty
            if (!getSeat(FirestoreConstants.INTERNAL_AIRLINE_COLLECTION, quote.getFlightId().toString(), quote.getSeatId().toString())
                    .get("bookingReference").getAsString().equals("")) {
                return false;
            }
        }
        return true;
    }

    public Booking bookTickets(List<Quote> quotes, String email, String bookingReference, LocalDateTime time) {
        // Create booking
        List<Ticket> tickets = new ArrayList<>();
        WriteBatch batch = firestore.batch(); // Use batch writes for atomicity

        for (Quote quote : quotes) {
            // Get the seat document reference
            DocumentReference seatRef = internalFlightsRef
                    .document(quote.getFlightId().toString())
                    .collection(FirestoreConstants.SEATS_COLLECTION)
                    .document(quote.getSeatId().toString());

            // Update the seat data
            batch.update(seatRef, "bookingReference", bookingReference);
            batch.update(seatRef, "customer", email);

            // Create the ticket
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = new Ticket(quote.getAirline(), quote.getFlightId(), quote.getSeatId(),
                    ticketId, email, bookingReference);
            tickets.add(ticket);
        }

        // Commit the batch write to save the changes
        try {
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null; // Return null in case of an error
        }

        // Create and return the booking
        return new Booking(UUID.fromString(bookingReference), time, tickets, email);
    }

}
package be.kuleuven.distributedsystems.cloud.repository;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class FirestoreRepository {
    private final Firestore firestore;
    private final String USER_COLLECTION = "users";
    private final String BOOKING_COLLECTION = "bookings";
    private final String INTERNAL_AIRLINE_COLLECTION = "internalAirline";
    private final String AIRLINE_COLLECTION = "airline";
    private final String FLIGHTS_AIRLINE_COLLECTION = "flights";
    private final String SEATS_COLLECTION = "seats";

    private final String INTERNAL_AIRLINE_DATA = "src/main/resources/data.json";

    private final CollectionReference usersRef;
    private final CollectionReference internalFlightsRef;

    @Autowired
    public FirestoreRepository(Firestore firestore) {
        this.firestore = firestore;
        usersRef = firestore.collection(USER_COLLECTION);
        internalFlightsRef = firestore.collection(AIRLINE_COLLECTION)
                .document(INTERNAL_AIRLINE_COLLECTION)
                .collection(FLIGHTS_AIRLINE_COLLECTION);
        loadInternalAirlineData();
    }

    public void loadInternalAirlineData() {
        System.out.println("Loading Internal Airline into Firestore");
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode data = mapper.readTree(new File(INTERNAL_AIRLINE_DATA));
            for (JsonNode flightNode : data.get("flights")) {
                if (flightExists(flightNode.get("name").asText())) {
                    System.out.println("Flight with name " + flightNode.get("name").asText() + " already exists");
                    continue;
                }

                UUID flightUUID = UUID.randomUUID();
                DocumentReference flightDoc = createFlightDocument(flightNode, flightUUID);

                for (JsonNode seatNode : flightNode.get(SEATS_COLLECTION)) {
                    UUID seatUUID = UUID.randomUUID();
                    createSeat(seatNode, flightDoc, flightUUID, seatUUID);
                }

            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private boolean flightExists(String name) throws ExecutionException, InterruptedException {
        Query query = internalFlightsRef.whereEqualTo("name", name);
        return query.get().get().size() > 0;
    }

    private DocumentReference createFlightDocument(JsonNode flightNode, UUID flightUUID) {
        DocumentReference airlineDoc = internalFlightsRef.document(flightUUID.toString());
        Map<String, Object> flightMap = new HashMap<>();
        flightMap.put("airline", INTERNAL_AIRLINE_COLLECTION);
        flightMap.put("flightId", flightUUID.toString());
        flightMap.put("name", flightNode.get("name").asText());
        flightMap.put("location", flightNode.get("location").asText());
        flightMap.put("image", flightNode.get("image").asText());
        airlineDoc.set(flightMap);
        return airlineDoc;
    }

    private void createSeat(JsonNode seatNode, DocumentReference flightDoc, UUID flightUUID, UUID seatUUID) {
        DocumentReference seatDoc = flightDoc.collection(SEATS_COLLECTION).document(seatUUID.toString());
        Map<String, Object> seatMap = new HashMap<>();
        seatMap.put("airline", INTERNAL_AIRLINE_COLLECTION);
        seatMap.put("flightId", flightUUID.toString());
        seatMap.put("name", seatNode.get("name").asText());
        seatMap.put("time", seatNode.get("time").asText());
        seatMap.put("type", seatNode.get("type").asText());
        seatMap.put("price", seatNode.get("price").toString());
        seatMap.put("bookingReference", "");
        seatMap.put("customer", "");
        seatDoc.set(seatMap);
    }

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

    public JsonObject getFlight(String flightId) {
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
        flightObject.addProperty("airline", INTERNAL_AIRLINE_COLLECTION);
        flightObject.addProperty("flightId", snapshot.getId());
        flightObject.addProperty("name", snapshot.getString("name"));
        flightObject.addProperty("location", snapshot.getString("location"));
        flightObject.addProperty("image", snapshot.getString("image"));
        return flightObject;
    }

    public String[] getFlightTimes(String flightId) {
        // Get all the flight times from the flight
        List<QueryDocumentSnapshot> documents;
        try {
            documents = internalFlightsRef.document(flightId)
                    .collection(SEATS_COLLECTION).select("time").get().get().getDocuments();
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

    public JsonArray getAvailableSeats(String flightId, String time) {
        // Get all seats from the flight with the given time that are not booked
        Query query = internalFlightsRef.document(flightId).collection(SEATS_COLLECTION)
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


    public JsonObject getSeat(String flightId, String seatId) {
        try {
            // Get the seat from the flight
            DocumentSnapshot snapshot = internalFlightsRef.document(flightId)
                    .collection(SEATS_COLLECTION).document(seatId).get().get();
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

    public void saveBooking(Booking booking, String uid) {
        DocumentReference bookingRef = usersRef.document(uid).collection(BOOKING_COLLECTION).document(booking.getId().toString());
        booking.getTickets().forEach(ticket -> {
            DocumentReference ticketRef = bookingRef.collection("tickets").document(ticket.getTicketId().toString());
            ticketRef.set(ticket.getTicketMap());
        });
        bookingRef.set(booking.getBookingMap());
    }

    public List<Booking> getBookings(String uid) {
        try {
            DocumentReference userRef = firestore.collection(USER_COLLECTION).document(uid);
            QuerySnapshot bookingsSnapshot = userRef.collection(BOOKING_COLLECTION).get().get();
            return bookingsSnapshot.getDocuments().stream()
                    .map(this::parseBookingFromSnapshot)
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private Booking parseBookingFromSnapshot(QueryDocumentSnapshot bookingDoc) {
        UUID bookingId = UUID.fromString(bookingDoc.getId());
        LocalDateTime time = LocalDateTime.parse(bookingDoc.getString("time"));
        String customer = bookingDoc.getString("customer");
        List<Ticket> tickets = new ArrayList<>();
        try {
            QuerySnapshot ticketSnapshot = bookingDoc.getReference().collection("tickets").get().get();
            ticketSnapshot.getDocuments().forEach(ticketDoc -> tickets.add(parseTicketFromSnapshot(ticketDoc)));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return new Booking(bookingId, time, tickets, customer);
    }

    private Ticket parseTicketFromSnapshot(QueryDocumentSnapshot ticketDoc) {
        String airline = ticketDoc.getString("airline");
        UUID flightId = UUID.fromString(ticketDoc.getString("flightId"));
        UUID seatId = UUID.fromString(ticketDoc.getString("seatId"));
        UUID ticketId = UUID.fromString(ticketDoc.getId());
        String bookingReference = ticketDoc.getString("bookingReference");
        String customer = ticketDoc.getString("customer");
        return new Ticket(airline, flightId, seatId, ticketId, customer, bookingReference);
    }

    // MANAGER METHODS
    public List<Booking> getAllBookings() {
        try {
            // Create a list to store all bookings
            List<Booking> allBookings = new ArrayList<>();

            // Retrieve all user documents
            QuerySnapshot usersSnapshot = usersRef.get().get();

            // Iterate over each user document
            for (DocumentSnapshot userDoc : usersSnapshot.getDocuments()) {
                // Get the 'bookings' collection of the current user document
                CollectionReference bookingsRef = userDoc.getReference().collection(BOOKING_COLLECTION);
                // Retrieve all booking documents within the 'bookings' collection
                QuerySnapshot bookingsSnapshot = bookingsRef.get().get();

                // Parse the bookings and add them to the list
                List<Booking> userBookings = bookingsSnapshot.getDocuments().stream()
                        .map(this::parseBookingFromSnapshot).toList();
                allBookings.addAll(userBookings);
            }

            return allBookings;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<String> getBestCustomers() {
        // Get the all bookings from the database
        List<Booking> bookings = getAllBookings();

        // Create a map of customers and their number of bookings
        Map<String, Integer> customerBookings = new HashMap<>();
        bookings.forEach(booking -> {
            customerBookings.merge(booking.getCustomer(), 1, Integer::sum);
        });
        return new ArrayList<>(customerBookings.keySet());
    }

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
            if (!getSeat(quote.getFlightId().toString(), quote.getSeatId().toString())
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
                    .collection(SEATS_COLLECTION)
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
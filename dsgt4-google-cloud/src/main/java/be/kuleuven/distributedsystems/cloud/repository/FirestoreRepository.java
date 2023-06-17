package be.kuleuven.distributedsystems.cloud.repository;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
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
    private final CollectionReference usersRef;
    private final CollectionReference internalFlightsRef;

    @Autowired
    public FirestoreRepository(Firestore firestore) {
        this.firestore = firestore;
        usersRef = firestore.collection(FirestoreConstants.USER_COLLECTION);
        internalFlightsRef = firestore.collection(FirestoreConstants.AIRLINE_COLLECTION)
                .document(FirestoreConstants.INTERNAL_AIRLINE_COLLECTION)
                .collection(FirestoreConstants.FLIGHTS_AIRLINE_COLLECTION);
        loadInternalAirlineData();
    }

    public void loadInternalAirlineData() {
        System.out.println("Loading Internal Airline into Firestore");
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode data = mapper.readTree(new File(FirestoreConstants.INTERNAL_AIRLINE_DATA));
            for (JsonNode flightNode : data.get("flights")) {
                if (flightExists(flightNode.get("name").asText())) {
                    System.out.println("Flight with name " + flightNode.get("name").asText() + " already exists");
                    continue;
                }

                UUID flightUUID = UUID.randomUUID();
                DocumentReference flightDoc = createFlightDocument(flightNode, flightUUID);

                for (JsonNode seatNode : flightNode.get(FirestoreConstants.SEATS_COLLECTION)) {
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
        flightMap.put("airline", FirestoreConstants.INTERNAL_AIRLINE_COLLECTION);
        flightMap.put("flightId", flightUUID.toString());
        flightMap.put("name", flightNode.get("name").asText());
        flightMap.put("location", flightNode.get("location").asText());
        flightMap.put("image", flightNode.get("image").asText());
        airlineDoc.set(flightMap);
        return airlineDoc;
    }

    private void createSeat(JsonNode seatNode, DocumentReference flightDoc, UUID flightUUID, UUID seatUUID) {
        DocumentReference seatDoc = flightDoc.collection(FirestoreConstants.SEATS_COLLECTION).document(seatUUID.toString());
        Map<String, Object> seatMap = new HashMap<>();
        seatMap.put("airline", FirestoreConstants.INTERNAL_AIRLINE_COLLECTION);
        seatMap.put("flightId", flightUUID.toString());
        seatMap.put("name", seatNode.get("name").asText());
        seatMap.put("time", seatNode.get("time").asText());
        seatMap.put("type", seatNode.get("type").asText());
        seatMap.put("price", seatNode.get("price").toString());
        seatMap.put("bookingReference", "");
        seatMap.put("customer", "");
        seatDoc.set(seatMap);
    }

    public void saveBooking(Booking booking, String uid) {
        DocumentReference bookingRef = usersRef.document(uid).collection(FirestoreConstants.BOOKING_COLLECTION).document(booking.getId().toString());
        booking.getTickets().forEach(ticket -> {
            DocumentReference ticketRef = bookingRef.collection("tickets").document(ticket.getTicketId().toString());
            ticketRef.set(ticket.getTicketMap());
        });
        bookingRef.set(booking.getBookingMap());
    }

    public List<Booking> getBookings(String uid) {
        try {
            DocumentReference userRef = firestore.collection(FirestoreConstants.USER_COLLECTION).document(uid);
            QuerySnapshot bookingsSnapshot = userRef.collection(FirestoreConstants.BOOKING_COLLECTION).get().get();
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
                CollectionReference bookingsRef = userDoc.getReference().collection(FirestoreConstants.BOOKING_COLLECTION);
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
        bookings.forEach(booking -> customerBookings.merge(booking.getCustomer(), 1, Integer::sum));
        // TODO: Sort

        return new ArrayList<>(customerBookings.keySet());
    }

}
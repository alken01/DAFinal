package be.kuleuven.distributedsystems.cloud.repository;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
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

    private final String INTERNAL_AIRLINE_DATA = "src/main/resources/datatest.json";

    private final CollectionReference usersRef;
    private final CollectionReference internalFlightsRef;

    @Autowired
    public FirestoreRepository(Firestore firestore){
        // initialize the firestore
        this.firestore = firestore;
        // get the collections
        usersRef = firestore.collection(USER_COLLECTION);
        internalFlightsRef = firestore.collection(AIRLINE_COLLECTION)
                .document(INTERNAL_AIRLINE_COLLECTION)
                .collection(FLIGHTS_AIRLINE_COLLECTION);

        // load the internal airline data
        loadInternalAirlineData();
    }

    public void loadInternalAirlineData() {
        System.out.println("Loading Internal Airline into Firestore");
        // read the json file
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data;
        try {
            data = mapper.readTree(new File(INTERNAL_AIRLINE_DATA));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // load the data
        for (JsonNode flightNode : data.get("flights")) {
            // check if flight with this name already exists
            try {
                Query query = internalFlightsRef.whereEqualTo("name", flightNode.get("name").asText());
                List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
                if (!documents.isEmpty()) {
                    System.out.println("Flight with name " + flightNode.get("name").asText() + " already exists");
                    continue;
                }
            } catch (Exception e) {
                System.out.println("Error while checking if flight already exists");
            }

            // create the flight document
            UUID flightUUID = UUID.randomUUID();
            DocumentReference flightDoc = createFlightDocument(flightNode, flightUUID);
            // create the seats
            for (JsonNode seatNode : flightNode.get(SEATS_COLLECTION)) {
                createSeatDocument(seatNode, flightDoc);
            }
        }
    }

    private DocumentReference createFlightDocument(JsonNode flightNode, UUID flightUUID) {
        // create a new flight document
        DocumentReference airlineDoc = internalFlightsRef.document(flightUUID.toString());

        // create the flight map
        Map<String, String> flightMap = new HashMap<>();
        flightMap.put("airline", INTERNAL_AIRLINE_COLLECTION);
        flightMap.put("flightId", flightUUID.toString());
        flightMap.put("name", flightNode.get("name").asText());
        flightMap.put("location", flightNode.get("location").asText());
        flightMap.put("image", flightNode.get("image").asText());
        airlineDoc.set(flightMap);
        // return the document reference
        return airlineDoc;
    }

    private void createSeatDocument(JsonNode seatNode, DocumentReference flightDoc){
        // create a new seat document
        UUID seatUUID = UUID.randomUUID();
        DocumentReference seatDoc = flightDoc.collection(SEATS_COLLECTION).document(seatUUID.toString());
        // create the seat map
        Map<String, Object> seatMap = new HashMap<>();
        seatMap.put("name", seatNode.get("name").asText());
        seatMap.put("time", seatNode.get("time").asText());
        seatMap.put("type", seatNode.get("type").asText());
        seatMap.put("price", seatNode.get("price").toString());
        // default is empty
        seatMap.put("bookingReference", "");
        seatMap.put("customer", "");
        // save the seat
        seatDoc.set(seatMap);
    }

    // INTERNAL AIRLINE METHODS
    public JsonArray getFlights(){
        ApiFuture<QuerySnapshot> querySnapshot = internalFlightsRef.get();
        List<QueryDocumentSnapshot> documents = null;
        try {
            documents = querySnapshot.get().getDocuments();
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonArray flightsArray = new JsonArray();

        for (QueryDocumentSnapshot document : documents) {
            JsonObject flightObject = new JsonObject();
            flightObject.addProperty("flightId", document.getId());
            flightObject.addProperty("name", document.getString("name"));
            flightObject.addProperty("location", document.getString("location"));
            flightObject.addProperty("image", document.getString("image"));

            flightsArray.add(flightObject);
        }

        return flightsArray;
    }

    public JsonObject getFlight(String flightId) {
        DocumentReference flightRef = internalFlightsRef.document(flightId);
        ApiFuture<DocumentSnapshot> documentSnapshot = flightRef.get();
        DocumentSnapshot snapshot;
        try {
            snapshot = documentSnapshot.get();
        } catch (Exception e) {
            e.printStackTrace();
            return new JsonObject();
        }

        JsonObject flightObject = new JsonObject();
        flightObject.addProperty("airline", INTERNAL_AIRLINE_COLLECTION);
        flightObject.addProperty("flightId", snapshot.getId());
        flightObject.addProperty("name", snapshot.getString("name"));
        flightObject.addProperty("location", snapshot.getString("location"));
        flightObject.addProperty("image", snapshot.getString("image"));
        return flightObject;
    }


    public String[] getFlightTimes(String flightId){
        CollectionReference seatsRef = internalFlightsRef.document(flightId).collection(SEATS_COLLECTION);
        ApiFuture<QuerySnapshot> querySnapshot = seatsRef.select("time").get();
        List<QueryDocumentSnapshot> documents;
        try {
            documents = querySnapshot.get().getDocuments();
        } catch (Exception e) {
            e.printStackTrace();
            return new String[0];
        }

        Set<String> timesSet = new HashSet<>();

        for (QueryDocumentSnapshot document : documents) {
            String time = document.getString("time");
            if (time != null) {
                timesSet.add(time);
            }
        }

        return timesSet.toArray(new String[0]);
    }

    public JsonArray getAvailableSeats(String flightId, String time){
        CollectionReference seatsRef = internalFlightsRef.document(flightId).collection(SEATS_COLLECTION);
        Query query = seatsRef.whereEqualTo("time", time)
                .whereEqualTo("bookingReference", "");

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<QueryDocumentSnapshot> documents;
        try {
            documents = querySnapshot.get().getDocuments();
        } catch (Exception e) {
            e.printStackTrace();
            return new JsonArray();
        }

        JsonArray seatsArray = new JsonArray();

        for (QueryDocumentSnapshot document : documents) {
            JsonObject seatObject = new JsonObject();
            seatObject.addProperty("seatId", document.getId());
            seatObject.addProperty("name", document.getString("name"));
            seatObject.addProperty("time", document.getString("time"));
            seatObject.addProperty("type", document.getString("type"));
            seatObject.addProperty("price", document.getString("price"));

            seatsArray.add(seatObject);
        }

        return seatsArray;
    }


    public JsonObject getSeat(String flightId, String seatId) {
        DocumentReference seatRef = internalFlightsRef.document(flightId)
                .collection(SEATS_COLLECTION).document(seatId);
        ApiFuture<DocumentSnapshot> documentSnapshot = seatRef.get();
        DocumentSnapshot snapshot;
        try {
            snapshot = documentSnapshot.get();
        } catch (Exception e) {
            e.printStackTrace();
            return new JsonObject();
        }

        if (snapshot.exists()) {
            JsonObject seatObject = new JsonObject();
            seatObject.addProperty("seatId", snapshot.getId());
            seatObject.addProperty("name", snapshot.getString("name"));
            seatObject.addProperty("time", snapshot.getString("time"));
            seatObject.addProperty("type", snapshot.getString("type"));
            seatObject.addProperty("price", snapshot.getDouble("price"));
            seatObject.addProperty("bookingReference", snapshot.getString("bookingReference"));
            seatObject.addProperty("customer", snapshot.getString("customer"));

            return seatObject;
        }
        // Seat document not found
        return new JsonObject();
    }



    // BOOKING METHODS
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

            // Parse the bookings
            return bookingsSnapshot.getDocuments().stream()
                    .map(this::parseBookingFromSnapshot)
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // HELPER METHODS
    private Booking parseBookingFromSnapshot(QueryDocumentSnapshot bookingDoc) {
        // Parse the booking
        UUID bookingId = UUID.fromString(bookingDoc.getId());
        LocalDateTime time = LocalDateTime.parse(bookingDoc.getString("time"));
        String customer = bookingDoc.getString("customer");

        // Parse the tickets
        List<Ticket> tickets = new ArrayList<>();
        try {
            // Get the tickets from the booking and parse them
            QuerySnapshot ticketSnapshot = bookingDoc.getReference().collection("tickets").get().get();
            ticketSnapshot.getDocuments().forEach(ticketDoc -> tickets.add(parseTicketFromSnapshot(ticketDoc)));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        // Return the booking
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
            if (customerBookings.containsKey(booking.getCustomer())) {
                customerBookings.put(booking.getCustomer(), customerBookings.get(booking.getCustomer()) + 1);
            } else {
                customerBookings.put(booking.getCustomer(), 1);
            }
        });

        // Create a list of users
        List<String> users = new ArrayList<>();
        customerBookings.forEach((customer, role) -> users.add(customer));

        // Return the users
        return users;
    }
}

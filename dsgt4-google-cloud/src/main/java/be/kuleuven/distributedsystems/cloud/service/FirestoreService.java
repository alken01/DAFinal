package be.kuleuven.distributedsystems.cloud.service;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class FirestoreService {

    private final Firestore firestore;

    @Autowired
    public FirestoreService(Firestore firestore) {
        this.firestore = firestore;
    }

    public void saveBooking(Booking booking, String uid) {
        DocumentReference userRef = firestore.collection("users").document(uid);
        DocumentReference bookingRef = userRef.collection("bookings").document(booking.getId().toString());

        booking.getTickets().forEach(ticket -> {
            DocumentReference ticketRef = bookingRef.collection("tickets").document(ticket.getTicketId().toString());
            ticketRef.set(ticket.getTicketMap());
        });

        bookingRef.set(booking.getBookingMap());
    }


    public List<Booking> getBookings(String uid) {
        try {
            DocumentReference userRef = firestore.collection("users").document(uid);
            QuerySnapshot bookingsSnapshot = userRef.collection("bookings").get().get();

            // Parse the bookings
            return bookingsSnapshot.getDocuments().stream()
                    .map(this::parseBookingFromSnapshot)
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

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

    public List<Booking> getAllBookingsOld() {
        try {
            // Get the bookings from the database
            QuerySnapshot bookingsSnapshot = firestore.collection("bookings").get().get();
            // Parse the bookings
            return bookingsSnapshot.getDocuments().stream()
                    .map(this::parseBookingFromSnapshot)
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Booking> getAllBookings() {
        try {
            // Create a list to store all bookings
            List<Booking> allBookings = new ArrayList<>();

            // Get the reference to the 'users' collection
            CollectionReference usersRef = firestore.collection("users");
            // Retrieve all user documents
            QuerySnapshot usersSnapshot = usersRef.get().get();

            // Iterate over each user document
            for (DocumentSnapshot userDoc : usersSnapshot.getDocuments()) {
                // Get the 'bookings' subcollection of the current user document
                CollectionReference bookingsRef = userDoc.getReference().collection("bookings");
                // Retrieve all booking documents within the 'bookings' subcollection
                QuerySnapshot bookingsSnapshot = bookingsRef.get().get();

                // Parse the bookings and add them to the list
                List<Booking> userBookings = bookingsSnapshot.getDocuments().stream()
                        .map(this::parseBookingFromSnapshot)
                        .collect(Collectors.toList());
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

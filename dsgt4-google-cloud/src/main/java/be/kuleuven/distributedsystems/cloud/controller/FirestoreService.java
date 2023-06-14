package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
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

    public void saveBooking(Booking booking) {
        DocumentReference bookingRef = firestore.collection("bookings").document(booking.getId().toString());

        booking.getTickets().forEach(ticket -> {
            DocumentReference ticketRef = bookingRef.collection("tickets").document(ticket.getTicketId().toString());
            ticketRef.set(createTicketMap(ticket));
        });

        bookingRef.set(createBookingMap(booking));
    }

    private Map<String, Object> createTicketMap(Ticket ticket) {
        Map<String, Object> ticketMap = new HashMap<>();
        ticketMap.put("airline", ticket.getAirline());
        ticketMap.put("bookingReference", ticket.getBookingReference());
        ticketMap.put("seatId", ticket.getSeatId().toString());
        ticketMap.put("flightId", ticket.getFlightId().toString());
        ticketMap.put("customer", ticket.getCustomer());
        ticketMap.put("status", "booked");
        return ticketMap;
    }

    private Map<String, Object> createBookingMap(Booking booking) {
        Map<String, Object> bookingMap = new HashMap<>();
        bookingMap.put("time", LocalDateTime.now().toString());
        bookingMap.put("customer", booking.getCustomer());
        return bookingMap;
    }

    public List<Booking> getBookings(String email) {
        try {
            // Get the bookings from the database
            QuerySnapshot bookingsSnapshot = firestore.collection("bookings").whereEqualTo("customer", email).get().get();
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

    public List<Booking> getAllBookings() {
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

    public List<String> getBestCustomers() {
        try {
            // Get the bookings from the database
            QuerySnapshot bookingsSnapshot = firestore.collection("bookings").get().get();
            // Parse the bookings
            List<Booking> bookings = bookingsSnapshot.getDocuments().stream()
                    .map(this::parseBookingFromSnapshot).toList();

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
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
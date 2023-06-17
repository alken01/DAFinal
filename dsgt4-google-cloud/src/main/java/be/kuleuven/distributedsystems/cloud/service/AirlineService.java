package be.kuleuven.distributedsystems.cloud.service;

import be.kuleuven.distributedsystems.cloud.auth.SecurityFilter;
import be.kuleuven.distributedsystems.cloud.entities.*;
import be.kuleuven.distributedsystems.cloud.repository.AirlineRepository;
import be.kuleuven.distributedsystems.cloud.repository.ExternalAirlineRepository;
import be.kuleuven.distributedsystems.cloud.repository.FirestoreRepository;
import be.kuleuven.distributedsystems.cloud.repository.InternalAirlineRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AirlineService {

    private final List<AirlineRepository> airlineRepositories;
    private final FirestoreRepository firestoreRepository;

    public AirlineService(FirestoreRepository firestoreRepository,
                          InternalAirlineRepository internalAirlineRepository,
                          ExternalAirlineRepository externalAirlineRepository) {
        this.firestoreRepository = firestoreRepository;
        this.airlineRepositories = new ArrayList<>();
        this.airlineRepositories.add(internalAirlineRepository);
        this.airlineRepositories.add(externalAirlineRepository);
    }

    public JsonArray getFlights() {
        // get the flights from the airlines
        JsonArray flights = new JsonArray();
        for (AirlineRepository airlineRepository : airlineRepositories) {
            flights.addAll(airlineRepository.getFlights());
        }
        return flights;
    }


    public JsonObject getFlight(String airline, String flightId) {
        for (AirlineRepository airlineRepository : airlineRepositories) {
            if (airlineRepository.containsAirline(airline)) {
                return airlineRepository.getFlight(airline, flightId);
            }
        }
        return new JsonObject();
    }


    public String[] getFlightTimes(String airline, String flightId) {
        for (AirlineRepository airlineRepository : airlineRepositories) {
            if (airlineRepository.containsAirline(airline)) {
                String[] flightTimes = airlineRepository.getFlightTimes(airline, flightId);
                Arrays.sort(flightTimes);
                return flightTimes;
            }
        }
        return new String[0];
    }

    public JsonObject getAvailableSeats(String airline, String flightId, String time) {
        for (AirlineRepository airlineRepository : airlineRepositories) {
            if (airlineRepository.containsAirline(airline)) {
                return sortSeats(airlineRepository.getAvailableSeats(airline, flightId, time));
            }
        }
        return new JsonObject();
    }

    public JsonObject getSeat(String airline, String flightId, String seatId) {
        for (AirlineRepository airlineRepository : airlineRepositories) {
            if (airlineRepository.containsAirline(airline)) {
                return airlineRepository.getSeat(airline, flightId, seatId);
            }
        }
        return new JsonObject();
    }


    public boolean confirmQuotes(Quote[] quotes) {
        // Get the user uid and generate a booking reference
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String uid = user.getUid();
        String email = user.getEmail();
        String bookingReference = UUID.randomUUID().toString();
        LocalDateTime time = LocalDateTime.now();

        // get all the bookings
        List<Booking> bookings = new ArrayList<>();
        for (AirlineRepository airlineRepository : airlineRepositories) {
            // get all the quotes for this airline
            List<Quote> airlineQuotes = new ArrayList<>();
            for (Quote quote : quotes) {
                if (airlineRepository.containsAirline(quote.getAirline())) {
                    airlineQuotes.add(quote);
                }
            }

            // confirm the quotes for this airline and add the booking
            Booking booking = airlineRepository.confirmQuotes(airlineQuotes, email, bookingReference, time);
            bookings.add(booking);
        }


        // // get all the tickets
        List<Ticket> tickets = new ArrayList<>();
        for (Booking booking : bookings) {
            tickets.addAll(booking.getTickets());
        }
        // create a new final booking with all the tickets
        Booking booking = new Booking(UUID.fromString(bookingReference), time, tickets, email);

        // Save the booking in the firestore
        firestoreRepository.saveBooking(booking, uid);

        // Return 200 OK
        return true;
    }

    public JsonArray getBookings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Cast the authentication object to FirebaseAuthentication
        SecurityFilter.FirebaseAuthentication firebaseAuthentication = (SecurityFilter.FirebaseAuthentication) authentication;
        // Access the uid property from the user object
        String uid = firebaseAuthentication.getUid();

        // Get the bookings from the firestore
        List<Booking> bookings = firestoreRepository.getBookings(uid);

        // make a booking json array and add all the bookings to it
        JsonArray bookingArray = new JsonArray();
        for (Booking booking : bookings) {
            bookingArray.add(booking.getJsonObject(booking.getCustomer()));
        }

        // Return the bookings
        return bookingArray;
    }

    // MANAGER METHODS
    public JsonArray getAllBookings() {
        // Get the bookings from the firestore
        List<Booking> bookings = firestoreRepository.getAllBookings();

        // make a booking json array and add all the bookings to it
        JsonArray bookingArray = new JsonArray();
        for (Booking booking : bookings) {
            bookingArray.add(booking.getJsonObject(booking.getCustomer()));
        }

        // Return the bookings
        return bookingArray;
    }

    public JsonArray getBestCustomers() {
        // Get the best customers
        List<String> customers = firestoreRepository.getBestCustomers();

        // only the emails are needed
        JsonArray customerArray = new JsonArray();
        for (String customer : customers) {
            customerArray.add(customer);
        }

        // Return the bookings
        return customerArray;
    }

    // HELPER METHODS
    private JsonObject sortSeats(JsonArray seatList) {
        // group the seats based on type
        Map<String, List<JsonObject>> seatsByType = new HashMap<>();
        for (JsonElement seatElement : seatList) {
            JsonObject seatObject = seatElement.getAsJsonObject();
            String type = seatObject.get("type").getAsString();
            // create a new list if the type is not yet in the map
            if (!seatsByType.containsKey(type)) seatsByType.put(type, new ArrayList<>());
            // add the seat to the list
            seatsByType.get(type).add(seatObject);
        }

        // sort the seats within each type based on seat number
        for (List<JsonObject> seats : seatsByType.values()) {
            seats.sort(Comparator.comparing(seat -> {
                String seatNumber = seat.get("name").getAsString();
                int number = Integer.parseInt(seatNumber.replaceAll("\\D+", ""));
                String letter = seatNumber.replaceAll("\\d+", "");
                return number * 100 + letter.charAt(0);
            }));
        }

        // construct the final result in the requested format
        JsonObject result = new JsonObject();
        for (Map.Entry<String, List<JsonObject>> entry : seatsByType.entrySet()) {
            JsonArray seatArray = new JsonArray();
            // add all the seats to the array
            for (JsonObject seat : entry.getValue()) {
                seatArray.add(seat);
            }
            result.add(entry.getKey(), seatArray);
        }
        return result;
    }
}
package be.kuleuven.distributedsystems.cloud.service;

import be.kuleuven.distributedsystems.cloud.auth.SecurityFilter;
import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.User;
import be.kuleuven.distributedsystems.cloud.repository.ExternalAirlineRepository;
import be.kuleuven.distributedsystems.cloud.repository.FirestoreRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Service
public class AirlineService {

    private final FirestoreRepository firestoreRepository;
    private final ExternalAirlineRepository externalAirlineRepository;

    public AirlineService(FirestoreRepository firestoreRepository, ExternalAirlineRepository externalAirlineRepository) {
        this.firestoreRepository = firestoreRepository;
        this.externalAirlineRepository = externalAirlineRepository;
    }

    public JsonArray getFlights() {
        // get the flights from the external airlines and the internal firestore
        JsonArray externalFlightsArray = externalAirlineRepository.getFlights();
        JsonArray internalFlightsArray = firestoreRepository.getFlights();

        // combine the flights and return them
        externalFlightsArray.addAll(internalFlightsArray);
        return externalFlightsArray;
    }


    public JsonObject getFlight(@RequestParam String airline, @RequestParam String flightId) {
        JsonObject flightObject;
        if (externalAirlineRepository.isExternal(airline)) {
            flightObject = externalAirlineRepository.getFlight(airline, flightId);
        } else {
            flightObject = firestoreRepository.getFlight(flightId);
        }
        return flightObject;
    }


    public String[] getFlightTimes(@RequestParam String airline, @RequestParam String flightId) {
        String[] flightTimes;
        if (externalAirlineRepository.isExternal(airline)) {
            flightTimes = externalAirlineRepository.getFlightTimes(airline, flightId);
        } else {
            flightTimes = firestoreRepository.getFlightTimes(flightId);
        }
        // sort the flight times and return them
        Arrays.sort(flightTimes);
        return flightTimes;
    }

    public JsonObject getAvailableSeats(@RequestParam String airline,
                                        @RequestParam String flightId,
                                        @RequestParam String time) {
        JsonArray seatList;
        if (externalAirlineRepository.isExternal(airline)) {
            seatList = externalAirlineRepository.getAvailableSeats(airline, flightId, time);
        } else {
            seatList = firestoreRepository.getAvailableSeats(flightId, time);
        }
        // sort the seats and return them
        return sortSeats(seatList);
    }

    public JsonObject getSeat(@RequestParam String airline,
                              @RequestParam String flightId,
                              @RequestParam String seatId) {
        JsonObject seatList;
        if (externalAirlineRepository.isExternal(airline)) {
            seatList = externalAirlineRepository.getSeat(airline, flightId, seatId);
        } else {
            seatList = firestoreRepository.getSeat(flightId, seatId);
        }
        return seatList;
    }


    public boolean confirmQuotes(@RequestBody Quote[] quotes) {
        // Get the user uid and generate a booking reference
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String uid = user.getUid();
        String bookingReference = UUID.randomUUID().toString();

        // separate the quotes into internal and external
        List<Quote> internalQuotes = new ArrayList<>();
        List<Quote> externalQuotes = new ArrayList<>();

        for (Quote quote : quotes) {
            if (externalAirlineRepository.isExternal(quote.getAirline())) {
                externalQuotes.add(quote);
            } else {
                internalQuotes.add(quote);
            }
        }

        // get the bookings from the external airlines
        Booking bookings = externalAirlineRepository.confirmQuotes(externalQuotes, uid, bookingReference);

        // get the bookings from the internal airlines
        // Booking internalBookingsArray = firestoreRepository.confirmQuotes(internalQuotes, email, bookingReference);

        // combine the bookings
        // Booking[] bookings = new Booking[externalBookingsArray.length + internalBookingsArray.length];
        // System.arraycopy(externalBookingsArray, 0, bookings, 0, externalBookingsArray.length);
        // System.arraycopy(internalBookingsArray, 0, bookings, externalBookingsArray.length, internalBookingsArray.length);

        // Save the booking in the firestore
        firestoreRepository.saveBooking(bookings, uid);

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
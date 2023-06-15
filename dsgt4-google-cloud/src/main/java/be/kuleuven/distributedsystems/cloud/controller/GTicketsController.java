package be.kuleuven.distributedsystems.cloud.controller;
import be.kuleuven.distributedsystems.cloud.auth.SecurityFilter;
import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.User;
import be.kuleuven.distributedsystems.cloud.service.ExternalAirlineService;
import be.kuleuven.distributedsystems.cloud.service.FirestoreService;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class GTicketsController {

    private final Gson gson = new Gson();
    private final FirestoreService firestoreService;
    private final ExternalAirlineService externalAirlineService;

    public GTicketsController(FirestoreService firestoreService, ExternalAirlineService externalAirlineService) {
        this.firestoreService = firestoreService;
        this.externalAirlineService = externalAirlineService;
    }

    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        // get the flights from the external airlines
        JsonArray externalFlightsArray = externalAirlineService.getFlights();

        // convert the flights to JSON String and return it
        return ResponseEntity.ok(gson.toJson(externalFlightsArray));
    }

    @GetMapping("/getFlight")
    public ResponseEntity<String> getFlight(@RequestParam String airline, @RequestParam String flightId) {
        JsonObject flightObject = new JsonObject();
        // check if the airline is external
        if (externalAirlineService.isExternal(airline)) {
            flightObject = externalAirlineService.getFlight(airline, flightId);
        }
        // TODO: else it is internal

        // return the flight
        return ResponseEntity.ok(flightObject.toString());
    }

    @GetMapping("/getFlightTimes")
    public ResponseEntity<String[]> getFlightTimes(@RequestParam String airline, @RequestParam String flightId) {
        String[] flightTimes = new String[0];

        // check if the airline is external
        if (externalAirlineService.isExternal(airline)) {
            flightTimes = externalAirlineService.getFlightTimes(airline, flightId);
        }
        // TODO: else it is internal

        // Order the flight times and return them
        Arrays.sort(flightTimes);
        return ResponseEntity.ok(flightTimes);
    }

    @GetMapping("/getAvailableSeats")
    public ResponseEntity<String> getAvailableSeats(@RequestParam String airline,
                                                    @RequestParam String flightId,
                                                    @RequestParam String time) {
        JsonArray seatList = new JsonArray();

        // check if the airline is external
        if (externalAirlineService.isExternal(airline)) {
            seatList = externalAirlineService.getAvailableSeats(airline, flightId, time);
        }
        // TODO: else it is internal

        // sort the seats and return them
        return ResponseEntity.ok(sortSeats(seatList).toString());
    }

    @GetMapping("/getSeat")
    public ResponseEntity<String> getSeat(@RequestParam String airline,
                                          @RequestParam String flightId,
                                          @RequestParam String seatId) {
        JsonObject seatList = new JsonObject();

        // check if the airline is external
        if (externalAirlineService.isExternal(airline)) {
            seatList = externalAirlineService.getSeat(airline, flightId, seatId);
        }
        // TODO: else it is internal

        // return the seat
        return ResponseEntity.ok(seatList.toString());
    }

    @PostMapping("/confirmQuotes")
    public ResponseEntity<String> confirmQuotes(@RequestBody Quote[] quotes) {
        // Get the user uid and generate a booking reference
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String uid = user.getUid();
        String email = user.getEmail();
        String bookingReference = UUID.randomUUID().toString();

        // seperate the quotes into internal and external
        List<Quote> internalQuotes = new ArrayList<>();
        List<Quote> externalQuotes = new ArrayList<>();

        for (Quote quote : quotes) {
            if (externalAirlineService.isExternal(quote.getAirline())) {
                externalQuotes.add(quote);
            } else {
                internalQuotes.add(quote);
            }
        }

        // get the bookings from the external airlines
        Booking bookings = externalAirlineService.confirmQuotes(externalQuotes, email, bookingReference);

        // get the bookings from the internal airlines
        // Booking internalBookingsArray = firestoreService.confirmQuotes(internalQuotes, email, bookingReference);

        // combine the bookings
        // Booking[] bookings = new Booking[externalBookingsArray.length + internalBookingsArray.length];
        // System.arraycopy(externalBookingsArray, 0, bookings, 0, externalBookingsArray.length);
        // System.arraycopy(internalBookingsArray, 0, bookings, externalBookingsArray.length, internalBookingsArray.length);

        // Save the booking in the firestore
        firestoreService.saveBooking(bookings, uid);

        // Return 200 OK
        return ResponseEntity.ok().build();
    }

    @GetMapping("/getBookings")
    public ResponseEntity<String> getBookings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Cast the authentication object to FirebaseAuthentication
        SecurityFilter.FirebaseAuthentication firebaseAuthentication = (SecurityFilter.FirebaseAuthentication) authentication;
        // Access the uid property from the user object
        String uid = firebaseAuthentication.getUid();

        // Get the bookings from the firestore
        List<Booking> bookings = firestoreService.getBookings(uid);

        // make a booking json array and add all the bookings to it
        JsonArray bookingArray = new JsonArray();
        for (Booking booking : bookings) {
            bookingArray.add(booking.getJsonObject(booking.getCustomer()));
        }

        // Return the bookings
        return ResponseEntity.ok(bookingArray.toString());
    }

    @GetMapping("/getAllBookings")
    public ResponseEntity<String> getAllBookings() {
        // Get the bookings from the firestore
        List<Booking> bookings = firestoreService.getAllBookings();

        // make a booking json array and add all the bookings to it
        JsonArray bookingArray = new JsonArray();
        for (Booking booking : bookings) {
            bookingArray.add(booking.getJsonObject(booking.getCustomer()));
        }

        // Return the bookings
        return ResponseEntity.ok(bookingArray.toString());
    }

    @GetMapping("/getBestCustomers")
    public ResponseEntity<String> getBestCustomers() {
        // Get the best customers
        List<String> customers = firestoreService.getBestCustomers();

        // only the emails are needed
        JsonArray customerArray = new JsonArray();
        for (String customer : customers) {
            customerArray.add(customer);
        }

        // Return the bookings
        return ResponseEntity.ok(customerArray.toString());
    }


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
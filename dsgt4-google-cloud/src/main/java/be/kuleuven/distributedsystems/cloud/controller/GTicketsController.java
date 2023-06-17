package be.kuleuven.distributedsystems.cloud.controller;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.User;
import be.kuleuven.distributedsystems.cloud.service.AirlineService;
import com.google.gson.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GTicketsController {

    private final Gson gson = new Gson();
    private final AirlineService airlineService;

    public GTicketsController(AirlineService airlineService) {
        this.airlineService = airlineService;
    }

    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        JsonArray externalFlightsArray = airlineService.getFlights();
        return ResponseEntity.ok(gson.toJson(externalFlightsArray));
    }

    @GetMapping("/getFlight")
    public ResponseEntity<String> getFlight(@RequestParam String airline, @RequestParam String flightId) {
        JsonObject flightObject = airlineService.getFlight(airline, flightId);
        return ResponseEntity.ok(flightObject.toString());
    }

    @GetMapping("/getFlightTimes")
    public ResponseEntity<String[]> getFlightTimes(@RequestParam String airline, @RequestParam String flightId) {
        String[] flightTimes = airlineService.getFlightTimes(airline, flightId);
        return ResponseEntity.ok(flightTimes);
    }

    @GetMapping("/getAvailableSeats")
    public ResponseEntity<String> getAvailableSeats(@RequestParam String airline,
                                                    @RequestParam String flightId,
                                                    @RequestParam String time) {
        JsonObject seatList = airlineService.getAvailableSeats(airline, flightId, time);
        return ResponseEntity.ok(seatList.toString());
    }

    @GetMapping("/getSeat")
    public ResponseEntity<String> getSeat(@RequestParam String airline,
                                          @RequestParam String flightId,
                                          @RequestParam String seatId) {
        JsonObject seatList = airlineService.getSeat(airline, flightId, seatId);
        return ResponseEntity.ok(seatList.toString());
    }

    @PostMapping("/confirmQuotes")
    public ResponseEntity<String> confirmQuotes(@RequestBody Quote[] quotes) {
        if(airlineService.confirmQuotes(quotes))
            return ResponseEntity.ok().build();
        // If the quotes could not be confirmed, return a bad request
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/getBookings")
    public ResponseEntity<String> getBookings() {
        JsonArray bookings = airlineService.getBookings();
        return ResponseEntity.ok(bookings.toString());
    }

    // MANAGER METHODS
    @GetMapping("/getAllBookings")
    public ResponseEntity<String> getAllBookings() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!user.isManager()) {
            // TODO: change this to forbidden
            return ResponseEntity.ok("");
        }

        JsonArray bookings = airlineService.getAllBookings();
        return ResponseEntity.ok(bookings.toString());
    }

    @GetMapping("/getBestCustomers")
    public ResponseEntity<String> getBestCustomers() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!user.isManager()) {
            // TODO: change this to forbidden
            return ResponseEntity.ok("");
        }
        JsonArray customers = airlineService.getBestCustomers();
        return ResponseEntity.ok(customers.toString());
    }
}
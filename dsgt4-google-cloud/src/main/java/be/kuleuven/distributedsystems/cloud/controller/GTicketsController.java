package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import be.kuleuven.distributedsystems.cloud.entities.User;
import be.kuleuven.distributedsystems.cloud.manager.BookingManager;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.awt.print.Book;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/api")
public class GTicketsController {

    private final String apiKey;
    private final HashMap<String, WebClient> airlineEndpoints = new HashMap<>();
    private static ConcurrentMap<String, JsonArray> cachedFlights;
    private final Gson gson = new Gson();
    private BookingManager bookingManager;

    public GTicketsController(WebClient.Builder webClientBuilder, @Value("${api.key}") String apiKey,
                              @Value("${airline.endpoints}") String[] airlineEndpointsConfig) {
        // initialize the webclients for the airlines from the application.properties
        for (String airline : airlineEndpointsConfig) {
            // keep only the first word after the http://
            airlineEndpoints.put(airline, webClientBuilder.baseUrl(airline)
                    .build());
        }
        // set the api key from the application.properties
        this.apiKey = apiKey;

        // initialize the cachedFlights
        cachedFlights = new ConcurrentHashMap<>();
        bookingManager = new BookingManager();
    }


    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        String endpoint = UriComponentsBuilder.fromPath("/flights")
                .queryParam("key", apiKey)
                .toUriString();
        // get the flights from the airlines
        JsonArray flightsArray = new JsonArray();
        for (Map.Entry<String, WebClient> entry : airlineEndpoints.entrySet()) {
            // TODO: maybe add a timeout?
            // check if the flights are already cached
            if (cachedFlights.containsKey(entry.getKey())) {
                flightsArray.addAll(cachedFlights.get(entry.getKey()));
                continue;
            }
            flightsArray.addAll(getFlights(endpoint, entry.getValue(), entry.getKey()));
        }

        // convert the flights to JSON String and return it
        return ResponseEntity.ok(gson.toJson(flightsArray));
    }

    private JsonArray getFlights(String endpoint, WebClient webClient, String airline) {
        // get the flights from the airline and parse the flights to a JSON array
        JsonArray flightsArray = getResponse(endpoint, webClient)
                .getAsJsonObject("_embedded")
                .getAsJsonArray("flights");

        // cache the flights and return them
        cachedFlights.put(airline, flightsArray);

        return flightsArray;
    }

    @GetMapping("/getFlight")
    public ResponseEntity<String> getFlight(@RequestParam String airline, @RequestParam String flightId) {
        // get the url in the cachedFlights
        String flightEndpoint = getFlightLink(airline, flightId, "self");

        // get the flight from the url
        JsonObject flightsObject = getResponse(flightEndpoint, airlineEndpoints.get(airline));

        // return the flight
        return ResponseEntity.ok(flightsObject.toString());
    }

    @GetMapping("/getFlightTimes")
    public ResponseEntity<String[]> getFlightTimes(@RequestParam String airline, @RequestParam String flightId) {
        // get the url in the cachedFlights
        String flightURL = getFlightLink(airline, flightId, "times");

        // get the flight times from the flight as a string array
        JsonArray stringList = getResponse(flightURL, airlineEndpoints.get(airline))
                .getAsJsonObject("_embedded")
                .getAsJsonArray("stringList");

        String[] stringArray = new String[stringList.size()];
        for (int i = 0; i < stringList.size(); i++) {
            stringArray[i] = stringList.get(i).getAsString();
        }

        // Order the flight times and return them
        Arrays.sort(stringArray);
        return ResponseEntity.ok(stringArray);
    }

    @GetMapping("/getAvailableSeats")
    public ResponseEntity<String> getAvailableSeats(@RequestParam String airline,
                                                    @RequestParam String flightId, @RequestParam String time) {
        // get the URL in the cachedFlights
        String seatsURL = getFlightLink(airline, flightId, "seats").replace("{time}", time);

        // get the seats from the URL
        JsonArray seatList = getResponse(seatsURL, airlineEndpoints.get(airline))
                .getAsJsonObject("_embedded")
                .getAsJsonArray("seats");

        // group the seats based on type
        Map<String, List<JsonObject>> seatsByType = new HashMap<>();
        for (JsonElement seatElement : seatList) {
            JsonObject seatObject = seatElement.getAsJsonObject();
            String type = seatObject.get("type").getAsString();
            // create a new list if the type is not yet in the map
            if (!seatsByType.containsKey(type)) {
                seatsByType.put(type, new ArrayList<>());
            }
            // add the seat to the list
            seatsByType.get(type).add(seatObject);
        }

        // sort the seats within each type based on name
        for (List<JsonObject> seats : seatsByType.values()) {
            seats.sort(Comparator.comparing(seat -> seat.get("name").getAsString()));
        }

        // construct the final result in the requested format
        JsonObject result = new JsonObject();
        for (Map.Entry<String, List<JsonObject>> entry : seatsByType.entrySet()) {
            List<JsonObject> seats = entry.getValue();
            JsonArray seatArray = new JsonArray();
            // add all the seats to the array
            for (JsonObject seat : seats) seatArray.add(seat);
            result.add(entry.getKey(), seatArray);
        }

        // return the formatted seat data
        return ResponseEntity.ok(gson.toJson(result));
    }


    @GetMapping("/getSeat")
    public ResponseEntity<String> getSeat(@RequestParam String airline, @RequestParam String flightId,
                                          @RequestParam String seatId) {
        // get the seat url
        String url = UriComponentsBuilder.fromPath("/flights/{flightId}/seats/{seatId}")
                .queryParam("key", apiKey)
                .buildAndExpand(flightId, seatId)
                .toUriString();

        // get the seat from the url
        JsonObject seat = getResponse(url, airlineEndpoints.get(airline));

        System.out.println("Ticket from URL: " + url);

        // return the seat
        return ResponseEntity.ok(seat.toString());
    }

    @PostMapping("/confirmQuotes")
    public ResponseEntity<String> confirmQuotes(@RequestBody Quote[] quotes) throws IOException, InterruptedException {
        // Get the user email and generate a booking reference
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        String bookingReference = UUID.randomUUID().toString();

        // Check if ALL the tickets are still available
        if(!ticketsAvailable(quotes)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // Reserve all the tickets
        Booking booking = bookTickets(quotes, email, bookingReference);

        // TODO: save the booking in the firestore
        //  Something like this:
        // BookingManager.saveBooking(booking);

        // Return 200 OK
        return ResponseEntity.ok().build();
    }

    private boolean ticketsAvailable(Quote[] quotes){
        for (Quote quote : quotes) {
            // Get the URL to check if they are still available
            String getTicketURL = UriComponentsBuilder.fromPath("/flights/{flightId}/seats/{seatId}/ticket")
                    .queryParam("key", apiKey)
                    .buildAndExpand(quote.getFlightId(), quote.getSeatId())
                    .toUriString();

            // Get the airline endpoint
            WebClient airlineEndpoint = airlineEndpoints.get(quote.getAirline());

            // Call the URL and get the response
            JsonObject getTicket = getResponse(getTicketURL, airlineEndpoint);

            // Check if the ticket is available
            if(!getTicket.equals(new JsonObject())) return false;
        }
        return true;
    }

    private Booking bookTickets(Quote[] quotes, String email, String bookingReference){
        List<Ticket> tickets = new ArrayList<>();
        for (Quote quote : quotes) {
            // Get the URL
            String putTicketURL = UriComponentsBuilder.fromPath("/flights/{flightId}/seats/{seatId}/ticket")
                    .queryParam("customer", email)
                    .queryParam("bookingReference", bookingReference)
                    .queryParam("key", apiKey)
                    .buildAndExpand(quote.getFlightId(), quote.getSeatId())
                    .toUriString();

            // Get the airline endpoint
            WebClient airlineEndpoint = airlineEndpoints.get(quote.getAirline());

            // Reserve all the seats
            putRequest(putTicketURL, airlineEndpoint, new JsonObject());

            // Create a ticket and add it to the list
            Ticket ticket = quote.getTicket(email, bookingReference);
            tickets.add(ticket);
        }
        return new Booking(UUID.fromString(bookingReference), LocalDateTime.now(), tickets, email);
    }


    @GetMapping("/getBookings")
    public ResponseEntity<String> getBookings() {
        // Get the user email
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // Get the bookings from the firestore
        // List<Booking> bookings = BookingManager.getBookings(email);
        //
        // // Return the bookings
        // return ResponseEntity.ok(gson.toJson(bookings));

        return null;
    }

    private JsonObject getResponse(String endpoint, WebClient webClient) {
        // backoff retry mechanism
        int delayMs = 10;
        int maxRetries = 5;

        for (int retries = 0; retries < maxRetries; retries++) {
            // Make the API request to get the flights from the airline
            try {
                String response = webClient.get()
                        .uri(endpoint)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                System.out.println("Calling " + endpoint);
                return JsonParser.parseString(response).getAsJsonObject();
            } catch (Exception e) {
                // Wait before making the next retry
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Fallback mechanism
        System.out.println("GET: Falling back to empty response");
        return new JsonObject();
    }

    private void putRequest(String endpoint, WebClient webClient, JsonObject requestBody) {
        // Backoff retry mechanism
        int delayMs = 100;
        int maxRetries = 5;

        for (int retries = 0; retries < maxRetries; retries++) {
            // Make the API request to update the ticket
            try {
                webClient.put()
                        .uri(endpoint)
                        .body(Mono.just(requestBody.toString()), String.class)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
                System.out.println("Calling " + endpoint);
                return;
            } catch (Exception e) {
                // Wait before making the next retry
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Fallback mechanism
        System.out.println("PUT: Failed to update the ticket after multiple retries.");
    }


    private static String getFlightLink(String airline, String flightId, String field) {
        // check if the flight is cached
        for (Map.Entry<String, JsonArray> entry : cachedFlights.entrySet()) {
            // get the flights from the airline
            JsonArray flights = entry.getValue();
            // iterate over the flights
            for (JsonElement flightElement : flights) {
                // get the flight data
                JsonObject flight = flightElement.getAsJsonObject();
                String flightAirline = flight.get("airline").getAsString();
                String flightFlightId = flight.get("flightId").getAsString();
                // check if the flight matches
                if (flightAirline.equals(airline) && flightFlightId.equals(flightId)) {
                    // return the link
                    try {
                        return flight.getAsJsonObject("_links")
                                .getAsJsonObject(field)
                                .get("href").getAsString();
                    } catch (NullPointerException e) {
                        // the link is not available
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
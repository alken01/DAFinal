package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import be.kuleuven.distributedsystems.cloud.entities.User;
import be.kuleuven.distributedsystems.cloud.manager.BookingManager;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/api")
public class GTicketsController {

    private final String apiKey;
    private final Gson gson = new Gson();
    private static ConcurrentMap<String, JsonArray> cachedFlights;
    private final HashMap<String, WebClient> airlineEndpoints = new HashMap<>();

    private static List<Quote> allquotes = new ArrayList<>();

    private BookingManager bookingManager;
    private Booking booking;
    private static List<Ticket> alltickets = new ArrayList<>();
    private final String email;

    private final List<Booking> bookingList = new ArrayList<>();
    private WebClient.Builder wCB;

    public GTicketsController(WebClient.Builder webClientBuilder,  @Value("${api.key}") String apiKey,
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
        email = "cpc@gmail.com";
        wCB = WebClient.builder();
    }


    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        String endpoint = "/flights?key=" + apiKey;
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
            if (!seatsByType.containsKey(type)) {
                seatsByType.put(type, new ArrayList<>());
            }
            seatsByType.get(type).add(seatObject);
        }

        // sort the seats within each type based on name (if needed)
        for (List<JsonObject> seats : seatsByType.values()) {
            seats.sort(Comparator.comparing(o -> o.get("name").getAsString()));
        }

        // construct the final result in the requested format
        JsonObject result = new JsonObject();
        for (Map.Entry<String, List<JsonObject>> entry : seatsByType.entrySet()) {
            String type = entry.getKey();
            List<JsonObject> seats = entry.getValue();
            JsonArray seatArray = new JsonArray();
            for (JsonObject seat : seats) {
                seatArray.add(seat);
            }
            result.add(type, seatArray);
        }

        // return the formatted seat data
        return ResponseEntity.ok(gson.toJson(result));
    }

    @GetMapping("/getSeat")
    public ResponseEntity<String> getSeat(@RequestParam String airline, @RequestParam String flightId,
                                          @RequestParam String seatId) {
        // get the seat url
        String seatURL = "/flights/" + flightId + "/seats/" + seatId;

        // get the seat from the url
        JsonObject seat = getResponse(seatURL, airlineEndpoints.get(airline));


        //create quote
        BookingManager.createQuote(airline, flightId, seatId);

        // return the seat
        return ResponseEntity.ok(seat.toString());
    }


    //For this method i tried to follow this website: https://www.baeldung.com/java-httpclient-post

    @PostMapping("/confirmQuotes")
    public ResponseEntity<String> confirmQuotes(@RequestBody Quote[] quotes) throws IOException, InterruptedException {
        //use urls to get tickets and populate them with the quotes; create the booking for the tickets
        List<JsonObject> ticketList = new ArrayList<JsonObject>();
        System.out.println(quotes.length);

        ArrayList<Ticket> tickets = new ArrayList<>();
        // get the tickets from the URL
        for (Quote quote : quotes) {
            UUID ticketUUID = UUID.randomUUID();
            String ticketURL = "/flights/" + quote.getFlightId().toString() + "/seats/" + quote.getSeatId().toString() + "/ticket?key=" + apiKey;

            // Create the URI with the necessary parameters
            URI putTicketUri = UriComponentsBuilder.fromUriString(ticketURL)
                    .queryParam("customer", email)
                    .queryParam("bookingReference", UUID.randomUUID().toString())
                    .build()
                    .toUri();

            // Send the PUT request
            Ticket ticket = this.wCB
                    .baseUrl(quote.getAirline())
                    .build()
                    .put()
                    .uri(putTicketUri)
                    .retrieve()
                    .bodyToMono(Ticket.class)
                    .block();

            if (ticket != null) {
                tickets.add(ticket);
                JsonObject ticketJsonObject = new JsonObject();
                ticketJsonObject.addProperty("ticketId", ticket.getTicketId().toString()); // Adjust the property name as per your Ticket class
                ticketList.add(ticketJsonObject);
                System.out.println("Ticket from URL: " + ticketJsonObject);
            }
        }
        booking = BookingManager.createBooking(tickets, email);
        bookingList.add(booking);
        alltickets = tickets;
        return ResponseEntity.noContent().build();
    }
    //I think the problem is accessing the endpoint, for some reason i dont haave permission and get error 403





    //Currently working on this, i think the problem may be with the way i am passing the bookings but they do not show up
    //The method is being called as can be seen by the print statements, but the bookings do not appear
    @GetMapping("/getBookings")
    public ResponseEntity<String> getBookings() {
        booking = this.booking;
        // Create a JSONObject for the booking
        JsonObject bookingObject = new JsonObject();
        bookingObject.addProperty("id", booking.getId().toString());
        bookingObject.addProperty("time", booking.getTime().toString());

        List<Ticket> tickets = booking.getTickets();

        // Create a JSONArray for the tickets
        JsonArray ticketsArray = new JsonArray();
        for (Ticket ticket : tickets) {
            JsonObject ticketObject = new JsonObject();
            ticketObject.addProperty("airline", ticket.getAirline());
            ticketObject.addProperty("flightId", ticket.getFlightId().toString());
            ticketObject.addProperty("seatId", ticket.getSeatId().toString());
            ticketObject.addProperty("ticketId", ticket.getTicketId().toString());
            ticketObject.addProperty("customer", email);
            ticketsArray.add(ticketObject);
        }

        bookingObject.add("tickets", ticketsArray);
        bookingObject.addProperty("customer", email);
        // Create the bookings array and add the booking object
        JsonArray bookingsArray = new JsonArray();
        bookingsArray.add(bookingObject);

        // Create Gson instance
        System.out.println("test" + gson.toJson(bookingsArray));

        // Convert the booking object to JSON
        return ResponseEntity.ok(gson.toJson(bookingsArray));
    }



    // Helper methods

    private JsonArray getFlights(String endpoint, WebClient webClient, String airline) {
        // get the flights from the airline
        JsonObject flightsObject = getResponse(endpoint, webClient);

        // parse the flights to a JSON array
        JsonArray flightsArray = flightsObject.getAsJsonObject("_embedded").getAsJsonArray("flights");

        // cache the flights and return them

        cachedFlights.put(airline, flightsArray);

        return flightsArray;
    }

    private JsonObject getResponse(String endpoint, WebClient webClient) {
        // Exponential backoff retry mechanism
        long delayMs = 10;
        int maxRetries = 5;
        long maxDelayMs = 60000;

        for (int retries = 0; retries < maxRetries; retries++) {
            try {
                // Make the API request to get the flights from the airline
                String response = webClient.get().uri(endpoint).retrieve().bodyToMono(String.class).block();
                return JsonParser.parseString(response).getAsJsonObject();
            } catch (Exception e) {
                // Wait before making the next retry
                delayMs = Math.min(delayMs * 2, maxDelayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Fallback mechanism
        return new JsonObject();
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
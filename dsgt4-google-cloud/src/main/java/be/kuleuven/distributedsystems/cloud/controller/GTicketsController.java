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
    private User user;

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
        //get all the quotes now to use them in the next method
        allquotes = BookingManager.getAllQuotes();
        // format quotes for the API
        List<Map<String, String>> formattedQuotes = new ArrayList<>();
        for (Quote quote : quotes) {
            Map<String, String> formattedQuote = new HashMap<>();
            formattedQuote.put("airline", quote.getAirline());
            formattedQuote.put("flightId", quote.getFlightId().toString());
            formattedQuote.put("seatId", quote.getSeatId().toString());
            formattedQuotes.add(formattedQuote);
        }
        System.out.println("Formatted Quotes: " + formattedQuotes);

        //make POST request
        String confirmQuotesEndpoint = "http://localhost:8080/api/confirmQuotes?key=" + apiKey;
        // The email is hardcoded for now for the authentication, but it still does not work, i think this should be handled in auth part
        HttpClient httpClient = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                "cpc@gmail.com",
                                "password".toCharArray());
                    }
                })
                .build();
        Gson gson = new Gson();
        //this just prepares the request body
        String requestBody = gson.toJson(formattedQuotes);
        System.out.println("Request Body: " + requestBody);
        //prepares the request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(confirmQuotesEndpoint))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        //gets the response from the server
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Response: " + response);
        //check response
        if (response.statusCode() == 204) {
            System.out.println("Quotes confirmed successfully.");
        } else {
            System.out.println("Failed to confirm quotes. Status code: " + response.statusCode());
        }
        return ResponseEntity.noContent().build();
    }
    //I think the problem is accessing the endpoint, for some reason i dont haave permission and get error 403





    //Currently working on this, i think the problem may be with the way i am passing the bookings but they do not show up
    //The method is being called as can be seen by the print statements, but the bookings do not appear
    @GetMapping("/getBookings")
    public ResponseEntity<String> getBookings() {
        String email = "cpc@gmail.com";
        booking = BookingManager.createBooking(allquotes, email);
        System.out.println("Booking added: " + booking.getCustomer());

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
        System.out.println(bookingObject.toString());
        // Create the bookings array and add the booking object
        JsonArray bookingsArray = new JsonArray();
        bookingsArray.add(bookingObject);
        System.out.println(bookingsArray.toString());

        // Create Gson instance
        Gson gson = new Gson();
        System.out.println("test" + gson.toJson(bookingsArray));

        // Convert the booking object to JSON and return
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
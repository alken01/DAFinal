package be.kuleuven.distributedsystems.cloud.repository;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class ExternalAirlineRepository {

    private final int MAX_RETRIES = 5;
    private final int WAIT_MS = 100;
    private final String apiKey;
    private final HashMap<String, WebClient> airlineEndpoints = new HashMap<>();

    @Autowired
    public ExternalAirlineRepository(WebClient.Builder webClientBuilder, @Value("${api.key}") String apiKey,
                                  @Value("${airline.endpoints}") String[] airlineEndpointsConfig) {
        // initialize the webclients for the airlines from the application.properties
        for (String airline : airlineEndpointsConfig) {
            // keep only the first word after the http://
            airlineEndpoints.put(airline, webClientBuilder.baseUrl(airline)
                    .build());
        }
        // set the api key from the application.properties
        this.apiKey = apiKey;
    }

    public JsonArray getAvailableSeats(String airline, String flightId, String time) {
        // get the URL
        String seatsURL = UriComponentsBuilder.fromPath("/flights/{flightId}/seats")
                .queryParam("time", time)
                .queryParam("available", "true")
                .queryParam("key", apiKey)
                .buildAndExpand(flightId)
                .toUriString();

        // get the seats from the URL
        return getResponse(seatsURL, airlineEndpoints.get(airline))
                .getAsJsonObject("_embedded")
                .getAsJsonArray("seats");
    }

    public JsonArray getFlights() {
        // get the url
        String endpoint = UriComponentsBuilder.fromPath("/flights")
                .queryParam("key", apiKey)
                .toUriString();

        // get the flights from the airlines
        JsonArray flightsArray = new JsonArray();
        for (Map.Entry<String, WebClient> entry : airlineEndpoints.entrySet()) {
            JsonArray flightArray = getResponse(endpoint, entry.getValue())
                    .getAsJsonObject("_embedded")
                    .getAsJsonArray("flights");
            flightsArray.addAll(flightArray);
        }
        return flightsArray;
    }

    public JsonObject getSeat(String airline, String flightId, String seatId) {
        // get the seat url
        String url = UriComponentsBuilder.fromPath("/flights/{flightId}/seats/{seatId}")
                .queryParam("key", apiKey)
                .buildAndExpand(flightId, seatId)
                .toUriString();

        // get the seat from the url
        return getResponse(url, airlineEndpoints.get(airline));
    }

    public JsonObject getFlight(String airline, String flightId) {
        // get the url
        String flightURL = UriComponentsBuilder.fromPath("/flights/{flightId}")
                .queryParam("key", apiKey)
                .buildAndExpand(flightId)
                .toUriString();

        // get the flight from the url
        return getResponse(flightURL, airlineEndpoints.get(airline));
    }

    public String[] getFlightTimes(String airline, String flightId) {
        // get the url
        String timeURL = UriComponentsBuilder.fromPath("/flights/{flightId}/times")
                .queryParam("key", apiKey)
                .buildAndExpand(flightId)
                .toUriString();

        // get the flight times from the flight as a string array
        JsonArray stringList = getResponse(timeURL, airlineEndpoints.get(airline))
                .getAsJsonObject("_embedded")
                .getAsJsonArray("stringList");

        String[] stringArray = new String[stringList.size()];
        for (int i = 0; i < stringList.size(); i++) {
            stringArray[i] = stringList.get(i).getAsString();
        }

        return stringArray;
    }

    public Booking confirmQuotes(List<Quote> quotes, String email, String bookingReference, LocalDateTime time) {
        // Check if ALL the tickets are still available
        if (!ticketsAvailable(quotes)) return null;

        // Reserve all the tickets
        return bookTickets(quotes, email, bookingReference, time);
    }

    // HELPER METHODS
    public boolean ticketsAvailable(List<Quote> quotes) {
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
            if (!getTicket.equals(new JsonObject())) return false;
        }
        return true;
    }
    public Booking bookTickets(List<Quote> quotes, String email, String bookingReference, LocalDateTime time) {
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

        return new Booking(UUID.fromString(bookingReference), time, tickets, email);
    }

    private JsonObject getResponse(String endpoint, WebClient webClient) {
        if (webClient == null || endpoint == null) return new JsonObject();

        // backoff retry mechanism
        for (int retries = 0; retries < MAX_RETRIES; retries++) {
            // Make the API request to get the flights from the airline
            try {
                String response = webClient.get()
                        .uri(endpoint)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                if (response != null) {
                    return JsonParser.parseString(response).getAsJsonObject();
                }
            } catch (Exception e) {
                // Wait before making the next retry
                try {
                    Thread.sleep(WAIT_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Fallback mechanism
        System.out.println("GET: Falling back to empty response for " + endpoint);
        return new JsonObject();
    }
    private void putRequest(String endpoint, WebClient webClient, JsonObject requestBody) {
        // Backoff retry mechanism
        for (int retries = 0; retries < MAX_RETRIES; retries++) {
            // Make the API request to update the ticket
            try {
                webClient.put()
                        .uri(endpoint)
                        .body(Mono.just(requestBody.toString()), String.class)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
                return;
            } catch (Exception e) {
                // Wait before making the next retry
                try {
                    Thread.sleep(WAIT_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Fallback mechanism
        System.out.println("PUT: Failed to update the ticket after multiple retries.");
    }

    public boolean isExternal(String airline) {
        return airlineEndpoints.containsKey(airline);
    }
}

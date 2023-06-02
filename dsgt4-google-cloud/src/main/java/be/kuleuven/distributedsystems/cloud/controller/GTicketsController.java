package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.manager.QuoteManager;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

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

    private static List<Quote> quotes = new ArrayList<>();
    private QuoteManager quoteManager;

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
        quoteManager = new QuoteManager();
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
        QuoteManager.createQuote(airline, flightId, seatId);

        // return the seat
        return ResponseEntity.ok(seat.toString());
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
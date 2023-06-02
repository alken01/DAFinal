package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.manager.QuoteManager;
import com.google.gson.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@RestController
@RequestMapping("/api")
public class GTicketsController {
    // TODO: Hide the API key in a configuration file
    // the url to get the flights from the airlines
    private final String reliableAirlineEndpoint = "http://reliable.westeurope.cloudapp.azure.com";
    private final String unreliableAirlineEndpoint = "http://unreliable.eastus.cloudapp.azure.com";
    private final String apiKey = "Iw8zeveVyaPNWonPNaU0213uw3g6Ei";
    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    private final Gson gson = new Gson();
    private static ConcurrentMap<String, JsonArray> cachedFlights;

    private static List<Quote> quotes = new ArrayList<>();
    private QuoteManager quoteManager;

    public GTicketsController() {
        // initialize the cache
        cachedFlights = new ConcurrentHashMap<>();
        getFlights();

        quoteManager = new QuoteManager();
    }

    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        // check if the flights are already cached
        // TODO: maybe add a timeout? if more flights are added in the external API
        //  the cache will not be updated as it is currently implemented
        if (cachedFlights.containsKey("flights")) {
            // return the cached flights
            return ResponseEntity.ok(gson.toJson(cachedFlights.get("flights")));
        }

        String reliableUrl = reliableAirlineEndpoint + "/flights?key=" + apiKey;
        String unreliableUrl = unreliableAirlineEndpoint + "/flights?key=" + apiKey;

        // get the flights from the airline
        JsonArray reliableFlightsArray = getFlights(reliableUrl);
        JsonArray unreliableFlightsArray = getFlights(unreliableUrl);

        // merge the flights from the two airlines
        JsonArray flightsArray = new JsonArray();
        flightsArray.addAll(reliableFlightsArray);
        flightsArray.addAll(unreliableFlightsArray);

        // convert the flights to JSON String and return it
        return ResponseEntity.ok(gson.toJson(flightsArray));
    }

    @GetMapping("/getFlight")
    public ResponseEntity<String> getFlight(@RequestParam String airline, @RequestParam String flightId) {
        // get the url in the cachedFlights
        String flightURL = "http://" + airline + getFlightLink(airline, flightId, "self");

        // get the flight from the url
        JsonObject flightsObject = getResponse(flightURL);

        // return the flight
        return ResponseEntity.ok(flightsObject.toString());
    }

    @GetMapping("/getFlightTimes")
    public ResponseEntity<String[]> getFlightTimes(@RequestParam String airline, @RequestParam String flightId) {
        // get the url in the cachedFlights
        String flightURL = "http://" + airline + getFlightLink(airline, flightId, "times");

        // get the flight times from the flight as a string array
        JsonObject json = getResponse(flightURL);
        JsonArray stringList = json.getAsJsonObject("_embedded").getAsJsonArray("stringList");

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
        String seatsURL = "http://" + airline + getFlightLink(airline, flightId, "seats").replace("{time}", time);
        System.out.println("Seats URL: " + seatsURL);

        // get the seats from the URL
        JsonArray seatList = getResponse(seatsURL).getAsJsonObject("_embedded").getAsJsonArray("seats");

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

        System.out.println("Formatted Seat Data: " + result);

        // return the formatted seat data
        return ResponseEntity.ok(gson.toJson(result));
    }

    @GetMapping("/getSeat")
    public ResponseEntity<String> getSeat(@RequestParam String airline, @RequestParam String flightId,
                                          @RequestParam String seatId) {
        // get the seat url
        String seatURL = "http://" + airline + "/flights/" + flightId + "/seats/" + seatId;

        // get the seat from the url
        JsonObject seat = getResponse(seatURL);
        System.out.println("Seat: " + seat);


        //create quote
        QuoteManager.createQuote(airline, flightId, seatId);


        // return the seat
        return ResponseEntity.ok(seat.toString());
    }

    // Helper methods

    private JsonArray getFlights(String url) {
        // get the flights from the airline
        JsonObject flightsObject = getResponse(url);

        // parse the flights to a JSON array
        JsonArray flightsArray = flightsObject.getAsJsonObject("_embedded").getAsJsonArray("flights");

        // cache the flights and return them
        cachedFlights.put(url, flightsArray);
        return flightsArray;
    }

    private JsonObject getResponse(String url) {
        // Exponential backoff retry mechanism
        long delayMs = 10;
        int maxRetries = 5;
        long maxDelayMs = 60000;

        for (int retries = 0; retries < maxRetries; retries++) {
            try {
                // Make the API request to get the flights from the airline
                ResponseEntity<String> reliableResponse = restTemplate.getForEntity(url, String.class);
                return JsonParser.parseString(reliableResponse.getBody()).getAsJsonObject();
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                // Wait before making the next retry
                delayMs = Math.min(delayMs * 2, maxDelayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                // Handle other exceptions
                e.printStackTrace();
                break;
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
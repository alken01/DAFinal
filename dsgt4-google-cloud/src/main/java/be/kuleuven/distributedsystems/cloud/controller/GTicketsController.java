package be.kuleuven.distributedsystems.cloud.controller;

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
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@RestController
@RequestMapping("/api")
public class GTicketsController {
    private final String reliableAirlineEndpoint = "http://reliable.westeurope.cloudapp.azure.com";
    private final String unreliableAirlineEndpoint = "http://unreliable.eastus.cloudapp.azure.com";
    // TODO: Hide the API key in a configuration file
    private final String apiKey = "Iw8zeveVyaPNWonPNaU0213uw3g6Ei";
    private final RestTemplate restTemplate;
    private final Gson gson;
    private static ConcurrentMap<String, JsonArray> cachedFlights;

    public GTicketsController() {
        restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        // initialize the cache
        this.cachedFlights = new ConcurrentHashMap<>();
        getFlights();
    }

    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        // the url to get the flights from the airlines
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

    private JsonArray getFlights(String url) {
        // get the flights from the airline
        JsonObject flightsObject = getResponse(url);

        // parse the flights to a JSON array
        JsonArray flightsArray = flightsObject.getAsJsonObject("_embedded").getAsJsonArray("flights");

        // cache the flights and return them
        cachedFlights.put(url, flightsArray);
        return flightsArray;
    }

    // Helper method to make sure we get the response from the airline
    private JsonObject getResponse(String url) {
        // Exponential backoff retry mechanism
        long delayMs = 10; // initial delay
        int maxRetries = 5;

        for (int retries = 0; retries < maxRetries; retries++) {
            try {
                // Make the API request to get the flights from the airline
                ResponseEntity<String> reliableResponse = restTemplate.getForEntity(url, String.class);
                return JsonParser.parseString(reliableResponse.getBody()).getAsJsonObject();
            }
            catch (HttpClientErrorException | HttpServerErrorException e) {
                System.out.println("Retrying to get the flights from the airline");

                // Wait before making the next retry
                long maxDelayMs = 60000;
                delayMs = Math.min(delayMs * 2, maxDelayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                System.out.println(e);
                // Handle other exceptions
                e.printStackTrace();
                break;
            }
        }

        // Fallback mechanism
        return new JsonObject();
    }

    @GetMapping("/getFlight")
    public ResponseEntity<String> getFlight(@RequestParam String airline, @RequestParam String flightId) {
        // get the url in the cachedFlights
        String flightURL = "http://" + airline + getFlightLink(airline, flightId, "self");

        // if the flightURL is null, the flight is not found
        if (flightURL == null) return ResponseEntity.notFound().build();

        // get the flight from the url
        JsonObject flightsObject = getResponse(flightURL);

        // return the flight
        return ResponseEntity.ok(flightsObject.toString());
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
        // Flight not found or link not available
        return null;
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
        // get the url in the cachedFlights
        String flightURL = airline + getFlightLink(airline, flightId, "seats");

        // replace time with time
        flightURL = flightURL.replace("{time}", time);
        System.out.println("Flight URL: " + flightURL);

        // get the flight from the url
        JsonObject flightJson = getResponse(flightURL);

        // return the flight
        return ResponseEntity.ok(flightJson.getAsString());
    }
}
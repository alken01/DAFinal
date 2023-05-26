package be.kuleuven.distributedsystems.cloud.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.google.gson.Gson;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@RestController
@RequestMapping("/api")
public class GTicketsController {
    private final String reliableAirlineEndpoint = "http://reliable.westeurope.cloudapp.azure.com";
    private final String unreliableAirlineEndpoint = "http://unreliable.eastus.cloudapp.azure.com";
    // TODO: Hide the API key in a configuration file
    private final String apiKey = "Iw8zeveVyaPNWonPNaU0213uw3g6Ei";
    private final Duration maxCacheDuration = Duration.ofMinutes(5);

    private final RestTemplate restTemplate;
    private final Gson gson;
    private final ConcurrentMap<String, JsonArray> cachedFlights;

    public GTicketsController() {
        restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.cachedFlights = new ConcurrentHashMap<>();
    }

    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        // the url to get the flights from the airlines
        String reliableUrl = reliableAirlineEndpoint + "/flights?key=" + apiKey;
        String unreliableUrl = unreliableAirlineEndpoint + "/flights?key=" + apiKey;

        // get the flights from the airline
        JsonArray reliableFlightsArray = getFlightJSONArray(reliableUrl);
        JsonArray unreliableFlightsArray = getFlightJSONArray(unreliableUrl);

        // merge the flights from the two airlines
        JsonArray flightsArray = new JsonArray();
        for (JsonElement flight : reliableFlightsArray) flightsArray.add(flight);
        for (JsonElement flight : unreliableFlightsArray) flightsArray.add(flight);

        // convert the flights to JSON String and return it
        String flightsJson = gson.toJson(flightsArray);
        return ResponseEntity.ok(flightsJson);
    }

    private JsonArray getFlightJSONArray(String url) {
        // TODO: Make sure that it is okay to cache the flights,
        //  if new flights are added to the database, the cache should be invalidated
        // check if the flights are already cached
        if (cachedFlights.containsKey(url)) {
            return cachedFlights.get(url);
        }
        // exponential backoff retry mechanism
        long delayMs = 10; // initial delay
        int maxRetries = 5;

        for(int retries = 0; retries < maxRetries; retries++) {
            try {
                // Make the API request to get the flights from the airline
                ResponseEntity<String> reliableResponse = restTemplate.getForEntity(url, String.class);
                String json = reliableResponse.getBody();

                // Parse the JSON string to get the flights
                JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
                JsonArray flightsArray = jsonObject.getAsJsonObject("_embedded").getAsJsonArray("flights");
                // Success: cache the data and return it
                cachedFlights.put(url, flightsArray);
                return flightsArray;

            } catch (HttpClientErrorException | HttpServerErrorException e) {
                // wait before making the next retry
                long maxDelayMs = 60000;
                delayMs = Math.min(delayMs * 2, maxDelayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                // other exceptions
                e.printStackTrace();
                break;
            }
        }
        // Fallback mechanism: return the cached data if present
        return cachedFlights.getOrDefault(url, new JsonArray());
    }
}

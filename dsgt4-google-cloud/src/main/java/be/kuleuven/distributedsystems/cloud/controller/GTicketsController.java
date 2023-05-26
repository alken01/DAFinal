package be.kuleuven.distributedsystems.cloud.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.google.gson.Gson;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api")
public class GTicketsController {
    private final String reliableAirlineEndpoint = "http://reliable.westeurope.cloudapp.azure.com";
    private final String unreliableAirlineEndpoint = "http://unreliable.eastus.cloudapp.azure.com";
    private final String apiKey = "Iw8zeveVyaPNWonPNaU0213uw3g6Ei";

    private final RestTemplate restTemplate;
    private final Gson gson;


    public GTicketsController() {
        this.restTemplate = new RestTemplate();
        this.gson = new Gson();
    }

    @GetMapping("/getFlights")
    public ResponseEntity<String> getFlights() {
        // the url to get all flights from the reliable airline
        String reliableUrl = reliableAirlineEndpoint + "/flights?key=" + apiKey;

        // get the flights from the reliable airline
        ResponseEntity<String> reliableResponse = restTemplate.getForEntity(reliableUrl, String.class);
        String json = reliableResponse.getBody();

        // Parse the JSON string
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        JsonArray flightsArray = jsonObject.getAsJsonObject("_embedded").getAsJsonArray("flights");

        String flightsJson = gson.toJson(flightsArray);

        return ResponseEntity.ok(flightsJson);
    }

}


package be.kuleuven.distributedsystems.cloud.manager;

import be.kuleuven.distributedsystems.cloud.entities.Quote;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuoteManager {
    private static List<Quote> quotes;

    public QuoteManager() {
        quotes = new ArrayList<>();
    }

    public static void createQuote(String airline, String flightId, String seatId) {
        // Check if quote already exists
        boolean quoteExists = false;
        for (Quote quote : quotes) {
            if (quote.getAirline().equals(airline)
                    && quote.getFlightId().toString().equals(flightId)
                    && quote.getSeatId().toString().equals(seatId)) {
                quoteExists = true;
                break;
            }
        }

        if (!quoteExists) {
            UUID flightuuid = UUID.fromString(flightId);
            UUID seatuuid = UUID.fromString(seatId);
            Quote quote = new Quote(airline, flightuuid, seatuuid);
            quotes.add(quote);
            System.out.println("Quote created: " + quote.getSeatId() + "*****" + quote.getAirline() + "*****" + quote.getFlightId());
        }


    }

    public List<Quote> getAllQuotes() {
        return quotes;
    }
}

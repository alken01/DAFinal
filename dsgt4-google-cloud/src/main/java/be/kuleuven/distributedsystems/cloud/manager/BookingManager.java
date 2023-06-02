package be.kuleuven.distributedsystems.cloud.manager;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class BookingManager {

    private static List<Quote> quotes;

    public BookingManager() {
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

    public static List<Quote> getAllQuotes() {
        return quotes;
    }


    public static Ticket quote2Ticket(Quote quote, String customer, String bookingReference) {
        return new Ticket(quote.getAirline(), quote.getFlightId(), quote.getSeatId(),
                UUID.randomUUID(), customer, bookingReference);
    }

    public static void createBooking(List<Quote> quotes, String customer, Booking booking) {
        for (Quote quote : quotes) {
            Ticket ticket = quote2Ticket(quote, customer, booking.getId().toString());
            booking.getTickets().add(ticket);
        }
    }


}

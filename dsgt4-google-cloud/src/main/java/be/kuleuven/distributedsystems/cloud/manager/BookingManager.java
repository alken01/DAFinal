package be.kuleuven.distributedsystems.cloud.manager;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;

import java.time.LocalDateTime;
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


    public static List<Ticket> quote2Ticket(List<Quote> quotes, String customer, String bookingReference) {
        //Just creates the ticket from a quote
        List<Ticket> ticketList = new ArrayList<>();
        if(!quotes.isEmpty()){
            for(Quote quote: quotes){
                Ticket ticket = new Ticket(quote.getAirline(), quote.getFlightId(), quote.getSeatId(), UUID.randomUUID(), customer, bookingReference);
                ticketList.add(ticket);
            }
            return ticketList;
        } else return null;
    }



    public static Booking createBooking(List<Ticket> tickets, String customer) {
        //creates a booking with random ID, the current time and the customer

        UUID bookingId = UUID.randomUUID();
        LocalDateTime bookingTime = LocalDateTime.now();
        List<Ticket> ticketList = new ArrayList<>();
        /*for (JsonObject jsonObject : tickets) {
            String airline = jsonObject.get("airline").toString();
            String flightId = jsonObject.get("flightId").toString();
            String seatId = jsonObject.get("seatId").toString();
            String ticketId = jsonObject.get("ticketId").toString();
            String customerName = jsonObject.get("customer").toString();
            String bookingReference = UUID.randomUUID().toString();

            Ticket ticket = new Ticket(airline, UUID.fromString(flightId),
                    UUID.fromString(seatId),
                    UUID.fromString(ticketId),
                    customerName,
                    bookingReference);

            ticketList.add(ticket);
        }*/
        Booking booking = new Booking(bookingId, bookingTime, tickets, customer);

        //Prints for debugging
        System.out.println("Booking created: " + booking.getCustomer() + "*****" + booking.getTickets() + "*****");
        return booking;
    }


    //added in case we need to delete the quotes in case of logout or something similar
    public static void deleteQuotes(List<Quote> quotes){
        quotes.clear();
    }

}

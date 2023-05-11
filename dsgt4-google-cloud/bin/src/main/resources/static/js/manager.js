import { h, Component } from "https://esm.sh/preact@10.11.2";
import htm from "https://esm.sh/htm@3.1.1";
import { getAuth } from "./state.js";

const html = htm.bind(h);

export class Manager extends Component {
  constructor() {
    super();
    this.state = {
      bestCustomers: [],
      bookings: [],
      flights: new Map(),
      seats: new Map(),
    };
  }

  async componentDidMount() {
    const response1 = await fetch("/api/getBestCustomers", {
      headers: {
        Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
          false
        )}`,
      },
    });
    if (!response1.ok) {
      return html`${await response1.text()}`;
    }
    const bestCustomers = await response1.json();

    const response2 = await fetch("/api/getAllBookings", {
      headers: {
        Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
          false
        )}`,
      },
    });
    if (!response2.ok) {
      return html`${await response2.text()}`;
    }
    const bookings = await response2.json();

    const flights = new Map();
    const seats = new Map();
    for (const booking of bookings) {
      for (const ticket of booking.tickets) {
        if (!flights.has(ticket.flightId)) {
          const response = await fetch(
            `/api/getFlight?airline=${ticket.airline}&flightId=${ticket.flightId}`,
            {
              headers: {
                Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
                  false
                )}`,
              },
            }
          );
          if (!response.ok) {
            return html`${await response.text()}`;
          }
          const flight = await response.json();
          flights.set(flight.flightId, flight);
        }
        if (!seats.has(ticket.seatId)) {
          const response = await fetch(
            `/api/getSeat?airline=${ticket.airline}&flightId=${ticket.flightId}&seatId=${ticket.seatId}`,
            {
              headers: {
                Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
                  false
                )}`,
              },
            }
          );
          if (!response.ok) {
            return html`${await response.text()}`;
          }
          const seat = await response.json();
          seats.set(seat.seatId, seat);
        }
      }
    }

    this.setState({ bestCustomers, bookings, flights, seats });
  }

  render() {
    return html`
      <div class="page">
        <div>
          <h1>Manager dashboard</h1>
        </div>
        <div>
          <h2>Best customers</h2>
          ${this.state.bestCustomers.map(
            (customer) => html` <div>${customer}</div>`
          )}
        </div>
        <div>
          <h2>All bookings</h2>
        </div>
        <div>
          ${this.state.bookings.map(
            (booking) => html`
              <div class="booking">
                <div class="booking-header">
                  <div>Booking reference: ${booking.id}</div>
                  <div>
                    ${Intl.DateTimeFormat("en-gb", {
                      dateStyle: "long",
                      timeStyle: "short",
                    }).format(new Date(booking.time))}
                  </div>
                </div>
                ${booking.tickets.map(
                  (ticket) => html`
                    <div class="ticket">
                      <div>${this.state.flights.get(ticket.flightId).name}</div>
                      <div>
                        ${Intl.DateTimeFormat("en-gb", {
                          dateStyle: "long",
                          timeStyle: "short",
                        }).format(
                          new Date(this.state.seats.get(ticket.seatId).time)
                        )}
                      </div>
                      <div>${this.state.seats.get(ticket.seatId).type}</div>
                      <div>${this.state.seats.get(ticket.seatId).name}</div>
                      <div>€ ${this.state.seats.get(ticket.seatId).price}</div>
                    </div>
                  `
                )}
                <div>${booking.customer}</div>
              </div>
            `
          )}
        </div>
      </div>
    `;
  }
}

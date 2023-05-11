import { h, Component } from "https://esm.sh/preact@10.11.2";
import htm from "https://esm.sh/htm@3.1.1";
import { effect } from "https://esm.sh/@preact/signals@1.1.2";
import { getAuth, getQuotes, setQuotes } from "./state.js";

const html = htm.bind(h);

const sortingOrder = ["First", "Business", "Economy"];

export class FlightSeats extends Component {
  constructor() {
    super();
    this.state = {
      flight: null,
      seats: {},
      time: "",
    };
    effect(() => {
      this.setState({
        ...this.state,
        quotes: getQuotes(),
      });
    });
  }

  async componentDidMount() {
    const [, , airline, flightId, time] = location.pathname.split("/");
    const response1 = await fetch(
      `/api/getFlight?airline=${airline}&flightId=${flightId}`,
      {
        headers: {
          Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
            false
          )}`,
        },
      }
    );
    if (!response1.ok) {
      return html`${await response1.text()}`;
    }
    const flight = await response1.json();

    const response2 = await fetch(
      `/api/getAvailableSeats?airline=${airline}&flightId=${flightId}&time=${time}`,
      {
        headers: {
          Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
            false
          )}`,
        },
      }
    );
    if (!response2.ok) {
      return html`${await response2.text()}`;
    }
    const seats = await response2.json();

    this.setState({ flight, seats, time });
  }

  render() {
    let quotes = this.state.quotes;
    const seatsInCart = new Set(quotes.map((quote) => quote.seatId));
    return html`
      <div class="page">
        <div class="flights-item">
          ${this.state.flight != null
            ? html`
                <img src="${this.state.flight.image}" />
                <div>
                  <div class="flights-item-name">${this.state.flight.name}</div>
                  <div class="flights-item-location">
                    ${this.state.flight.location}
                  </div>
                  <div class="flight-time">${this.state.time}</div>
                </div>
              `
            : ""}
        </div>
        <div>
          ${Object.entries(this.state.seats)
            .sort(
              (a, b) => sortingOrder.indexOf(a[0]) - sortingOrder.indexOf(b[0])
            )
            .map(
              ([name, seats]) => html`
                <div>
                  <div class="seats-type">${name}</div>
                  <div class="seats seats-${name}">
                    ${seats
                      .filter((seat) => !seatsInCart.has(seat.seatId))
                      .map(
                        (seat) => html`
                          <div
                            class="seat seat-${seat.name.slice(
                              seat.name.length - 1
                            )}"
                          >
                            <button
                              class="seats-button"
                              onClick="${() => {
                                quotes = [
                                  ...quotes,
                                  {
                                    airline: seat.airline,
                                    flightId: seat.flightId,
                                    seatId: seat.seatId,
                                  },
                                ];
                                setQuotes(quotes);
                              }}"
                            >
                              ${seat.name}
                            </button>
                          </div>
                        `
                      )}
                  </div>
                </div>
              `
            )}
        </div>
      </div>
    `;
  }
}

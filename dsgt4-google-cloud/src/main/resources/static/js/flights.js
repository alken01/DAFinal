import { h, Component } from "https://esm.sh/preact@10.11.2";
import htm from "https://esm.sh/htm@3.1.1";
import { getAuth } from "./state.js";

const html = htm.bind(h);

export class Flights extends Component {
  constructor() {
    super();
    this.state = {
      flights: [],
    };
  }

  async componentDidMount() {
    const response = await fetch("/api/getFlights", {
      headers: {
        Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
          false
        )}`,
      },
    });
    if (!response.ok) {
      return html`${await response.text()}`;
    }
    const flights = await response.json();

    this.setState({ flights });
  }

  render() {
    return html`
      <div class="page">
        <div>
          <h1>Flights</h1>
        </div>
        <div class="flights-grid">
          ${this.state.flights.map(
            (flight) => html`
              <a href="/flights/${flight.airline}/${flight.flightId}">
                <div class="flights-item">
                  <img async src="${flight.image}" />
                  <div>
                    <div class="flights-item-name">${flight.name}</div>
                    <div class="flights-item-location">${flight.location}</div>
                  </div>
                </div>
              </a>
            `
          )}
        </div>
      </div>
    `;
  }
}

import { h, Component } from "https://esm.sh/preact@10.11.2";
import htm from "https://esm.sh/htm@3.1.1";
import { getAuth } from "./state.js";

const html = htm.bind(h);

export class FlightTimes extends Component {
  constructor() {
    super();
    this.state = {
      flight: null,
      times: [],
    };
  }

  async componentDidMount() {
    const [, , airline, flightId] = location.pathname.split("/");
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
      `/api/getFlightTimes?airline=${airline}&flightId=${flightId}`,
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
    const times = await response2.json();

    this.setState({ flight, times });
  }

  render() {
    return html`
      <div class="page">
        <div class="flights-item">
          ${this.state.flight != null
            ? html`
                <img async src="${this.state.flight.image}" />
                <div>
                  <div class="flights-item-name">${this.state.flight.name}</div>
                  <div class="flights-item-location">
                    ${this.state.flight.location}
                  </div>
                </div>
              `
            : ""}
        </div>
        <div>
          ${this.state.times.map(
            (time) => html`
              <div class="flight-time">
                <div>
                  ${Intl.DateTimeFormat("en-gb", {
                    dateStyle: "long",
                    timeStyle: "short",
                  }).format(new Date(time))}
                </div>
                <a
                  href="/flights/${this.state.flight.airline}/${this.state
                    .flight.flightId}/${time}"
                >
                  <div class="flight-times-button-book">Book now</div>
                </a>
              </div>
            `
          )}
        </div>
      </div>
    `;
  }
}

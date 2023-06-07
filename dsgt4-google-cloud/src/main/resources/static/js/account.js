import { h, Component } from "https://esm.sh/preact@10.11.2";
import htm from "https://esm.sh/htm@3.1.1";
import { getAuth, getDb } from "./state.js";
import { doc, setDoc, getDocs, collection, addDoc } from "https://www.gstatic.com/firebasejs/9.6.6/firebase-firestore.js";

const html = htm.bind(h);

export class Account extends Component {
  constructor() {
    super();
    this.state = {
      bookings: [],
      flights: new Map(),
      seats: new Map(),
    };
  }

  async componentDidMount() {

// temp code to try Firestore
//const addTestDocument = async () => {
//  try {
//    const docRef = await addDoc(collection(db, "users"), {
//      first: "WArd",
//      last: "Zwart",
//      born: 2062
//    });
//    console.log("Document written with ID: ", docRef.id);
//  } catch (error) {
//    console.error("Error adding document: ", error);
//  }
//};
//await addTestDocument();
const db = getDb();
const querySnapshot = await getDocs(collection(db, "bookings"));
querySnapshot.forEach((doc) => {
  console.log(`${doc.id} => ${doc.data()}`);
});
// end of temp code

//TODO: instead of retrieving from API, get them from Firestore
    const response = await fetch("/api/getBookings", {
      headers: {
        Authorization: `Bearer ${await getAuth().currentUser.getIdToken(
          false
        )}`,
      },
    });
    if (!response.ok) {
      return html`${await response.text()}`;
    }
    const bookings = await response.json();

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
  console.error(`Error: ${response.status} - ${response.statusText}`);
  // Handle the error case here
  // For example, you can throw an error or return an error message
  throw new Error("Failed to get seat data");
}

const seat = await response.json();
seats.set(seat.seatId, seat);
        }
      }
    }

    this.setState({ bookings, flights, seats });
  }

  render() {
    return html`
      <div class="page">
        <div>
          <h1>Bookings</h1>
        </div>
        ${this.state.bookings.length !== 0
          ? html`
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
                            <div>
                              ${this.state.flights.get(ticket.flightId).name}
                            </div>
                            <div>
                              ${Intl.DateTimeFormat("en-gb", {
                                dateStyle: "long",
                                timeStyle: "short",
                              }).format(
                                new Date(
                                  this.state.seats.get(ticket.seatId).time
                                )
                              )}
                            </div>
                            <div>
                              ${this.state.seats.get(ticket.seatId).type}
                            </div>
                            <div>
                              ${this.state.seats.get(ticket.seatId).name}
                            </div>
                            <div>
                              € ${this.state.seats.get(ticket.seatId).price}
                            </div>
                          </div>
                        `
                      )}
                    </div>
                  `
                )}
              </div>
            `
          : html` You have no bookings yet `}
      </div>
    `;
  }
}

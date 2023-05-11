import { h, render } from "https://esm.sh/preact@10.11.2";
import Router from "https://esm.sh/preact-router@4.1.0";
import htm from "https://esm.sh/htm@3.1.1";
import { initializeApp } from "https://www.gstatic.com/firebasejs/9.9.4/firebase-app.js";
import {
  getAuth,
  connectAuthEmulator,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/9.9.4/firebase-auth.js";
import { Header } from "./header.js";
import { Flights } from "./flights.js";
import { setAuth, setIsManager } from "./state.js";
import { FlightTimes } from "./flight_times.js";
import { FlightSeats } from "./flight_seats.js";
import { Cart } from "./cart.js";
import { Account } from "./account.js";
import { Manager } from "./manager.js";
import { Login } from "./login.js";

const html = htm.bind(h);

let firebaseConfig;
if (location.hostname === "localhost") {
  firebaseConfig = {
    apiKey: "AIzaSyBoLKKR7OFL2ICE15Lc1-8czPtnbej0jWY",
    projectId: "demo-distributed-systems-kul",
  };
} else {
  firebaseConfig = {
    // TODO: for level 2, paste your config here
  };
}

const firebaseApp = initializeApp(firebaseConfig);
const auth = getAuth(firebaseApp);
setAuth(auth);
if (location.hostname === "localhost") {
  connectAuthEmulator(auth, "http://localhost:8082", { disableWarnings: true });
}
let rendered = false;
onAuthStateChanged(auth, (user) => {
  if (user == null) {
    if (location.pathname !== "/login") {
      location.assign("/login");
    }
  } else {
    auth.currentUser.getIdTokenResult().then((idTokenResult) => {
      setIsManager(idTokenResult.claims.role === "manager");
    });
  }

  if (!rendered) {
    if (location.pathname === "/login") {
      render(html` <${Login} />`, document.body);
    } else {
      render(
        html`
            <${Header}/>
            <${Router}>
                <${Flights} path="/"/>
                <${FlightTimes} path="/flights/:airline/:flightId"/>
                <${FlightSeats} path="/flights/:airline/:flightId/:time"/>
                <${Cart} path="/cart"/>
                <${Account} path="/account"/>
                <${Manager} path="/manager"/>
            </${Router}>
        `,
        document.body
      );
    }
    rendered = true;
  }
});

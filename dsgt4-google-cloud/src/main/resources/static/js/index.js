import { h, render } from "https://esm.sh/preact@10.11.2";
import Router from "https://esm.sh/preact-router@4.1.0";
import htm from "https://esm.sh/htm@3.1.1";
import { initializeApp } from "https://www.gstatic.com/firebasejs/9.6.6/firebase-app.js";
import {
  getAuth,
  connectAuthEmulator,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/9.6.6/firebase-auth.js";
import { getFirestore, doc, setDoc, getDocs, collection, addDoc } from "https://www.gstatic.com/firebasejs/9.6.6/firebase-firestore.js";
import { Header } from "./header.js";
import { Flights } from "./flights.js";
import { setAuth, setIsManager, setDb } from "./state.js";
import { FlightTimes } from "./flight_times.js";
import { FlightSeats } from "./flight_seats.js";
import { Cart } from "./cart.js";
import { Account } from "./account.js";
import { Manager } from "./manager.js";
import { Login } from "./login.js";

const html = htm.bind(h);

let firebaseConfig;
if ((location.hostname === "localhost") && false) { //temp false to test out level 2 (&& false)
    console.log("using emulator");
  firebaseConfig = {
    apiKey: "AIzaSyBoLKKR7OFL2ICE15Lc1-8czPtnbej0jWY",
    projectId: "demo-distributed-systems-kul",
  };
} else {
  firebaseConfig = {
    // TODO: for level 2, paste your config here
  apiKey: "AIzaSyD7zJLuqTko8494kglGcPN5rwDzKh_cITg",
  authDomain: "dafinal-50ac9.firebaseapp.com",
  projectId: "dafinal-50ac9",
  storageBucket: "dafinal-50ac9.appspot.com",
  messagingSenderId: "674331922712",
  appId: "1:674331922712:web:b766bb1530fddc38694e6d",
  measurementId: "G-HG6K66GWNM"
  };
}

const firebaseApp = initializeApp(firebaseConfig);
const auth = getAuth(firebaseApp);
setAuth(auth);
if ((location.hostname === "localhost") && false) { //temp false to test out level 2 (&& false)
  connectAuthEmulator(auth, "http://localhost:9099", { disableWarnings: true });
}
const db = getFirestore(firebaseApp, {
  timestampsInSnapshots: true
});
setDb(db);

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

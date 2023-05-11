import { h, Component } from "https://esm.sh/preact@10.11.2";
import htm from "https://esm.sh/htm@3.1.1";
import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
} from "https://www.gstatic.com/firebasejs/9.9.4/firebase-auth.js";
import { getAuth } from "./state.js";

const html = htm.bind(h);

export class Login extends Component {
  render() {
    return html`
      <div class="login">
        <div class="textfield">
          <input class="email" type="text" required="required" />
          <span class="highlight"></span>
          <span class="bar"></span>
          <label>Email</label>
        </div>
        <div class="textfield">
          <input
            class="password"
            type="password"
            required="required"
            onKeyDown="${(ev) => {
              if (ev.keyCode === 13) {
                this.login();
              }
              return false;
            }}"
          />
          <span class="highlight"></span>
          <span class="bar"></span>
          <label>Password</label>
        </div>
        <div class="login-button" onClick="${() => this.login()}">Login</div>
      </div>
    `;
  }

  async login() {
    const email = document.querySelector("input.email").value;
    const password = document.querySelector("input.password").value;
    try {
      await signInWithEmailAndPassword(getAuth(), email, password);
      location.assign("/");
    } catch (e) {
      if (e.code === "auth/user-not-found") {
        await createUserWithEmailAndPassword(getAuth(), email, password);
        location.assign("/");
      } else {
        throw e;
      }
    }
  }
}

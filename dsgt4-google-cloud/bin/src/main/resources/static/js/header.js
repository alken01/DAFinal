import { h, Component } from "https://esm.sh/preact@10.11.2";
import htm from "https://esm.sh/htm@3.1.1";
import { effect } from "https://esm.sh/@preact/signals@1.1.2";
import { getAuth, getIsManager, getQuotes } from "./state.js";

const html = htm.bind(h);

export class Header extends Component {
  constructor() {
    super();
    this.state = {};
    effect(() => {
      this.setState({
        ...this.state,
        quotes: getQuotes(),
        isManager: getIsManager(),
      });
    });
  }

  render() {
    return html`
      <header>
        <div class="header-menu">
          <a href="/">
            <div class="header-title">GTickets</div>
          </a>
          <span>
            ${this.state.isManager
              ? html`
                  <a href="/manager">
                    <span class="header-icon">
                      <img src="/images/receipt.svg" height="24" />
                    </span>
                  </a>
                `
              : ""}
            <a href="/cart">
              <span class="header-icon">
                <img src="/images/shopping_cart.svg" height="24" />
                ${this.state.quotes.length !== 0
                  ? html` <div class="header-cart-badge">
                      ${this.state.quotes.length}
                    </div>`
                  : ""}
              </span>
            </a>
            <a href="/account">
              <span class="header-icon">
                <img src="/images/account.svg" height="24" />
              </span>
            </a>
            <span
              class="header-icon"
              onClick="${async () => {
                await getAuth().signOut();
                location.assign("/");
              }}"
            >
              <img src="/images/logout.svg" height="24" />
            </span>
          </span>
        </div>
      </header>
    `;
  }
}

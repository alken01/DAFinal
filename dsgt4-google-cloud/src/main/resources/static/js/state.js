import { signal } from "https://esm.sh/@preact/signals@1.1.2";

let initQuotes = [];
try {
  initQuotes = JSON.parse(localStorage.quotes ?? "[]");
} catch (e) {}
const quotes = signal(initQuotes);
const isManager = signal(false);
let auth = {};

export function getQuotes() {
  return quotes.value;
}

export function setQuotes(q) {
  quotes.value = q;
  localStorage.quotes = JSON.stringify(q);
}

export function getIsManager() {
  return isManager.value;
}

export function setIsManager(b) {
  isManager.value = b;
}

export function getAuth() {
  return auth;
}

export function setAuth(a) {
  auth = a;
}

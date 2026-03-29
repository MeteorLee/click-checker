const ACCESS_TOKEN_KEY = "click-checker.access-token";
const REFRESH_TOKEN_KEY = "click-checker.refresh-token";

export function getAccessToken() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(accessToken: string) {
  window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
}

export function getRefreshToken() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(refreshToken: string) {
  window.localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function setSessionTokens(accessToken: string, refreshToken: string) {
  setAccessToken(accessToken);
  setRefreshToken(refreshToken);
}

export function clearAccessToken() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
}

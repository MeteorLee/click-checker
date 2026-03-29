const DEFAULT_API_BASE_URL = "";

export function getApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? DEFAULT_API_BASE_URL;
}

export function buildApiUrl(path: string) {
  const baseUrl = getApiBaseUrl();
  return `${baseUrl}${path}`;
}

export function buildApiUrlObject(path: string) {
  const apiUrl = buildApiUrl(path);

  if (/^https?:\/\//.test(apiUrl)) {
    return new URL(apiUrl);
  }

  const baseOrigin =
    typeof window !== "undefined" ? window.location.origin : "http://localhost";

  return new URL(apiUrl, baseOrigin);
}

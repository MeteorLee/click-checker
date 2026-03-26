export function formatNumber(value: number | null | undefined) {
  return new Intl.NumberFormat("ko-KR").format(value ?? 0);
}

export function formatPercent(value: number | null | undefined) {
  if (value == null) {
    return "-";
  }

  return `${(value * 100).toFixed(1)}%`;
}

export function formatSignedNumber(value: number | null | undefined) {
  if (value == null) {
    return "-";
  }

  if (value > 0) {
    return `+${formatNumber(value)}`;
  }

  return formatNumber(value);
}

export function formatSignedPercent(value: number | null | undefined) {
  if (value == null) {
    return "-";
  }

  const percent = (value * 100).toFixed(1);
  return value > 0 ? `+${percent}%` : `${percent}%`;
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "-";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Seoul",
  }).format(new Date(value));
}

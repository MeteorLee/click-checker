function pad(value: number) {
  return value.toString().padStart(2, "0");
}

export const MAX_ANALYTICS_RANGE_DAYS = 90;

export function formatLocalDate(date: Date) {
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());

  return `${year}-${month}-${day}`;
}

export function getOverviewRange(days: number) {
  const endExclusive = new Date();
  endExclusive.setDate(endExclusive.getDate() + 1);

  const start = new Date(endExclusive);
  start.setDate(endExclusive.getDate() - days);

  const displayEnd = new Date(endExclusive);
  displayEnd.setDate(endExclusive.getDate() - 1);

  return {
    from: formatLocalDate(start),
    to: formatLocalDate(endExclusive),
    displayFrom: formatLocalDate(start),
    displayTo: formatLocalDate(displayEnd),
  };
}

export function getCustomOverviewRange(from: string, to: string) {
  const start = new Date(`${from}T00:00:00`);
  const displayEnd = new Date(`${to}T00:00:00`);
  const endExclusive = new Date(displayEnd);
  endExclusive.setDate(endExclusive.getDate() + 1);

  return {
    from: formatLocalDate(start),
    to: formatLocalDate(endExclusive),
    displayFrom: formatLocalDate(start),
    displayTo: formatLocalDate(displayEnd),
  };
}

export function getInclusiveDateRangeLengthDays(from: string, to: string) {
  const start = new Date(`${from}T00:00:00`);
  const end = new Date(`${to}T00:00:00`);

  return Math.floor((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
}

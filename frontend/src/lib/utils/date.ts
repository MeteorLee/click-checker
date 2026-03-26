function pad(value: number) {
  return value.toString().padStart(2, "0");
}

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

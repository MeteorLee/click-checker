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
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - (days - 1));

  return {
    from: formatLocalDate(start),
    to: formatLocalDate(end),
  };
}

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ORG_ID = __ENV.ORG_ID || '1';
const FROM = __ENV.FROM || '2026-02-13T00:00:00';
const TO = __ENV.TO || '2026-02-14T00:00:00';
const TOP = __ENV.TOP || '5';

export const options = {
  scenarios: {
    read_heavy: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 20,
      maxVUs: 60,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

export default function () {
  const url = `${BASE_URL}/api/events/aggregates/paths?organizationId=${ORG_ID}&from=${encodeURIComponent(FROM)}&to=${encodeURIComponent(TO)}&top=${TOP}`;
  const res = http.get(url);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'content type is json': (r) => (r.headers['Content-Type'] || '').includes('application/json'),
  });
}

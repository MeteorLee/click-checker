import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ORG_ID = Number(__ENV.ORG_ID || '1');
const EVENT_TYPE = __ENV.EVENT_TYPE || 'click';

export const options = {
  scenarios: {
    write_heavy: {
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
  const now = new Date().toISOString();
  const body = JSON.stringify({
    organizationId: ORG_ID,
    externalUserId: `load-user-${__VU}`,
    eventType: EVENT_TYPE,
    path: `/__test/load/${__VU}/${__ITER % 50}`,
    occurredAt: now,
    payload: JSON.stringify({ source: 'k6', vu: __VU, iter: __ITER }),
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(`${BASE_URL}/api/events`, body, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response has id': (r) => {
      if (r.status !== 200) return false;
      try {
        const json = r.json();
        return json && typeof json.id === 'number';
      } catch (e) {
        return false;
      }
    },
  });
}

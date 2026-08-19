import http from 'k6/http';
import { check, sleep } from 'k6';
import { authHeaders, jsonAuthHeaders } from './lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const STOCK = Number(__ENV.STOCK || 100);
const API = `${BASE_URL}/api/v1`;
const OWNER_ID = 1;
const OWNER = jsonAuthHeaders(OWNER_ID);

http.setResponseCallback(http.expectedStatuses(200, 201, 202, 409));

export const options = {
  scenarios: {
    capacity: {
      executor: 'ramping-arrival-rate',
      startRate: 500,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 6000,
      stages: [
        { target: 500,  duration: '20s' },
        { target: 1000, duration: '20s' },
        { target: 2000, duration: '20s' },
        { target: 4000, duration: '20s' },
        { target: 6000, duration: '20s' },
        { target: 8000, duration: '20s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const openAt = new Date(Date.now() + 4000).toISOString().slice(0, 19);
  const res = http.post(
    `${API}/campaigns`,
    JSON.stringify({ title: 'k6 capacity', totalStock: STOCK, openAt, requiresPayment: false }),
    { headers: OWNER }
  );
  const campaignId = res.json('id');
  const shortCode = res.json('shortCode');
  for (let i = 0; i < 20; i++) {
    if (http.get(`${API}/campaigns/${shortCode}`).json('status') === 'OPEN') break;
    sleep(1);
  }
  console.log(`campaign=${campaignId}, stock=${STOCK} — 계단식 부하 시작`);
  return { campaignId };
}

export default function (data) {
  const userId = __VU * 1000000 + __ITER;
  const res = http.post(`${API}/campaigns/${data.campaignId}/apply`, null, {
    headers: authHeaders(userId),
  });
  check(res, { '5xx 아님': (r) => r.status === 201 || r.status === 202 || r.status === 409 });
}

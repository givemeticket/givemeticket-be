import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { authHeaders, jsonAuthHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const STOCK = Number(__ENV.STOCK || 100);
const RATE = Number(__ENV.RATE || 500);
const DURATION = __ENV.DURATION || '30s';
const PRE_VUS = Number(__ENV.PRE_VUS || 200);
const MAX_VUS = Number(__ENV.MAX_VUS || 2000);
const API = `${BASE_URL}/api/v1`;
const OWNER = jsonAuthHeaders(1);

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const created = new Counter('apply_created');
const soldOut = new Counter('apply_sold_out');

export const options = {
  scenarios: {
    rush: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const openAt = new Date(Date.now() + 5000).toISOString().slice(0, 19);
  const res = http.post(
    `${API}/campaigns`,
    JSON.stringify({
      title: 'k6 rush',
      totalStock: STOCK,
      openAt,
    }),
    { headers: OWNER }
  );
  const campaignId = res.json('id');
  const shortCode = res.json('shortCode');
  console.log(`campaign=${campaignId}, stock=${STOCK}, openAt=${openAt} (UTC)`);

  for (let i = 0; i < 30; i++) {
    if (http.get(`${API}/campaigns/${shortCode}`).json('status') === 'OPEN') {
      console.log('campaign OPEN — 부하 시작');
      break;
    }
    sleep(1);
  }
  return { campaignId, shortCode };
}

export default function (data) {
  const userId = __VU * 1000000 + __ITER;
  const res = http.post(`${API}/campaigns/${data.campaignId}/apply`, null, {
    headers: authHeaders(userId),
  });

  if (res.status === 201) created.add(1);
  else if (res.status === 409) soldOut.add(1);

  check(res, {
    '5xx 없음(201/409만)': (r) => r.status === 201 || r.status === 409,
  });
}

export function teardown(data) {
  const remaining = http.get(`${API}/campaigns/${data.shortCode}`).json('remainingStock');
  console.log('==================================================');
  console.log(`잔여 재고 = ${remaining}  (0 이상이면 오버셀 없음)`);
  console.log(`정원 = ${STOCK} → apply_created 가 ${STOCK} 이하이면 정상`);
  console.log('==================================================');
}

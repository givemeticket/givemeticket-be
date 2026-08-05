import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '2m';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  scenarios: {
    soak: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VUS },
        { duration: DURATION, target: VUS },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

export function setup() {
  const openAt = new Date(Date.now() + 4000).toISOString().slice(0, 19);
  const res = http.post(
    `${BASE_URL}/campaigns`,
    JSON.stringify({ title: 'k6 soak', totalStock: 1000000, openAt }),
    { headers: JSON_HEADERS }
  );
  const campaignId = res.json('id');
  for (let i = 0; i < 20; i++) {
    if (http.get(`${BASE_URL}/campaigns/${campaignId}`).json('status') === 'OPEN') break;
    sleep(1);
  }
  console.log(`campaign=${campaignId} 준비 완료`);
  return { campaignId };
}

export default function (data) {
  const userId = __VU * 1000000 + __ITER;

  group('read', () => {
    const res = http.get(`${BASE_URL}/campaigns/${data.campaignId}`);
    check(res, { '조회 200': (r) => r.status === 200 });
  });

  let applicationId;
  group('apply', () => {
    const res = http.post(`${BASE_URL}/campaigns/${data.campaignId}/apply`, null, {
      headers: { 'X-User-Id': String(userId) },
    });
    check(res, { '신청 201/409': (r) => r.status === 201 || r.status === 409 });
    if (res.status === 201) applicationId = res.json('id');
  });

  if (applicationId) {
    group('confirm', () => {
      const res = http.post(`${BASE_URL}/applications/${applicationId}/confirm`, null, {
        headers: { 'X-User-Id': String(userId) },
      });
      check(res, { '확정 200': (r) => r.status === 200 });
    });
  }

  sleep(1);
}

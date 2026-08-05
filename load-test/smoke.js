import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.0'],
  },
};

export default function () {
  const openAt = new Date(Date.now() + 4000).toISOString().slice(0, 19);
  const createRes = http.post(
    `${BASE_URL}/campaigns`,
    JSON.stringify({ title: 'k6 smoke', totalStock: 10, openAt }),
    { headers: JSON_HEADERS }
  );
  check(createRes, { '캠페인 생성 201': (r) => r.status === 201 });
  const campaignId = createRes.json('id');

  let status = 'SCHEDULED';
  for (let i = 0; i < 15 && status !== 'OPEN'; i++) {
    sleep(1);
    status = http.get(`${BASE_URL}/campaigns/${campaignId}`).json('status');
  }
  check(null, { '캠페인 OPEN 전환': () => status === 'OPEN' });

  const applyRes = http.post(`${BASE_URL}/campaigns/${campaignId}/apply`, null, {
    headers: { 'X-User-Id': '1' },
  });
  check(applyRes, {
    '신청 201': (r) => r.status === 201,
    '신청 상태 PENDING': (r) => r.json('status') === 'PENDING',
  });
  const applicationId = applyRes.json('id');

  const confirmRes = http.post(`${BASE_URL}/applications/${applicationId}/confirm`, null, {
    headers: { 'X-User-Id': '1' },
  });
  check(confirmRes, {
    '확정 200': (r) => r.status === 200,
    '확정 상태 CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
  });
}

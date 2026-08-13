import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders, jsonAuthHeaders } from './lib/auth.js';

/**
 * 응답 시간을 지킬 수 있는 최대 처리량을 찾는다.
 *
 * capacity.js 는 "무너지는 지점" 을 찾지만, 무너진 뒤의 처리량은 목표로 쓸 수 없다.
 * 여기서는 고정 RATE 로 짧게 돌려 그 속도에서 응답 시간이 기준 안에 들어오는지 본다.
 * RATE 를 바꿔가며 반복하면 "기준을 지키는 최대 TPS" 가 나온다.
 *
 * 재고 설정으로 두 경로를 나눠 잰다.
 *   STOCK=100    대부분 매진 응답 → Redis 만 쓰는 싼 경로
 *   STOCK=999999 대부분 신청 성공 → DB 까지 가는 비싼 경로
 *
 *   k6 run -e RATE=100 -e STOCK=999999 load-test/slo.js
 */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const API = `${BASE_URL}/api/v1`;

const RATE = Number(__ENV.RATE || 100);
const STOCK = Number(__ENV.STOCK || 100);
const DURATION = __ENV.DURATION || '30s';
const OWNER_ID = 1;

http.setResponseCallback(http.expectedStatuses(200, 201, 202, 409));

const created = new Counter('apply_created');
const soldOut = new Counter('apply_sold_out');
const applyTime = new Trend('apply_duration', true);
const browseTime = new Trend('browse_duration', true);

export const options = {
  scenarios: {
    apply: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(100, RATE),
      maxVUs: 3000,
      exec: 'applyScenario',
    },
    browse: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: 'browseScenario',
    },
  },
  thresholds: {},
};

export function setup() {
  const openAt = new Date(Date.now() + 5000).toISOString().slice(0, 19);
  const res = http.post(
    `${API}/campaigns`,
    JSON.stringify({
      title: `k6 slo rate=${RATE} stock=${STOCK}`,
      totalStock: STOCK,
      openAt,
      requiresPayment: false,
    }),
    { headers: jsonAuthHeaders(OWNER_ID) }
  );
  if (res.status !== 201) throw new Error(`캠페인 생성 실패: ${res.status} ${res.body}`);

  const campaignId = res.json('id');
  const shortCode = res.json('shortCode');

  for (let i = 0; i < 30; i++) {
    if (http.get(`${API}/campaigns/${shortCode}`).json('status') === 'OPEN') {
      return { campaignId, shortCode };
    }
    sleep(1);
  }
  throw new Error('캠페인이 열리지 않았다');
}

export function applyScenario(data) {
  const userId = 5000000 + __VU * 100000 + __ITER;
  const res = http.post(`${API}/campaigns/${data.campaignId}/apply`, null, {
    headers: authHeaders(userId),
  });

  applyTime.add(res.timings.duration);
  if (res.status === 201) created.add(1);
  else if (res.status === 409) soldOut.add(1);

  check(res, { 'apply: 401/5xx 없음': (r) => r.status === 201 || r.status === 202 || r.status === 409 });
}

export function browseScenario(data) {
  const res = http.get(`${API}/campaigns/${data.shortCode}`);
  browseTime.add(res.timings.duration);
  check(res, { 'browse: 200': (r) => r.status === 200 });
}

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders, jsonAuthHeaders } from './lib/auth.js';

/**
 * 대기열 도입 전 기준선 측정.
 *
 * "정원 100인 행사에 N명이 몰리면 무슨 일이 벌어지는가" 를 재는 것이 목적이다.
 * 신청 API 만 재면 절반만 보는 것이라, 신청과 무관한 조회 API 를 부하 전·중·후로
 * 나눠 함께 잰다. 오픈 스파이크가 서비스 전체를 느리게 만드는지 확인하기 위해서다.
 *
 *   k6 run -e APPLICANTS=1000 load-test/baseline.js
 *   k6 run -e APPLICANTS=10000 -e SPIKE_VUS=2000 load-test/baseline.js
 */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const API = `${BASE_URL}/api/v1`;

const STOCK = Number(__ENV.STOCK || 100);
const APPLICANTS = Number(__ENV.APPLICANTS || 1000);
// 동시에 달려드는 정도. 실제 오픈은 순간적이라 지원자 수에 비례해 올린다.
const SPIKE_VUS = Number(__ENV.SPIKE_VUS || Math.min(APPLICANTS, 1000));

// 구간 경계 (초). 조회 API 응답시간을 이 구간별로 나눠 담는다.
const SPIKE_START = 15;
const SPIKE_END = 45;
const TOTAL = 60;

const OWNER_ID = 1;

// 409(매진·중복)는 정상 응답이다. 401·5xx 만 실패로 잡는다.
http.setResponseCallback(http.expectedStatuses(200, 201, 202, 409));

const created = new Counter('apply_created');
const soldOut = new Counter('apply_sold_out');
const unknown = new Counter('apply_unknown');
const rejected = new Counter('apply_rejected_other');
const applyTime = new Trend('apply_duration', true);

// 신청과 무관한 조회 API. 스파이크의 영향을 받는지 보려고 구간을 나눠 담는다.
const browseBefore = new Trend('browse_before_spike', true);
const browseDuring = new Trend('browse_during_spike', true);
const browseAfter = new Trend('browse_after_spike', true);
const browseFailed = new Counter('browse_failed');

export const options = {
  scenarios: {
    // 신청 스파이크: 정확히 APPLICANTS 명이 한꺼번에 신청한다.
    apply: {
      executor: 'shared-iterations',
      vus: SPIKE_VUS,
      iterations: APPLICANTS,
      maxDuration: `${SPIKE_END - SPIKE_START}s`,
      startTime: `${SPIKE_START}s`,
      exec: 'applyScenario',
    },
    // 무관한 조회: 처음부터 끝까지 일정한 속도로 계속 부른다.
    browse: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: `${TOTAL}s`,
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: 'browseScenario',
    },
  },
  // 기준선 측정이므로 임계값으로 실패시키지 않는다. 숫자를 보는 것이 목적이다.
  thresholds: {},
};

export function setup() {
  const openAt = new Date(Date.now() + 5000).toISOString().slice(0, 19);
  const res = http.post(
    `${API}/campaigns`,
    JSON.stringify({
      title: `k6 baseline (${APPLICANTS}명)`,
      totalStock: STOCK,
      openAt,
      requiresPayment: false,
    }),
    { headers: jsonAuthHeaders(OWNER_ID) }
  );

  if (res.status !== 201) {
    throw new Error(`캠페인 생성 실패: ${res.status} ${res.body}`);
  }

  const campaignId = res.json('id');
  const shortCode = res.json('shortCode');

  for (let i = 0; i < 30; i++) {
    if (http.get(`${API}/campaigns/${shortCode}`).json('status') === 'OPEN') {
      console.log(`campaign=${campaignId} 정원=${STOCK} 지원자=${APPLICANTS} → OPEN`);
      return { campaignId, shortCode, startedAt: Date.now() };
    }
    sleep(1);
  }
  throw new Error('캠페인이 열리지 않았다');
}

export function applyScenario(data) {
  const userId = 1000000 + __VU * 100000 + __ITER;

  const res = http.post(`${API}/campaigns/${data.campaignId}/apply`, null, {
    headers: authHeaders(userId),
    tags: { name: 'apply' },
  });

  applyTime.add(res.timings.duration);

  if (res.status === 201) created.add(1);
  else if (res.status === 202) unknown.add(1);
  else if (res.status === 409) soldOut.add(1);
  else rejected.add(1);

  check(res, {
    'apply: 401/5xx 없음': (r) => r.status === 201 || r.status === 202 || r.status === 409,
  });
}

export function browseScenario(data) {
  const res = http.get(`${API}/campaigns/${data.shortCode}`, {
    tags: { name: 'browse' },
  });

  const elapsed = (Date.now() - data.startedAt) / 1000;
  if (elapsed < SPIKE_START) browseBefore.add(res.timings.duration);
  else if (elapsed < SPIKE_END) browseDuring.add(res.timings.duration);
  else browseAfter.add(res.timings.duration);

  if (res.status !== 200) browseFailed.add(1);

  check(res, { 'browse: 200': (r) => r.status === 200 });
}

export function teardown(data) {
  const res = http.get(`${API}/campaigns/${data.shortCode}`);
  const remaining = res.json('remainingStock');

  console.log('='.repeat(60));
  console.log(`정원 ${STOCK} / 지원자 ${APPLICANTS}`);
  console.log(`잔여 재고 = ${remaining}   (음수면 오버셀)`);
  console.log('apply_created + apply_unknown 이 정원 이하여야 한다');
  console.log('browse_* 세 구간을 비교해 스파이크가 조회에 미친 영향을 본다');
  console.log('='.repeat(60));
}

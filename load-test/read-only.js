import http from 'k6/http';
import { check, sleep } from 'k6';
import { jsonAuthHeaders } from './auth.js';

/**
 * 상세 조회만 두드려서 캐시 효과만 떼어 본다.
 *
 * soak.js 는 조회·신청·확정을 1:1:1 로 도는데, 캐시를 재는 데는 두 가지가 방해된다.
 *   - 조회가 전체의 1/3 뿐이라 나머지 2/3 의 할당이 캐시 효과를 희석한다
 *   - 로그인 상태로 조회하면 내 신청 내역(findByCampaignIdAndUserId)이 매번 DB 로 나간다.
 *     사용자별이라 캐시할 수 없어서, 캐시를 켜도 조회당 쿼리가 2개에서 1개로 줄 뿐이다
 *
 * 그래서 여기서는 <b>비로그인으로 조회만</b> 한다. 서버가 userId == null 이면 캠페인 조회
 * 한 방으로 응답을 만들기 때문에, 캐시가 켜지면 DB 쿼리가 0 건이 된다.
 *
 *   RATE=10 DURATION=3m DETAIL_SIZE=50000 k6 run load-test/read-only.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const API = `${BASE_URL}/api/v1`;
const DURATION = __ENV.DURATION || '2m';
const RATE = Number(__ENV.RATE || 10);
// RATES 를 주면 계단식으로 부하를 올린다. 예: RATES=10,20,40,80,160 STEP=60s
// 각 계단은 즉시 목표 도착률로 뛰고 STEP 만큼 평평하게 유지된다.
const RATES = (__ENV.RATES || '').split(',').filter(Boolean).map(Number);
const STEP = __ENV.STEP || '60s';
const DETAIL_SIZE = Number(__ENV.DETAIL_SIZE || 0);
// 캠페인이 하나뿐이면 캐시에 가장 유리한 조건이 된다. 여러 개를 무작위로 돌아야
// 로컬 캐시의 max-size 와 TTL 이 실제로 작동한다.
const CAMPAIGNS = Number(__ENV.CAMPAIGNS || 50);

const OWNER = jsonAuthHeaders(1);

function buildDetail() {
  if (DETAIL_SIZE <= 0) {
    return null;
  }
  const paragraph =
    '공연 30분 전부터 입장 가능합니다. 티켓은 예매자 본인 확인 후 수령하실 수 있으며, ' +
    '신분증을 반드시 지참해 주세요. 공연장 내 음식물 반입은 제한됩니다. ';
  return {
    content: paragraph.repeat(Math.ceil(DETAIL_SIZE / paragraph.length)).slice(0, DETAIL_SIZE),
    location: '올림픽공원 체조경기장',
    address: '서울특별시 송파구 올림픽로 424',
    imageUrl: 'https://example.com/poster.png',
    contact: '010-0000-0000',
    price: 15000,
  };
}

// 계단마다 즉시 뛰고(0s) 그 도착률로 STEP 만큼 유지한다.
// 서서히 올리면 계단 안에서도 부하가 변해서 구간별 지표를 뽑을 수 없다.
function rampStages() {
  const stages = [];
  RATES.forEach((rate, i) => {
    if (i > 0) {
      stages.push({ target: rate, duration: '0s' });
    }
    stages.push({ target: rate, duration: STEP });
  });
  return stages;
}

const maxRate = RATES.length ? Math.max(...RATES) : RATE;

export const options = {
  // 응답 본문을 붙들고 있지 않는다. 116KB 짜리를 초당 수백~수천 개 받으면 k6 가 먼저 무너져서
  // 서버 한계가 아니라 부하 도구 한계를 재게 된다. 상태 코드만 확인하면 되므로 버린다.
  // 본문이 필요한 setup 의 요청만 responseType 으로 따로 받는다.
  discardResponseBodies: true,
  scenarios: {
    read: RATES.length
      ? {
          executor: 'ramping-arrival-rate',
          startRate: RATES[0],
          timeUnit: '1s',
          stages: rampStages(),
          // 서버가 느려지면 VU 가 더 필요하다. 부족해서 못 쏘면 k6 가 dropped_iterations 로
          // 알려 주는데, 그건 서버 포화 신호이기도 하므로 한도를 너무 낮게 잡으면 안 된다.
          preAllocatedVUs: Math.max(50, Math.ceil(maxRate / 4)),
          maxVUs: Math.max(200, maxRate * 2),
        }
      : {
          executor: 'constant-arrival-rate',
          rate: RATE,
          timeUnit: '1s',
          duration: DURATION,
          preAllocatedVUs: Math.max(20, RATE * 2),
          maxVUs: Math.max(100, RATE * 10),
        },
  },
  // 계단을 끝까지 올리면 포화되는 것이 정상이라 임계값을 두지 않는다.
  // 포화 여부는 실제 처리량과 dropped_iterations 로 판단한다.
  thresholds: RATES.length ? {} : { http_req_failed: ['rate<0.01'] },
};

export function setup() {
  const openAt = new Date(Date.now() + 5000).toISOString().slice(0, 19);
  const detail = buildDetail();
  const shortCodes = [];

  for (let i = 0; i < CAMPAIGNS; i++) {
    const res = http.post(
      `${API}/campaigns`,
      JSON.stringify({
        title: `k6 read ${i}`,
        totalStock: 1000000,
        openAt,
        requiresPayment: false,
        detail,
      }),
      { headers: OWNER, responseType: 'text' }
    );
    if (res.status !== 201) {
      throw new Error(`캠페인 생성 실패: ${res.status} ${String(res.body).slice(0, 200)}`);
    }
    shortCodes.push(res.json('shortCode'));
  }

  // 마지막 캠페인이 열리면 나머지도 열린 것으로 본다. 모두 같은 openAt 이다.
  const last = shortCodes[shortCodes.length - 1];
  for (let i = 0; i < 20; i++) {
    if (http.get(`${API}/campaigns/${last}`, { responseType: 'text' }).json('status') === 'OPEN') break;
    sleep(1);
  }

  console.log(`캠페인 ${shortCodes.length}개 준비 완료 (안내문 ${DETAIL_SIZE}자)`);
  // 계단별 구간을 계산하려면 부하가 실제로 시작한 시각을 알아야 한다.
  // setup 은 캠페인을 만드느라 시간이 들쭉날쭉해서, k6 시작 시각으로 역산하면 계단이 어긋난다.
  console.log(`SCENARIO_START ${Date.now()}`);
  return { shortCodes };
}

export default function (data) {
  const shortCode = data.shortCodes[Math.floor(Math.random() * data.shortCodes.length)];

  // 인증 헤더를 붙이지 않는다. 붙이면 내 신청 내역 조회가 DB 로 따라 나간다.
  const res = http.get(`${API}/campaigns/${shortCode}`);

  check(res, { '조회 200': (r) => r.status === 200 });
}

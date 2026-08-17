import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { authHeaders, jsonAuthHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '2m';
const API = `${BASE_URL}/api/v1`;
const OWNER = jsonAuthHeaders(1);
// 행사 안내문의 길이(자). 캐시에 담기는 값의 크기를 좌우해서 압축률과 Redis 왕복 시간을 바꾼다.
// 0이면 detail 없이 만든다. 서버 상한은 5,000자.
const DETAIL_SIZE = Number(__ENV.DETAIL_SIZE || 0);

function buildDetail() {
  if (DETAIL_SIZE <= 0) {
    return null;
  }
  // 실제 안내문처럼 같은 어구가 반복되는 글이라야 압축률이 현실적으로 나온다.
  // 무작위 문자열로 채우면 gzip이 거의 못 줄여서 압축이 손해인 것처럼 보인다.
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

// 결제 경로까지 계속 두드리는 것이 이 테스트의 목적이다.
http.setResponseCallback(http.expectedStatuses(200, 201, 202, 409));

// RATE(초당 반복 수)를 주면 도착률을 고정한다. A/B 비교는 반드시 이 모드로 해야 한다.
//
// VU 고정 모드는 시스템이 포화되면 처리량 자체가 결과가 되어 버린다. 그러면 두 설정을
// "같은 부하"에서 비교한 게 아니라 각자의 한계치를 잰 것이라, 장비 컨디션 같은 잡음이
// 그대로 처리량 차이로 증폭된다. 도착률을 고정하면 두 설정이 같은 일을 받고, 그때의
// 지연·GC·커넥션 대기를 비교할 수 있다.
const RATE = Number(__ENV.RATE || 0);

const scenarios = RATE > 0
  ? {
      steady: {
        executor: 'constant-arrival-rate',
        rate: RATE,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: Math.max(20, RATE * 4),
        maxVUs: Math.max(100, RATE * 20),
      },
    }
  : {
      soak: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
          { duration: '30s', target: VUS },
          { duration: DURATION, target: VUS },
          { duration: '20s', target: 0 },
        ],
      },
    };

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

export function setup() {
  const openAt = new Date(Date.now() + 4000).toISOString().slice(0, 19);
  const res = http.post(
    `${API}/campaigns`,
    JSON.stringify({
      title: 'k6 soak',
      totalStock: 1000000,
      openAt,
      requiresPayment: true,
      detail: buildDetail(),
    }),
    { headers: OWNER }
  );
  const campaignId = res.json('id');
  const shortCode = res.json('shortCode');
  for (let i = 0; i < 20; i++) {
    if (http.get(`${API}/campaigns/${shortCode}`).json('status') === 'OPEN') break;
    sleep(1);
  }
  console.log(`campaign=${campaignId}, shortCode=${shortCode} 준비 완료`);
  return { campaignId, shortCode };
}

export default function (data) {
  const userId = __VU * 1000000 + __ITER;

  group('read', () => {
    const res = http.get(`${API}/campaigns/${data.shortCode}`, {
      headers: authHeaders(userId),
    });
    check(res, { '조회 200': (r) => r.status === 200 });
  });

  let applicationId;
  group('apply', () => {
    const res = http.post(`${API}/campaigns/${data.campaignId}/apply`, null, {
      headers: authHeaders(userId),
    });
    check(res, { '신청 201/409': (r) => r.status === 201 || r.status === 409 });
    if (res.status === 201 && res.json('status') === 'PENDING') {
      applicationId = res.json('id');
    }
  });

  if (applicationId) {
    group('confirm', () => {
      const res = http.post(`${API}/applications/${applicationId}/confirm`, null, {
        headers: authHeaders(userId),
      });
      check(res, { '확정 200/202': (r) => r.status === 200 || r.status === 202 });
    });
  }

  sleep(1);
}

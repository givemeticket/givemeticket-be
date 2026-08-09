import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const API = `${BASE_URL}/api/v1`;
const OWNER = { 'Content-Type': 'application/json', 'X-User-Id': '1' };
const USER = { 'X-User-Id': '2' };

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.0'],
  },
};

function createCampaign(title, requiresPayment) {
  const openAt = new Date(Date.now() + 4000).toISOString().slice(0, 19);
  const res = http.post(
    `${API}/campaigns`,
    JSON.stringify({ title, totalStock: 10, openAt, requiresPayment }),
    { headers: OWNER }
  );
  check(res, { [`${title} 생성 201`]: (r) => r.status === 201 });
  return { id: res.json('id'), shortCode: res.json('shortCode') };
}

function waitUntilOpen(shortCode) {
  let status = 'SCHEDULED';
  for (let i = 0; i < 15 && status !== 'OPEN'; i++) {
    sleep(1);
    status = http.get(`${API}/campaigns/${shortCode}`).json('status');
  }
  check(null, { [`${shortCode} OPEN 전환`]: () => status === 'OPEN' });
}

export default function () {
  // 결제 없는 캠페인 — apply 한 번으로 확정된다.
  const free = createCampaign('k6 smoke (free)', false);
  waitUntilOpen(free.shortCode);

  const freeApply = http.post(`${API}/campaigns/${free.id}/apply`, null, { headers: USER });
  check(freeApply, {
    '무료 신청 201': (r) => r.status === 201,
    '무료 신청 CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
    '무료 신청은 만료 없음': (r) => r.json('expiresAt') === null,
  });
  const freeApplicationId = freeApply.json('id');

  check(http.post(`${API}/applications/${freeApplicationId}/confirm`, null, { headers: USER }), {
    '확정된 신청 재확정 409': (r) => r.status === 409,
  });

  // 결제 있는 캠페인 — apply는 자리만 잡고 PENDING으로 둔다.
  const paid = createCampaign('k6 smoke (paid)', true);
  waitUntilOpen(paid.shortCode);

  const paidApply = http.post(`${API}/campaigns/${paid.id}/apply`, null, { headers: USER });
  check(paidApply, {
    '유료 신청 201': (r) => r.status === 201,
    '유료 신청 PENDING': (r) => r.json('status') === 'PENDING',
    '만료 시각 내려옴': (r) => !!r.json('expiresAt'),
  });
  const paidApplicationId = paidApply.json('id');

  check(http.get(`${API}/campaigns/${paid.shortCode}`), {
    'PENDING도 재고를 잡는다': (r) => r.json('remainingStock') === 9,
  });

  // 결제는 confirm에서 일어난다.
  const confirmRes = http.post(`${API}/applications/${paidApplicationId}/confirm`, null, {
    headers: USER,
  });
  check(confirmRes, {
    '확정 200': (r) => r.status === 200,
    '확정 CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
    '거래번호 발급': (r) => !!r.json('transactionId'),
  });

  check(http.post(`${API}/campaigns/${paid.id}/apply`, null, { headers: USER }), {
    '중복 신청 409': (r) => r.status === 409,
  });

  // 역할별 상세 조회.
  check(http.get(`${API}/campaigns/${paid.shortCode}`), {
    '비로그인 조회 200': (r) => r.status === 200,
    '비로그인 역할 GUEST': (r) => r.json('viewerRole') === 'GUEST',
  });
  check(http.get(`${API}/campaigns/${paid.shortCode}`, { headers: USER }), {
    '참여자 역할 PARTICIPANT': (r) => r.json('viewerRole') === 'PARTICIPANT',
    '내 신청 내역 포함': (r) => r.json('myApplication.status') === 'CONFIRMED',
  });
  check(http.get(`${API}/campaigns/${paid.shortCode}`, { headers: OWNER }), {
    '개설자 역할 OWNER': (r) => r.json('viewerRole') === 'OWNER',
    '확정 수 노출': (r) => r.json('confirmedCount') === 1,
  });

  // 목록 조회. DB가 이전 실행 데이터를 갖고 있을 수 있으므로 개수가 아니라 포함 여부를 본다.
  const hasBoth = (r) => {
    const ids = r.json('campaigns').map((c) => c.id);
    return ids.includes(free.id) && ids.includes(paid.id);
  };
  check(http.get(`${API}/campaigns?scope=owned`, { headers: OWNER }), {
    '내가 만든 행사 200': (r) => r.status === 200,
    '내가 만든 행사에 이번 캠페인 2건 포함': hasBoth,
  });
  check(http.get(`${API}/campaigns?scope=participated`, { headers: USER }), {
    '나의 티켓에 이번 캠페인 2건 포함': hasBoth,
  });

  // 정원은 늘리는 것만 가능하다.
  check(http.patch(`${API}/campaigns/${paid.id}`, JSON.stringify({ totalStock: 20 }), {
    headers: OWNER,
  }), {
    '정원 증원 200': (r) => r.status === 200,
    '잔여 재고 반영': (r) => r.json('remainingStock') === 19,
  });
  check(http.patch(`${API}/campaigns/${paid.id}`, JSON.stringify({ totalStock: 5 }), {
    headers: OWNER,
  }), { '정원 감원 409': (r) => r.status === 409 });

  check(http.del(`${API}/campaigns/${paid.id}`, null, { headers: OWNER }), {
    '신청자 있는 캠페인 삭제 409': (r) => r.status === 409,
  });

  // 취소 — 결제가 없던 신청은 외부 호출 없이 끝난다.
  check(http.post(`${API}/applications/${freeApplicationId}/cancel`, null, { headers: USER }), {
    '무료 취소 200': (r) => r.status === 200,
    '무료 취소 CANCELLED': (r) => r.json('status') === 'CANCELLED',
    '무료 취소 환불 불필요': (r) => r.json('refundStatus') === 'NOT_REQUIRED',
  });
  check(http.get(`${API}/campaigns/${free.shortCode}`), {
    '무료 취소 후 재고 복원': (r) => r.json('remainingStock') === 10,
  });

  check(http.post(`${API}/applications/${freeApplicationId}/cancel`, null, { headers: USER }), {
    '중복 취소 409': (r) => r.status === 409,
  });
  check(http.post(`${API}/applications/${paidApplicationId}/cancel`, null, {
    headers: { 'X-User-Id': '999' },
  }), { '남의 신청 취소 403': (r) => r.status === 403 });

  check(http.post(`${API}/applications/${paidApplicationId}/cancel`, null, { headers: USER }), {
    '유료 취소 200': (r) => r.status === 200,
    '유료 취소 CANCELLED': (r) => r.json('status') === 'CANCELLED',
    '유료 취소 환불 완료': (r) => r.json('refundStatus') === 'COMPLETED',
  });
  check(http.get(`${API}/campaigns/${paid.shortCode}`), {
    '유료 취소 후 재고 복원': (r) => r.json('remainingStock') === 20,
  });

  // 유효한 신청이 모두 빠지면 삭제할 수 있다.
  check(http.del(`${API}/campaigns/${paid.id}`, null, { headers: OWNER }), {
    '취소 후 캠페인 삭제 204': (r) => r.status === 204,
  });
  check(http.get(`${API}/campaigns/${paid.shortCode}`), {
    '삭제된 캠페인 조회 410': (r) => r.status === 410,
  });
}

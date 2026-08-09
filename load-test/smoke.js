import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const API = `${BASE_URL}/api/v1`;
const OWNER = { 'Content-Type': 'application/json', 'X-User-Id': '1' };

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
  // 결제 없는 캠페인 — 신청 즉시 확정된다.
  const free = createCampaign('k6 smoke (free)', false);
  waitUntilOpen(free.shortCode);

  const freeApply = http.post(`${API}/campaigns/${free.id}/apply`, null, {
    headers: { 'X-User-Id': '2' },
  });
  check(freeApply, {
    '무료 신청 201': (r) => r.status === 201,
    '무료 신청 CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
  });
  const freeApplicationId = freeApply.json('id');

  // 결제 있는 캠페인 — 재고 차감 + 결제까지 한 번에 처리된다.
  const paid = createCampaign('k6 smoke (paid)', true);
  waitUntilOpen(paid.shortCode);

  const paidApply = http.post(`${API}/campaigns/${paid.id}/apply`, null, {
    headers: { 'X-User-Id': '2' },
  });
  check(paidApply, {
    '유료 신청 201': (r) => r.status === 201,
    '유료 신청 CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
    '거래번호 발급': (r) => !!r.json('transactionId'),
  });
  const paidApplicationId = paidApply.json('id');

  // 중복 신청은 막힌다.
  const duplicate = http.post(`${API}/campaigns/${paid.id}/apply`, null, {
    headers: { 'X-User-Id': '2' },
  });
  check(duplicate, { '중복 신청 409': (r) => r.status === 409 });

  // 역할별 상세 조회.
  const guestView = http.get(`${API}/campaigns/${paid.shortCode}`);
  check(guestView, {
    '비로그인 조회 200': (r) => r.status === 200,
    '비로그인 역할 GUEST': (r) => r.json('viewerRole') === 'GUEST',
  });

  const participantView = http.get(`${API}/campaigns/${paid.shortCode}`, {
    headers: { 'X-User-Id': '2' },
  });
  check(participantView, {
    '참여자 역할 PARTICIPANT': (r) => r.json('viewerRole') === 'PARTICIPANT',
    '내 신청 내역 포함': (r) => r.json('myApplication.status') === 'CONFIRMED',
  });

  const ownerView = http.get(`${API}/campaigns/${paid.shortCode}`, { headers: OWNER });
  check(ownerView, {
    '개설자 역할 OWNER': (r) => r.json('viewerRole') === 'OWNER',
    '확정 수 노출': (r) => r.json('confirmedCount') === 1,
  });

  // 목록 조회. DB가 이전 실행 데이터를 갖고 있을 수 있으므로 개수가 아니라 포함 여부를 본다.
  const hasBoth = (r) => {
    const ids = r.json('campaigns').map((c) => c.id);
    return ids.includes(free.id) && ids.includes(paid.id);
  };

  const owned = http.get(`${API}/campaigns?scope=owned`, { headers: OWNER });
  check(owned, {
    '내가 만든 행사 200': (r) => r.status === 200,
    '내가 만든 행사에 이번 캠페인 2건 포함': hasBoth,
  });

  const participated = http.get(`${API}/campaigns?scope=participated`, {
    headers: { 'X-User-Id': '2' },
  });
  check(participated, {
    '나의 티켓에 이번 캠페인 2건 포함': hasBoth,
    '나의 티켓 상태 CONFIRMED': (r) =>
      r.json('campaigns')
        .filter((c) => c.id === free.id || c.id === paid.id)
        .every((c) => c.myApplicationStatus === 'CONFIRMED'),
  });

  // 정원 증원만 가능하다.
  const increase = http.patch(
    `${API}/campaigns/${paid.id}`,
    JSON.stringify({ totalStock: 20 }),
    { headers: OWNER }
  );
  check(increase, {
    '정원 증원 200': (r) => r.status === 200,
    '잔여 재고 반영': (r) => r.json('remainingStock') === 19,
  });

  const decrease = http.patch(
    `${API}/campaigns/${paid.id}`,
    JSON.stringify({ totalStock: 5 }),
    { headers: OWNER }
  );
  check(decrease, { '정원 감원 409': (r) => r.status === 409 });

  // 신청자가 있으면 삭제할 수 없다.
  const deleteRes = http.del(`${API}/campaigns/${paid.id}`, null, { headers: OWNER });
  check(deleteRes, { '신청자 있는 캠페인 삭제 409': (r) => r.status === 409 });

  // 취소 — 결제가 없던 신청은 외부 호출 없이 그 자리에서 끝난다.
  const cancelFree = http.post(`${API}/applications/${freeApplicationId}/cancel`, null, {
    headers: { 'X-User-Id': '2' },
  });
  check(cancelFree, {
    '무료 취소 200': (r) => r.status === 200,
    '무료 취소 CANCELLED': (r) => r.json('status') === 'CANCELLED',
    '무료 취소 환불 불필요': (r) => r.json('refundStatus') === 'NOT_REQUIRED',
  });
  check(http.get(`${API}/campaigns/${free.shortCode}`), {
    '무료 취소 후 재고 복원': (r) => r.json('remainingStock') === 10,
  });

  check(http.post(`${API}/applications/${freeApplicationId}/cancel`, null, {
    headers: { 'X-User-Id': '2' },
  }), { '중복 취소 409': (r) => r.status === 409 });

  check(http.post(`${API}/applications/${paidApplicationId}/cancel`, null, {
    headers: { 'X-User-Id': '999' },
  }), { '남의 신청 취소 403': (r) => r.status === 403 });

  // 결제가 있던 신청은 자리를 먼저 돌려주고 환불을 요청한다.
  const cancelPaid = http.post(`${API}/applications/${paidApplicationId}/cancel`, null, {
    headers: { 'X-User-Id': '2' },
  });
  check(cancelPaid, {
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

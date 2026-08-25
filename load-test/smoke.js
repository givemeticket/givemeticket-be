import http from 'k6/http';
import { check, sleep } from 'k6';
import { authHeaders, jsonAuthHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const API = `${BASE_URL}/api/v1`;
const OWNER = jsonAuthHeaders(1);
const USER = authHeaders(2);

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.0'],
  },
};

function createCampaign(title, detail) {
  const openAt = new Date(Date.now() + 4000).toISOString().slice(0, 19);
  const res = http.post(
    `${API}/campaigns`,
    JSON.stringify({ title, totalStock: 10, openAt, detail }),
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
  // apply 한 번으로 자리를 잡고 그대로 확정된다. 이어서 부를 확정 API 는 없다.
  const first = createCampaign('k6 smoke (1)');
  waitUntilOpen(first.shortCode);

  const firstApply = http.post(`${API}/campaigns/${first.id}/apply`, null, { headers: USER });
  check(firstApply, {
    '첫 신청 201': (r) => r.status === 201,
    '첫 신청 즉시 CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
  });
  const firstApplicationId = firstApply.json('id');

  const second = createCampaign('k6 smoke (2)');
  waitUntilOpen(second.shortCode);

  const secondApply = http.post(`${API}/campaigns/${second.id}/apply`, null, { headers: USER });
  check(secondApply, {
    '두 번째 신청 201': (r) => r.status === 201,
    '두 번째 신청 즉시 CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
  });
  const secondApplicationId = secondApply.json('id');

  check(http.get(`${API}/campaigns/${second.shortCode}`), {
    '확정 신청이 재고를 잡는다': (r) => r.json('remainingStock') === 9,
  });

  check(http.post(`${API}/campaigns/${second.id}/apply`, null, { headers: USER }), {
    '중복 신청 409': (r) => r.status === 409,
  });

  // 역할별 상세 조회.
  check(http.get(`${API}/campaigns/${second.shortCode}`), {
    '비로그인 조회 200': (r) => r.status === 200,
    '비로그인 역할 GUEST': (r) => r.json('viewerRole') === 'GUEST',
  });
  check(http.get(`${API}/campaigns/${second.shortCode}`, { headers: USER }), {
    '참여자 역할 PARTICIPANT': (r) => r.json('viewerRole') === 'PARTICIPANT',
    '내 신청 내역 포함': (r) => r.json('myApplication.status') === 'CONFIRMED',
  });
  check(http.get(`${API}/campaigns/${second.shortCode}`, { headers: OWNER }), {
    '개설자 역할 OWNER': (r) => r.json('viewerRole') === 'OWNER',
    '확정 수 노출': (r) => r.json('confirmedCount') === 1,
  });

  // 목록 조회. DB가 이전 실행 데이터를 갖고 있을 수 있으므로 개수가 아니라 포함 여부를 본다.
  const hasBoth = (r) => {
    const ids = r.json('campaigns').map((c) => c.id);
    return ids.includes(first.id) && ids.includes(second.id);
  };
  check(http.get(`${API}/campaigns?scope=owned`, { headers: OWNER }), {
    '내가 만든 행사 200': (r) => r.status === 200,
    '내가 만든 행사에 이번 캠페인 2건 포함': hasBoth,
  });
  check(http.get(`${API}/campaigns?scope=participated`, { headers: USER }), {
    '나의 티켓에 이번 캠페인 2건 포함': hasBoth,
  });

  // 정원은 늘리는 것만 가능하다.
  check(http.patch(`${API}/campaigns/${second.id}`, JSON.stringify({ totalStock: 20 }), {
    headers: OWNER,
  }), {
    '정원 증원 200': (r) => r.status === 200,
    '잔여 재고 반영': (r) => r.json('remainingStock') === 19,
  });
  check(http.patch(`${API}/campaigns/${second.id}`, JSON.stringify({ totalStock: 5 }), {
    headers: OWNER,
  }), { '정원 감원 409': (r) => r.status === 409 });

  check(http.del(`${API}/campaigns/${second.id}`, null, { headers: OWNER }), {
    '신청자 있는 캠페인 삭제 409': (r) => r.status === 409,
  });

  // 취소 — 외부 호출 없이 그 자리에서 끝나고 재고가 즉시 돌아온다.
  check(http.post(`${API}/applications/${firstApplicationId}/cancel`, null, { headers: USER }), {
    '첫 취소 200': (r) => r.status === 200,
    '첫 취소 CANCELLED': (r) => r.json('status') === 'CANCELLED',
  });
  check(http.get(`${API}/campaigns/${first.shortCode}`), {
    '첫 취소 후 재고 복원': (r) => r.json('remainingStock') === 10,
  });

  check(http.post(`${API}/applications/${firstApplicationId}/cancel`, null, { headers: USER }), {
    '중복 취소 409': (r) => r.status === 409,
  });
  check(http.post(`${API}/applications/${secondApplicationId}/cancel`, null, {
    headers: authHeaders(999),
  }), { '남의 신청 취소 403': (r) => r.status === 403 });

  check(http.post(`${API}/applications/${secondApplicationId}/cancel`, null, { headers: USER }), {
    '두 번째 취소 200': (r) => r.status === 200,
    '두 번째 취소 CANCELLED': (r) => r.json('status') === 'CANCELLED',
  });
  check(http.get(`${API}/campaigns/${second.shortCode}`), {
    '두 번째 취소 후 재고 복원': (r) => r.json('remainingStock') === 20,
  });

  // 유효한 신청이 모두 빠지면 삭제할 수 있다.
  check(http.del(`${API}/campaigns/${second.id}`, null, { headers: OWNER }), {
    '취소 후 캠페인 삭제 204': (r) => r.status === 204,
  });
  check(http.get(`${API}/campaigns/${second.shortCode}`), {
    '삭제된 캠페인 조회 410': (r) => r.status === 410,
  });

  detailChecks();
}

// 행사 안내 정보(detail)
function detailChecks() {
  // 안 넣으면 null이다.
  const bare = createCampaign('k6 detail (없음)');
  check(http.get(`${API}/campaigns/${bare.shortCode}`), {
    'detail 없으면 null': (r) => r.json('detail') === null,
  });

  // 일부만 넣어도 된다.
  const partial = createCampaign('k6 detail (일부)', { location: '올림픽공원' });
  check(http.get(`${API}/campaigns/${partial.shortCode}`), {
    '일부 필드만 저장': (r) =>
      r.json('detail.location') === '올림픽공원' && r.json('detail.content') === null,
  });

  // 전부 넣기.
  const full = {
    content: '2026 신년 공연입니다.\n입장은 30분 전부터 가능합니다.',
    eventAt: '2026-12-24T19:00:00',
    eventEndAt: '2026-12-24T21:00:00',
    location: '올림픽공원 체조경기장',
    address: '서울시 송파구 올림픽로 424',
    imageUrl: 'https://example.com/poster.png',
    contact: 'help@example.com',
    price: 30000,
  };
  const rich = createCampaign('k6 detail (전체)', full);
  const detailRes = http.get(`${API}/campaigns/${rich.shortCode}`);
  check(detailRes, {
    '본문 저장': (r) => r.json('detail.content') === full.content,
    '행사일시 저장': (r) => r.json('detail.eventAt') === full.eventAt,
    '장소·주소 저장': (r) =>
      r.json('detail.location') === full.location && r.json('detail.address') === full.address,
    '가격·문의처 저장': (r) =>
      r.json('detail.price') === 30000 && r.json('detail.contact') === full.contact,
  });

  // 목록에는 카드용 필드만 펼쳐진다.
  const owned = http.get(`${API}/campaigns?scope=owned`, { headers: OWNER });
  const item = owned.json('campaigns').find((c) => c.id === rich.id);
  check(null, {
    '목록에 행사일시·장소·이미지 노출': () =>
      item.eventAt === full.eventAt &&
      item.location === full.location &&
      item.imageUrl === full.imageUrl,
    '목록에 본문 없음': () => item.content === undefined,
  });

  // PATCH는 통째로 교체한다.
  check(http.patch(`${API}/campaigns/${rich.id}`,
    JSON.stringify({ detail: { location: '잠실실내체육관', price: 50000 } }),
    { headers: OWNER }), {
    'detail 교체 200': (r) => r.status === 200,
    '교체된 값 반영': (r) =>
      r.json('detail.location') === '잠실실내체육관' && r.json('detail.price') === 50000,
    '지정 안 한 필드는 사라짐': (r) => r.json('detail.content') === null,
  });

  // 빈 detail을 보내면 안내 정보가 지워진다.
  check(http.patch(`${API}/campaigns/${rich.id}`, JSON.stringify({ detail: {} }),
    { headers: OWNER }), {
    '빈 detail로 삭제': (r) => r.json('detail') === null,
  });

  // 길이 제한.
  check(http.post(`${API}/campaigns`,
    JSON.stringify({
      title: 'k6 detail (초과)',
      totalStock: 1,
      openAt: new Date(Date.now() + 60000).toISOString().slice(0, 19),
      detail: { content: 'x'.repeat(5001) },
    }), { headers: OWNER }), {
    '본문 5000자 초과 400': (r) => r.status === 400,
  });
}

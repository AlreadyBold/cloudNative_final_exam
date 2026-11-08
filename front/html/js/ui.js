/* =====================================================================
   ui.js : 화면을 그릴 때 공통으로 쓰는 작은 도우미 함수 모음

   ※ 보안 : 서버에서 받은 글자(약품명, 메모 등)는 항상 textContent 로 넣는다.
     innerHTML 로 넣으면 글자 안에 <script> 같은 HTML 이 섞여 있을 때 실행될 수 있기 때문이다 (XSS).
   ===================================================================== */

/** id 로 요소 찾기 */
export const $ = (id) => document.getElementById(id);

/**
 * 요소 만들기
 *  el('li', {class: 'item'}, '텍스트', 자식요소...)
 *  - attrs 의 on... 은 이벤트(onclick 등), 나머지는 속성으로 넣는다
 *  - 자식이 문자열이면 글자(textContent)로, 요소면 그대로 붙인다. null/false 는 건너뛴다
 */
export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) continue;
    if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (key === 'class') {
      node.className = value;
    } else {
      node.setAttribute(key, value === true ? '' : value);
    }
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

/** 화면 아래에 메시지를 잠깐 보여 준다 (isError 면 빨간색) */
let toastTimer = null;
export function toast(message, isError = false) {
  const box = $('toast');
  box.textContent = message;
  box.classList.toggle('error', isError);
  box.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { box.hidden = true; }, 3000);
}

/** 에러를 사용자 메시지로 보여 준다 (ApiError 면 서버 메시지 그대로) */
export function showError(error) {
  toast(error?.message || '오류가 발생했습니다.', true);
}

/** 폼 입력값을 {name: value} 객체로 (앞뒤 공백 제거) */
export function formValues(form) {
  const values = {};
  for (const [key, value] of new FormData(form).entries()) {
    values[key] = typeof value === 'string' ? value.trim() : value;
  }
  return values;
}

/**
 * 버튼을 누른 뒤 서버 응답이 올 때까지 다시 못 누르게 막는다 (중복 요청 방지)
 *  await withBusy(button, async () => { ... })
 */
export async function withBusy(button, task) {
  if (button) button.disabled = true;
  try {
    return await task();
  } finally {
    if (button) button.disabled = false;
  }
}

/** 날짜 문자열 → "2026-10-28" / 시각 포함이면 "2026-10-28 14:03" */
export function formatDate(value) {
  if (!value) return '-';
  const text = String(value);
  return text.replace('T', ' ').slice(0, text.length > 10 ? 16 : 10);
}

/**
 * 낱알 사진. 사진 주소가 없거나 불러오지 못하면 💊 아이콘으로 대신한다.
 * referrerpolicy=no-referrer : 식약처 이미지 서버에 우리 사이트 주소를 보내지 않는다
 */
export function pillImage(url, alt) {
  const placeholder = () => el('div', { class: 'thumb empty', 'aria-hidden': 'true' }, '💊');
  if (!url) return placeholder();
  const img = el('img', { class: 'thumb', src: url, alt: alt || '낱알 사진', loading: 'lazy', referrerpolicy: 'no-referrer' });
  img.addEventListener('error', () => img.replaceWith(placeholder()));
  return img;
}

/** 전문/일반의약품 배지 */
export function otcBadge(etcOtc) {
  if (!etcOtc) return null;
  return el('span', { class: etcOtc.startsWith('일반') ? 'badge otc' : 'badge etc' }, etcOtc);
}

/** 판매 상태 배지 (정상 판매면 표시 안 함) */
const SALE_STATUS_TEXT = {
  EXPORT_ONLY: '수출용', EXPIRED: '유효기간 만료', WITHDRAWN: '허가 취하', CANCELLED: '허가 취소', UNKNOWN: '판매 정보 없음',
};
export function saleBadge(status) {
  if (!status || status === 'NORMAL') return null;
  return el('span', { class: 'badge warn' }, SALE_STATUS_TEXT[status] || status);
}

/* ----- 개인정보 가리기 (화면에 보여 줄 때만 사용, 서버로 보내는 값은 그대로) ----- */

/** 이름 : 홍길동 → 홍*동, 홍길 → 홍*, 남궁민수 → 남**수 */
export function maskName(name) {
  const text = String(name || '');
  if (text.length <= 1) return text;
  if (text.length === 2) return `${text[0]}*`;
  return text[0] + '*'.repeat(text.length - 2) + text[text.length - 1];
}

/** 이메일 : demo@alyak.com → d***@alyak.com (아이디 첫 글자만 보인다) */
export function maskEmail(email) {
  const [id, domain] = String(email || '').split('@');
  if (!domain) return '***';
  return `${id.slice(0, 1)}***@${domain}`;
}

/** 휴대폰 번호 : 01012345678 → 010-****-5678 (가운데를 가린다) */
export function maskPhone(phone) {
  const digits = String(phone || '').replace(/\D/g, '');
  if (digits.length < 10) return '***';
  return `${digits.slice(0, 3)}-****-${digits.slice(-4)}`;
}

/** 주민등록번호 : 앞 6자리 + 성별 자리만 보이고 나머지는 가린다 → 900101-1****** */
export function maskIdentity(front, back) {
  return `${String(front || '').slice(0, 6)}-${String(back || '').slice(0, 1)}******`;
}

/** 휴대폰 번호 입력칸 자동 하이픈 : 01012345678 → 010-1234-5678 */
export function formatPhoneInput(value) {
  const digits = String(value || '').replace(/\D/g, '').slice(0, 11);
  if (digits.length < 4) return digits;
  if (digits.length < 8) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  const middle = digits.length === 10 ? 3 : 4; // 011-123-4567 처럼 가운데가 3자리인 번호
  return `${digits.slice(0, 3)}-${digits.slice(3, 3 + middle)}-${digits.slice(3 + middle)}`;
}

/* ----- 날짜 (복용 기간) ----- */

/** 오늘 날짜 "2026-11-08" (브라우저의 현지 날짜 기준) */
export function todayString() {
  return toDateString(new Date());
}

/** "2026-11-08" 에 n 일을 더한 날짜 문자열 */
export function addDays(dateString, days) {
  const [y, m, d] = dateString.split('-').map(Number);
  return toDateString(new Date(y, m - 1, d + days));
}

function toDateString(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 복용 상태 배지 : TAKING 복용 중 / UPCOMING 복용 예정 / DONE 복용 완료 */
const STATUS_TEXT = { TAKING: '복용 중', UPCOMING: '복용 예정', DONE: '복용 완료' };
export function statusBadge(status) {
  return el('span', { class: `badge ${String(status).toLowerCase()}` }, STATUS_TEXT[status] || status);
}

/** 목록이 비었을 때 보여 줄 문구 */
export function emptyItem(text) {
  return el('li', { class: 'empty-text' }, text);
}

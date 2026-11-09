/* =====================================================================
   pillbox.js : 내 약통 (pill-api, 로그인 필요) — 먹는 약과 복용 기간을 관리하는 화면
     GET    /api/pill/pillbox       : 목록 (복용 상태 포함)
     POST   /api/pill/pillbox       : 담기 (복용 시작일·종료일 지정)
     PATCH  /api/pill/pillbox/{id}  : 복용 기간·메모 수정
     DELETE /api/pill/pillbox/{id}  : 빼기
     GET    /api/pill/pillbox/interactions : 함께 먹는 약 검사 (병용금기 · 같은 성분 중복 · 같은 효능)

   [복용 상태] 서버가 오늘 날짜로 계산해서 준다 : TAKING 복용 중 / UPCOMING 복용 예정 / DONE 복용 완료
   회원 번호는 보내지 않는다. pill-api 가 SESSION 쿠키로 Redis 세션을 읽어 알아낸다.
   ===================================================================== */
import { api } from './api.js';
import { renderCalendar } from './calendar.js';
import { toggleDetail } from './detail.js';
import {
  $, addDays, el, emptyItem, formValues, formatDate, otcBadge, pillImage, showError, statusBadge, toast,
  todayString, withBusy,
} from './ui.js';

const SOURCE_TEXT = { SEARCH: '검색에서 담음', PRESCRIPTION: '처방에서 담음' };

/**
 * 마지막으로 불러온 목록, 지금 고른 상태 필터(화면에서만 거른다), 병용금기 검사 결과
 *  checks : {약통 항목 id: 'CONFLICT' | 'CHECKED' | 'UNKNOWN'} (끝난 약은 검사하지 않아 없음)
 */
const state = { items: [], filter: 'ALL', pairs: [], checks: {}, sameIngredient: [], sameEfficacy: [] };

/** 약마다 보여 줄 병용금기 검사 배지 */
const CHECK_BADGE = {
  CONFLICT: ['badge danger', '⚠ 병용금기'],
  CHECKED: ['badge', '✓ 병용 확인'],
  UNKNOWN: ['badge', '? 성분 정보 없음'],
};

/** 모달에서 담을 약 정보 (openAddDialog 가 채운다) */
let pendingDrug = null;

export function initPillbox() {
  // 상태 필터 칩
  $('pillbox-filters').addEventListener('click', (event) => {
    const chip = event.target.closest('[data-status]');
    if (!chip) return;
    state.filter = chip.dataset.status;
    $('pillbox-filters').querySelectorAll('.chip').forEach((c) => c.classList.toggle('active', c === chip));
    render();
  });

  // ----- 약통에 담기 모달 -----
  const dialog = $('add-dialog');
  const form = $('add-form');
  $('add-close').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close(); // 바깥(어두운 배경)을 누르면 닫기
  });
  dialog.addEventListener('close', () => { pendingDrug = null; });
  // [1일][3일][7일][30일] : 시작일부터 그 기간만큼 먹도록 종료일을 채운다 (7일 = 시작일 + 6일)
  form.querySelector('.quick-days').addEventListener('click', (event) => {
    const chip = event.target.closest('[data-days]');
    if (!chip) return;
    const start = form.elements.start_date.value || todayString();
    form.elements.end_date.value = addDays(start, Number(chip.dataset.days) - 1);
  });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    await withBusy(form.querySelector('button[type=submit]'), submitAdd);
  });
}

/**
 * 약통에 담기 모달 열기 (약 검색 화면 등에서 부른다)
 * @param {object} drug {item_seq, drug_name, source} 또는 {drug_code, drug_name, source}
 */
export function openAddDialog(drug) {
  pendingDrug = drug;
  const form = $('add-form');
  form.reset();
  $('add-drug-name').textContent = drug.drug_name || '';
  $('add-error').hidden = true;
  // 기본값 : 오늘부터 7일
  form.elements.start_date.value = todayString();
  form.elements.end_date.value = addDays(todayString(), 6);
  $('add-dialog').showModal();
}

/** 모달의 [담기] : POST /api/pill/pillbox */
async function submitAdd() {
  const values = formValues($('add-form'));
  try {
    const item = await api.post('/api/pill/pillbox', { ...pendingDrug, ...values });
    $('add-dialog').close();
    toast(`내 약통에 담았습니다 : ${item.drug_name}`);
    if (!$('tab-pillbox').hidden) await loadPillbox();
  } catch (error) {
    // 모달은 화면 맨 위 층이라 하단 알림이 가려지므로 모달 안에 보여 준다 (예: 같은 날부터 먹는 같은 약)
    $('add-error').textContent = error.message;
    $('add-error').hidden = false;
  }
}

/** 약통 탭을 열 때마다 목록을 새로 불러온다 */
export async function loadPillbox() {
  try {
    state.items = await api.get('/api/pill/pillbox');
  } catch (error) {
    showError(error);
    return;
  }
  // 병용금기 검사는 실패해도 약통 목록은 보여 준다 (검사 결과만 비워 둠)
  try {
    const result = await api.get('/api/pill/pillbox/interactions');
    state.pairs = result.pairs;
    state.checks = Object.fromEntries(result.items.map((i) => [i.id, i.check]));
    state.sameIngredient = result.same_ingredient;
    state.sameEfficacy = result.same_efficacy;
  } catch (error) {
    state.pairs = [];
    state.checks = {};
    state.sameIngredient = [];
    state.sameEfficacy = [];
  }
  // 캘린더에서 날짜를 고를 때마다 그날에 해당하는 검사 결과만 보여 준다 (처음에는 오늘)
  renderCalendar($('pillbox-calendar'), state.items, state.pairs, dosageText, renderDurAlert);
  render();
}

/** "11-10 ~ 11-16 함께 복용" (두 약을 함께 먹게 되는 기간) */
function overlapText(p) {
  return `${formatDate(p.overlap_start)} ~ ${formatDate(p.overlap_end)} 함께 복용`;
}

/** 그날 두 약을 함께 먹는지 (겹치는 기간 안에 그 날짜가 있는지) */
const onDate = (date) => (p) => p.overlap_start <= date && date <= p.overlap_end;

/** "11월 10일" */
function dayLabel(date) {
  const [, m, d] = date.split('-').map(Number);
  return `${m}월 ${d}일`;
}

/**
 * 함께 먹는 약 검사 결과 : 캘린더에서 고른 날짜에 해당하는 것만 보여 준다 (해당 없으면 숨김)
 *  1) ⚠ 병용금기       : 약 쌍, 사유, 성분, 함께 먹게 되는 기간
 *  2) ⚠ 같은 성분 중복 : 같은 성분이 두 번 들어감 → 과다 복용 위험
 *  3) ℹ 같은 효능      : 비슷한 효능의 약을 함께 먹고 있음 (참고)
 * @param {string} date 캘린더에서 고른 날짜 "YYYY-MM-DD"
 */
function renderDurAlert(date) {
  const box = $('dur-alert');
  const pairs = state.pairs.filter(onDate(date));
  const sameIngredient = state.sameIngredient.filter(onDate(date));
  const sameEfficacy = state.sameEfficacy.filter(onDate(date));
  const notes = [
    pairs.length ? durBanner(date, pairs) : null,
    sameIngredient.length ? sameIngredientNote(date, sameIngredient) : null,
    sameEfficacy.length ? sameEfficacyNote(date, sameEfficacy) : null,
  ].filter(Boolean);
  box.replaceChildren(...notes);
  box.hidden = notes.length === 0;
}

/** ⚠ 병용금기 (빨강) */
function durBanner(date, pairs) {
  return el('div', { class: 'dur-alert danger' },
    el('div', { class: 'dur-title' }, `⚠ ${dayLabel(date)}에 함께 먹으면 안 되는 약이 ${pairs.length}쌍 있습니다`),
    el('ul', {}, pairs.map((p) => el('li', {},
      el('b', {}, `${p.item_a.drug_name} × ${p.item_b.drug_name}`),
      el('div', {}, p.detail || '병용금기'),
      el('div', { class: 'dur-sub-text' }, `${p.ingredient_a} × ${p.ingredient_b} · ${overlapText(p)}`)))),
    el('div', { class: 'dur-sub-text' },
      '심평원 DUR(의약품안전사용서비스) 병용금기 정보를 바탕으로 한 안내입니다. 복용을 바꾸기 전에 의사·약사와 상담하세요.'));
}

/** ⚠ 같은 성분 중복 (주황) */
function sameIngredientNote(date, list) {
  return el('div', { class: 'dur-alert warn' },
    el('div', { class: 'dur-title' }, `⚠ ${dayLabel(date)}에 같은 성분이 겹치는 약이 ${list.length}쌍 있습니다 (과다 복용 주의)`),
    el('ul', {}, list.map((p) => el('li', {},
      el('b', {}, `${p.item_a.drug_name} × ${p.item_b.drug_name}`),
      el('div', {}, `겹치는 성분 : ${p.ingredients.join(', ')}`),
      el('div', { class: 'dur-sub-text' }, overlapText(p))))));
}

/** ℹ 같은 효능 (파랑) */
function sameEfficacyNote(date, list) {
  return el('div', { class: 'dur-alert info' },
    el('div', { class: 'dur-title' }, `ℹ ${dayLabel(date)}에 효능이 비슷한 약이 ${list.length}쌍 있습니다`),
    el('ul', {}, list.map((p) => el('li', {},
      el('b', {}, `${p.item_a.drug_name} × ${p.item_b.drug_name}`),
      el('div', {}, `같은 약효 분류 : ${p.class_name || ''} (ATC ${p.atc_group})`),
      el('div', { class: 'dur-sub-text' }, overlapText(p))))));
}

/** 현재 필터에 맞는 항목만 그린다 */
function render() {
  const items = state.filter === 'ALL' ? state.items : state.items.filter((i) => i.status === state.filter);
  const list = $('pillbox-list');
  list.replaceChildren(...items.map(renderItem));
  if (items.length === 0) {
    list.append(emptyItem(state.items.length === 0
      ? '아직 담은 약이 없습니다. 약 검색이나 처방내역 불러오기에서 담아 보세요.'
      : '이 상태의 약이 없습니다.'));
  }
}

/** "2026-11-08 ~ 2026-11-14 · 3일 남음" 처럼 복용 기간과 남은 날 */
function periodText(item) {
  const range = `${formatDate(item.start_date)} ~ ${formatDate(item.end_date)}`;
  if (item.status === 'TAKING') return `${range} · ${item.days_left === 0 ? '오늘까지' : `${item.days_left}일 남음`}`;
  if (item.status === 'UPCOMING') return `${range} · 시작 전`;
  return range;
}

/**
 * 약효·복용법 한 줄 : "해열·진통·소염제 · 하루 3번 · 1번에 1개씩 · 7일분" (처방에서 담은 약만 값이 있다)
 * CODEF(심평원) 조제 내역의 약효, 1일 투여횟수, 1회 투약량, 총 투약일수를 그대로 보여 준다.
 * 1회 투약량이 숫자면 "개씩"을 붙이고, 숫자가 아니면(예: 0.5정, 10mL) 받은 값 그대로 쓴다.
 */
function dosageLine(item) {
  const text = dosageText(item, true);
  return text ? el('div', { class: 'dosage' }, `💊 ${text}`) : null;
}

/** 복용법 글자 (캘린더의 날짜별 목록에서도 쓴다). withDays 면 "7일분"까지 붙인다 */
function dosageText(item, withDays = false) {
  const isNumber = (value) => /^\d+(\.\d+)?$/.test(String(value || '').trim());
  return [
    item.effect,
    item.daily_doses && `하루 ${item.daily_doses}번`,
    item.one_dose && `1번에 ${item.one_dose}${isNumber(item.one_dose) ? '개' : ''}씩`,
    withDays && item.total_dosing_days && `${item.total_dosing_days}일분`,
  ].filter(Boolean).join(' · ');
}

/** 약통 항목 한 줄 : 상태 배지 + 기간 + 복용법 + [수정](기간·메모) + [상세] + [빼기] */
function renderItem(item) {
  const detailBox = el('div', { class: 'detail', hidden: true });
  const startInput = el('input', { type: 'date', value: item.start_date, 'aria-label': '복용 시작일' });
  const endInput = el('input', { type: 'date', value: item.end_date, 'aria-label': '복용 종료일' });
  const memoInput = el('input', { value: item.memo || '', placeholder: '메모 (예: 아침 식후 1알)', maxlength: '200' });
  const editRow = el('div', { class: 'row edit-row', hidden: true },
    startInput, '~', endInput, memoInput,
    el('button', {
      type: 'button', class: 'btn small primary',
      onclick: (event) => withBusy(event.currentTarget, () => saveEdit(item.id, {
        start_date: startInput.value, end_date: endInput.value, memo: memoInput.value,
      })),
    }, '저장'));

  return el('li', { class: `item ${item.status === 'DONE' ? 'done' : ''}` },
    pillImage(item.image_url, item.drug_name),
    el('div', { class: 'body' },
      el('div', { class: 'title' }, statusBadge(item.status), ' ', item.drug_name, otcBadge(item.etc_otc),
        checkBadge(state.checks[item.id])),
      el('div', { class: 'period' }, periodText(item)),
      dosageLine(item),
      el('div', { class: 'meta' },
        [item.memo, item.entp_name, SOURCE_TEXT[item.source]].filter(Boolean).join(' · '),
        // 카탈로그와 연결되지 않은 약(처방에만 있는 약)은 사진·상세가 없다는 표시
        item.item_seq ? null : el('span', { class: 'badge warn' }, '카탈로그 미연결')),
      editRow,
      detailBox),
    el('div', { class: 'actions' },
      el('button', {
        type: 'button', class: 'btn small',
        onclick: () => { editRow.hidden = !editRow.hidden; },
      }, '수정'),
      // 카탈로그와 연결된 약만 상세 정보(성분·성상·DUR)가 있다
      item.item_seq ? el('button', {
        type: 'button', class: 'btn small detail-toggle',
        onclick: (event) => withBusy(event.currentTarget, () => toggleDetail(item.item_seq, detailBox, event.currentTarget)),
      }, '상세') : null,
      el('button', {
        type: 'button', class: 'btn small danger',
        onclick: (event) => withBusy(event.currentTarget, () => removeItem(item)),
      }, '빼기')));
}

/** 병용금기 검사 배지 (끝난 약은 검사하지 않으므로 없음) */
function checkBadge(check) {
  if (!check) return null;
  const [className, text] = CHECK_BADGE[check];
  return el('span', { class: className }, text);
}

/** 복용 기간·메모 저장 : PATCH /api/pill/pillbox/{id} */
async function saveEdit(id, body) {
  try {
    await api.patch(`/api/pill/pillbox/${id}`, body);
    toast('저장했습니다.');
    await loadPillbox();
  } catch (error) {
    showError(error);
  }
}

/** 약통에서 빼기 : DELETE /api/pill/pillbox/{id} */
async function removeItem(item) {
  if (!confirm(`"${item.drug_name}" 을(를) 약통에서 뺄까요?`)) return;
  try {
    await api.delete(`/api/pill/pillbox/${item.id}`);
    toast('약통에서 뺐습니다.');
    await loadPillbox();
  } catch (error) {
    showError(error);
  }
}

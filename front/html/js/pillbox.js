/* =====================================================================
   pillbox.js : 내 약통 (pill-api, 로그인 필요) — 먹는 약과 복용 기간을 관리하는 화면
     GET    /api/pill/pillbox       : 목록 (복용 상태 포함)
     POST   /api/pill/pillbox       : 담기 (복용 시작일·종료일 지정)
     PATCH  /api/pill/pillbox/{id}  : 복용 기간·메모 수정
     DELETE /api/pill/pillbox/{id}  : 빼기

   [복용 상태] 서버가 오늘 날짜로 계산해서 준다 : TAKING 복용 중 / UPCOMING 복용 예정 / DONE 복용 완료
   회원 번호는 보내지 않는다. pill-api 가 SESSION 쿠키로 Redis 세션을 읽어 알아낸다.
   ===================================================================== */
import { api } from './api.js';
import { toggleDetail } from './detail.js';
import {
  $, addDays, el, emptyItem, formValues, formatDate, otcBadge, pillImage, showError, statusBadge, toast,
  todayString, withBusy,
} from './ui.js';

const SOURCE_TEXT = { SEARCH: '검색에서 담음', PRESCRIPTION: '처방에서 담음' };

/** 마지막으로 불러온 목록과 지금 고른 상태 필터 (필터는 화면에서만 거른다) */
const state = { items: [], filter: 'ALL' };

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
    render();
  } catch (error) {
    showError(error);
  }
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

/** 약통 항목 한 줄 : 상태 배지 + 기간 + [수정](기간·메모) + [상세] + [빼기] */
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
      el('div', { class: 'title' }, statusBadge(item.status), ' ', item.drug_name, otcBadge(item.etc_otc)),
      el('div', { class: 'period' }, periodText(item)),
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
        type: 'button', class: 'btn small',
        onclick: (event) => withBusy(event.currentTarget, () => toggleDetail(item.item_seq, detailBox)),
      }, '상세') : null,
      el('button', {
        type: 'button', class: 'btn small danger',
        onclick: (event) => withBusy(event.currentTarget, () => removeItem(item)),
      }, '빼기')));
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

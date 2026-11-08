/* =====================================================================
   saved.js : 저장한 처방 (pill-api, 로그인 필요)
     GET    /api/pill/prescriptions       : 저장한 처방 목록 (약 포함, 최근 조제일 순)
     DELETE /api/pill/prescriptions/{id}  : 처방 삭제 (안의 약도 함께 삭제)
   처방 안의 약 한 줄을 그리는 renderDrug 는 "처방내역 불러오기" 화면(importer.js)에서도 쓴다.
   ===================================================================== */
import { api } from './api.js';
import { toggleDetail } from './detail.js';
import { openAddDialog } from './pillbox.js';
import { $, el, emptyItem, formatDate, pillImage, showError, toast, withBusy } from './ui.js';

/** 저장한 처방 탭을 열 때마다 새로 불러온다 */
export async function loadSaved() {
  const list = $('saved-list');
  try {
    const prescriptions = await api.get('/api/pill/prescriptions');
    list.replaceChildren(...prescriptions.map(renderPrescription));
    if (prescriptions.length === 0) list.append(emptyItem('저장한 처방이 없습니다. [처방내역 불러오기]에서 저장해 보세요.'));
  } catch (error) {
    showError(error);
  }
}

/** 처방 1건 (머리 : 조제일·병원·약국 + 삭제 버튼 / 몸통 : 약 목록) */
function renderPrescription(rx) {
  return el('li', { class: 'rx' },
    el('div', { class: 'rx-head' },
      el('div', {},
        el('b', {}, formatDate(rx.dispensed_date)), ' ',
        [rx.prescriber, rx.pharmacy].filter(Boolean).join(' · '),
        sourceBadge(rx.data_source)),
      el('button', {
        type: 'button', class: 'btn small danger',
        onclick: (event) => withBusy(event.currentTarget, () => removePrescription(rx)),
      }, '삭제')),
    el('ul', { class: 'rx-drugs' }, rx.drugs.map((drug) => renderDrug({
      code: drug.drug_code, name: drug.drug_name, ingredients: drug.ingredients,
      oneDose: drug.one_dose, dailyDoses: drug.daily_doses, days: drug.total_dosing_days,
      effect: drug.effect, imageUrl: drug.image_url, itemSeq: drug.item_seq,
    }))));
}

/** 데이터 출처 배지 : MOCK(CODEF 키 없이 돌려준 샘플)이면 표시 */
export function sourceBadge(source) {
  return source === 'MOCK' ? el('span', { class: 'badge warn' }, '샘플 데이터') : null;
}

/**
 * 처방 안의 약 한 줄 + [약통에 담기]
 * @param {object} d {code, name, ingredients, oneDose, dailyDoses, days, effect, imageUrl, itemSeq}
 *   itemSeq : 카탈로그와 연결된 약 ID. 있으면 [상세] 버튼(성분·성상·DUR)을 붙이고, 없으면 "카탈로그 미연결" 표시
 */
export function renderDrug(d) {
  // "1회 1 · 하루 3번 · 5일" 처럼 복용법을 한 줄로
  const dosage = [
    d.oneDose && `1회 ${d.oneDose}`,
    d.dailyDoses && `하루 ${d.dailyDoses}번`,
    d.days && `${d.days}일`,
  ].filter(Boolean).join(' · ');

  const detailBox = el('div', { class: 'detail', hidden: true });

  return el('li', { class: 'item' },
    pillImage(d.imageUrl, d.name),
    el('div', { class: 'body' },
      el('div', { class: 'title' }, d.name),
      el('div', { class: 'meta' }, [d.ingredients, d.effect, dosage].filter(Boolean).join(' · ')),
      d.itemSeq ? null : el('span', { class: 'badge warn' }, '카탈로그 미연결'),
      detailBox),
    el('div', { class: 'actions' },
      el('button', {
        type: 'button', class: 'btn small primary',
        onclick: () => openAddDialog({ drug_code: d.code || null, drug_name: d.name, source: 'PRESCRIPTION' }),
      }, '약통에 담기'),
      // 카탈로그와 연결된 약만 상세 정보가 있다
      d.itemSeq ? el('button', {
        type: 'button', class: 'btn small',
        onclick: (event) => withBusy(event.currentTarget, () => toggleDetail(d.itemSeq, detailBox)),
      }, '상세') : null));
}

/** 처방 삭제 : DELETE /api/pill/prescriptions/{id} (내 약통에 담아 둔 약은 지워지지 않는다) */
async function removePrescription(rx) {
  if (!confirm(`${formatDate(rx.dispensed_date)} 처방을 삭제할까요? (내 약통은 그대로 둡니다)`)) return;
  try {
    await api.delete(`/api/pill/prescriptions/${rx.id}`);
    toast('처방을 삭제했습니다.');
    await loadSaved();
  } catch (error) {
    showError(error);
  }
}

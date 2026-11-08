/* =====================================================================
   detail.js : 약 상세 보기 (여러 화면에서 같이 쓰는 모듈)
     GET /api/pill/catalog/{item_seq}  →  성분, 성상, DUR 주의 정보

   [DUR(의약품 안전사용 정보) 표시 순서]
     임부금기 → 연령금기 → 노인주의 → 병용금기
   병용금기는 같이 먹으면 안 되는 성분이 많은 약(수십~백여 개)이 있어서
   처음에는 몇 개만 보여 주고 [모두 보기]로 펼친다.
   ===================================================================== */
import { api } from './api.js';
import { el, showError } from './ui.js';

/** 병용금기를 처음에 몇 개까지 보여 줄지 */
const COMBINATION_PREVIEW = 5;

/**
 * [상세] 버튼 동작 : 처음 누를 때만 서버에서 가져오고, 다시 누르면 접는다
 * @param {string} itemSeq 카탈로그 약 ID
 * @param {HTMLElement} box 상세 내용을 넣을 영역 (처음에는 hidden)
 */
export async function toggleDetail(itemSeq, box) {
  if (!box.hidden) {
    box.hidden = true;
    return;
  }
  if (!box.dataset.loaded) {
    try {
      const d = await api.get(`/api/pill/catalog/${encodeURIComponent(itemSeq)}`);
      box.replaceChildren(...renderDetail(d));
      box.dataset.loaded = '1';
    } catch (error) {
      showError(error);
      return;
    }
  }
  box.hidden = false;
}

/** 상세 내용 : 성분·성상 + DUR 주의 정보 */
function renderDetail(d) {
  // 사용자에게 의미 있는 정보만 보여 준다 (제형·각인·크기·보험코드는 낱알 식별용이라 뺐다)
  const rows = [
    ['성분', d.main_ingredient],
    ['성상', d.chart],
  ].filter(([, value]) => value).map(([label, value]) => el('div', {}, el('b', {}, `${label} : `), value));

  return [...rows, el('div', { class: 'dur' }, el('b', {}, 'DUR 주의 정보'), ...renderDur(d.dur))];
}

/** DUR 주의 정보 목록. 하나도 없으면 "없음" 문구 */
function renderDur(dur) {
  if (!dur) return [el('p', { class: 'dur-none' }, 'DUR 정보를 불러오지 못했습니다.')];
  const items = [
    ...dur.pregnancy.map((x) => durItem(`임부금기${x.grade ? ` ${x.grade}등급` : ''}`, x.detail)),
    ...dur.age.map((x) => durItem(`연령금기${x.age_condition ? ` (${x.age_condition})` : ''}`, x.detail)),
    ...dur.elderly.map((x) => durItem('노인주의', x.detail)),
  ];
  if (dur.combination.length > 0) {
    items.push(renderCombination(dur.combination));
  }
  if (items.length === 0) {
    return [el('p', { class: 'dur-none' }, 'DUR 주의 정보가 없습니다.')];
  }
  return [el('ul', { class: 'dur-list' }, items)];
}

/** DUR 한 줄 : [종류] 내용 */
function durItem(label, detail) {
  return el('li', {}, el('span', { class: 'badge warn' }, label), ' ', detail || '');
}

/** 병용금기 : 같이 먹으면 안 되는 성분 목록 (처음엔 일부만, [모두 보기]로 펼침) */
function renderCombination(list) {
  const lines = list.map((x) => el('li', {}, el('b', {}, x.partner_ingredient || '-'), x.detail ? ` : ${x.detail}` : ''));
  const shown = el('ul', { class: 'dur-sub' }, lines.slice(0, COMBINATION_PREVIEW));
  const item = el('li', {},
    el('span', { class: 'badge warn' }, '병용금기'), ` 같이 먹으면 안 되는 성분 ${list.length}개`, shown);
  if (list.length > COMBINATION_PREVIEW) {
    const more = el('button', {
      type: 'button', class: 'btn link',
      onclick: () => { shown.replaceChildren(...lines); more.remove(); },
    }, `모두 보기 (${list.length}개)`);
    item.append(more);
  }
  return item;
}

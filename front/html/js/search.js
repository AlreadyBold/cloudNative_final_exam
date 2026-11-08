/* =====================================================================
   search.js : 약 검색 (pill-api)
     GET /api/pill/catalog/search?q=...&page=1&size=20  : 이름 검색 (전문·일반의약품 모두)
     GET /api/pill/catalog/{item_seq}                   : 상세 (성분, 성상, DUR 주의 정보) → detail.js
   검색 결과에서 [약통에 담기]를 누르면 복용 기간을 고르는 모달(pillbox.js 의 openAddDialog)이 뜬다.
   ===================================================================== */
import { api } from './api.js';
import { toggleDetail } from './detail.js';
import { openAddDialog } from './pillbox.js';
import { $, el, emptyItem, otcBadge, pillImage, saleBadge, showError, withBusy } from './ui.js';

const PAGE_SIZE = 20;

/** 지금 보고 있는 검색어와 페이지 ([더 보기]에서 사용) */
const state = { q: '', page: 1 };

export function initSearch() {
  // [검색] : 첫 페이지부터 새로 찾는다
  $('search-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const q = event.currentTarget.elements.q.value.trim();
    if (!q) return;
    state.q = q;
    state.page = 1;
    $('search-results').replaceChildren();
    await withBusy(event.currentTarget.querySelector('button'), loadPage);
  });

  // [더 보기] : 다음 페이지를 이어 붙인다
  $('search-more').addEventListener('click', async (event) => {
    state.page += 1;
    await withBusy(event.currentTarget, loadPage);
  });
}

/** 현재 검색어·페이지로 검색해서 목록에 붙인다 */
async function loadPage() {
  const list = $('search-results');
  try {
    const query = new URLSearchParams({ q: state.q, page: state.page, size: PAGE_SIZE });
    const result = await api.get(`/api/pill/catalog/search?${query}`);
    if (state.page === 1 && result.items.length === 0) {
      list.append(emptyItem('검색 결과가 없습니다.'));
    }
    result.items.forEach((pill) => list.append(renderPill(pill)));
    $('search-more').hidden = !result.has_next;
  } catch (error) {
    showError(error);
  }
}

/** 검색 결과 한 줄 */
function renderPill(pill) {
  const detailBox = el('div', { class: 'detail', hidden: true });

  const item = el('li', { class: 'item' },
    pillImage(pill.image_url, pill.item_name),
    el('div', { class: 'body' },
      el('div', { class: 'title' }, pill.item_name, otcBadge(pill.etc_otc), saleBadge(pill.sale_status)),
      el('div', { class: 'meta' },
        [pill.entp_name, pill.class_name, shapeText(pill)].filter(Boolean).join(' · ')),
      detailBox),
    el('div', { class: 'actions' },
      el('button', {
        type: 'button', class: 'btn small primary',
        onclick: () => openAddDialog({ item_seq: pill.item_seq, drug_name: pill.item_name, source: 'SEARCH' }),
      }, '약통에 담기'),
      el('button', {
        type: 'button', class: 'btn small',
        onclick: (event) => withBusy(event.currentTarget, () => toggleDetail(pill.item_seq, detailBox)),
      }, '상세')));
  return item;
}

/** "흰색 원형" 처럼 모양·색 설명 (알약이 아니거나 정보가 없으면 빈 값) */
export function shapeText(pill) {
  return [pill.color1, pill.drug_shape].filter(Boolean).join(' ');
}

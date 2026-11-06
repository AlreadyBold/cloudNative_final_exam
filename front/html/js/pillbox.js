/* =====================================================================
   pillbox.js : 내 약통 (pill-api, 로그인 필요)
     GET    /api/pill/pillbox       : 목록
     POST   /api/pill/pillbox       : 담기 (검색 결과 또는 처방내역에서)
     PATCH  /api/pill/pillbox/{id}  : 메모 수정
     DELETE /api/pill/pillbox/{id}  : 빼기
   회원 번호는 보내지 않는다. pill-api 가 SESSION 쿠키로 Redis 세션을 읽어 알아낸다.
   ===================================================================== */
import { api } from './api.js';
import { $, el, emptyItem, formatDate, otcBadge, pillImage, showError, toast, withBusy } from './ui.js';

const SOURCE_TEXT = { SEARCH: '검색에서 담음', PRESCRIPTION: '처방에서 담음' };

/**
 * 약통에 담기 (다른 화면에서도 부른다)
 * @param {object} body {item_seq, source} 또는 {drug_code, drug_name, source}
 */
export async function addToPillbox(body) {
  try {
    const item = await api.post('/api/pill/pillbox', body);
    toast(`내 약통에 담았습니다 : ${item.drug_name}`);
  } catch (error) {
    showError(error); // 이미 담긴 약이면 409 "이미 내 약통에 담긴 약입니다."
  }
}

/** 약통 탭을 열 때마다 목록을 새로 불러온다 */
export async function loadPillbox() {
  const list = $('pillbox-list');
  try {
    const items = await api.get('/api/pill/pillbox');
    list.replaceChildren(...items.map(renderItem));
    if (items.length === 0) list.append(emptyItem('아직 담은 약이 없습니다. 약 검색이나 처방내역에서 담아 보세요.'));
  } catch (error) {
    showError(error);
  }
}

/** 약통 항목 한 줄 (메모 입력칸 포함) */
function renderItem(item) {
  const memoInput = el('input', { value: item.memo || '', placeholder: '메모 (예: 아침 식후 1알)', maxlength: '200' });

  return el('li', { class: 'item' },
    pillImage(item.image_url, item.drug_name),
    el('div', { class: 'body' },
      el('div', { class: 'title' }, item.drug_name, otcBadge(item.etc_otc)),
      el('div', { class: 'meta' },
        [item.entp_name, SOURCE_TEXT[item.source], `${formatDate(item.created_at)} 담음`].filter(Boolean).join(' · '),
        // 카탈로그와 연결되지 않은 약(처방에만 있는 약)은 사진·상세가 없다는 표시
        item.item_seq ? null : el('span', { class: 'badge warn' }, '카탈로그 미연결')),
      el('div', { class: 'row' },
        memoInput,
        el('button', {
          type: 'button', class: 'btn small',
          onclick: (event) => withBusy(event.currentTarget, () => saveMemo(item.id, memoInput.value)),
        }, '메모 저장'))),
    el('div', { class: 'actions' },
      el('button', {
        type: 'button', class: 'btn small danger',
        onclick: (event) => withBusy(event.currentTarget, () => removeItem(item)),
      }, '빼기')));
}

/** 메모 저장 : PATCH /api/pill/pillbox/{id} */
async function saveMemo(id, memo) {
  try {
    await api.patch(`/api/pill/pillbox/${id}`, { memo });
    toast('메모를 저장했습니다.');
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

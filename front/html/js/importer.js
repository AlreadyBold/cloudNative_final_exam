/* =====================================================================
   importer.js : 처방내역 불러오기 (간편인증 2단계) → 저장

   [흐름]
    1. [인증 요청]  POST /api/user/prescriptions/import            (user-api)
       → 사용자 휴대폰의 카카오톡 등으로 인증 요청이 감. import_id 와 남은 시간(270초)을 받는다.
       ※ CODEF 키가 없는 서버는 모의(MOCK) 모드 : 휴대폰 승인 없이 바로 [인증 완료]를 누르면 샘플 데이터가 온다.
       ※ 같은 아이디로 다른 기기에서 진행 중이면 409 (PRESCRIPTION_IMPORT_IN_PROGRESS)
    2. [인증 완료]  POST /api/user/prescriptions/import/{id}/complete (user-api, 시작한 브라우저에서만 가능)
       → 최근 1년 조제 내역
    3. [선택한 처방 저장]  POST /api/pill/prescriptions              (pill-api)
       → 이미 저장한 처방은 서버가 건너뛰고 "저장 n건 / 건너뜀 m건"을 알려 준다.

   주민등록번호는 2단계에서 한 번 더 필요하다. 서버에 저장하지 않으려고 화면 메모리(변수)에만 잠깐 두고,
   끝나면 바로 지운다. (localStorage 등 브라우저 저장소에는 절대 넣지 않는다)
   ===================================================================== */
import { api } from './api.js';
import { renderDrug, sourceBadge } from './saved.js';
import { $, el, formValues, formatDate, showError, toast, withBusy } from './ui.js';

/** 진행 중인 불러오기 상태 */
const state = {
  importId: null,     // 1단계에서 받은 ID
  identity: null,     // 2단계에 다시 보낼 주민등록번호 (메모리에만)
  timer: null,        // 남은 시간 카운트다운 타이머
  source: null,       // CODEF / MOCK
  prescriptions: [],  // 3단계에서 저장할 후보
};

export function initImporter() {
  $('import-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    await withBusy(event.currentTarget.querySelector('button'), () => start(event.currentTarget));
  });
  $('import-complete-btn').addEventListener('click', (event) => withBusy(event.currentTarget, complete));
  $('import-restart-btn').addEventListener('click', () => resetToForm());
  $('import-save-btn').addEventListener('click', (event) => withBusy(event.currentTarget, saveSelected));

  const form = $('import-form');
  // 주민등록번호 : 숫자만 입력되게 하고, 앞 6자리를 다 쓰면 뒷자리 칸으로 자동 이동
  ['identity_front', 'identity_back'].forEach((name) => {
    form.elements[name].addEventListener('input', (event) => {
      event.target.value = event.target.value.replace(/\D/g, '');
      if (name === 'identity_front' && event.target.value.length === 6) {
        form.elements.identity_back.focus();
      }
    });
  });
}

/** 1단계 : 간편인증 요청 */
async function start(form) {
  const values = formValues(form);
  // 서버로 보낼 값 : 주민등록번호는 앞·뒤를 합쳐 "900101-1234567" 형태로
  const identity = `${values.identity_front}-${values.identity_back}`;
  const body = {
    auth_app: values.auth_app, user_name: values.user_name, identity, phone_number: values.phone_number,
  };
  try {
    const result = await api.post('/api/user/prescriptions/import', body);
    state.importId = result.import_id;
    state.identity = identity;
    // 민감한 입력칸은 바로 비운다 (화면에 남기지 않음)
    form.elements.identity_front.value = '';
    form.elements.identity_back.value = '';

    $('import-wait-text').textContent = result.source === 'MOCK'
      ? '샘플(MOCK) 모드입니다. 휴대폰 승인 없이 [인증 완료]를 누르면 샘플 처방내역을 불러옵니다.'
      : '휴대폰의 간편인증 앱에서 [인증]을 누른 뒤, 아래 [인증 완료]를 눌러 주세요.';
    form.hidden = true;
    $('import-result').hidden = true;
    $('import-wait').hidden = false;
    $('import-complete-btn').disabled = false;
    startCountdown(result.expires_in_seconds);
  } catch (error) {
    showError(error); // 다른 기기에서 진행 중이면 "약 N초 후에 다시 시도해 주세요."
  }
}

/** 남은 시간 표시. 0초가 되면 다시 시작하도록 안내 */
function startCountdown(seconds) {
  clearInterval(state.timer);
  let left = seconds;
  $('import-countdown').textContent = left;
  state.timer = setInterval(() => {
    left -= 1;
    $('import-countdown').textContent = Math.max(left, 0);
    if (left <= 0) {
      clearInterval(state.timer);
      $('import-complete-btn').disabled = true;
      toast('인증 시간이 지났습니다. [처음부터 다시]를 눌러 주세요.', true);
    }
  }, 1000);
}

/** 2단계 : 인증 완료 → 조제 내역 받기 */
async function complete() {
  try {
    const result = await api.post(
      `/api/user/prescriptions/import/${encodeURIComponent(state.importId)}/complete`,
      { identity: state.identity });
    clearInterval(state.timer);
    state.identity = null; // 다 썼으므로 바로 지운다
    state.source = result.source;
    state.prescriptions = result.prescriptions;
    $('import-wait').hidden = true;
    showResult();
  } catch (error) {
    showError(error);
    // 아직 승인 전(PRESCRIPTION_AUTH_NOT_APPROVED)이면 그대로 기다리고, 그 밖의 실패는 처음 화면으로
    if (error.code !== 'PRESCRIPTION_AUTH_NOT_APPROVED') {
      resetToForm();
    }
  }
}

/** 3단계 화면 : 처방마다 체크박스(기본 선택) + 약 목록 */
function showResult() {
  $('import-source').replaceChildren(...[sourceBadge(state.source)].filter(Boolean));
  const list = $('import-list');
  list.replaceChildren(...state.prescriptions.map((rx, index) => el('li', { class: 'rx' },
    el('div', { class: 'rx-head' },
      el('label', {},
        el('input', { type: 'checkbox', checked: true, 'data-index': index }), ' ',
        el('b', {}, formatDate(rx.dispensed_date)), ' ',
        [rx.prescriber, rx.pharmacy].filter(Boolean).join(' · '))),
    el('ul', { class: 'rx-drugs' }, rx.drugs.map((drug) => renderDrug({
      code: drug.drug_code, name: drug.name, ingredients: drug.ingredients,
      oneDose: drug.one_dose, dailyDoses: drug.daily_doses, days: drug.total_dosing_days, effect: drug.effect,
    }))))));
  if (state.prescriptions.length === 0) {
    list.append(el('li', { class: 'empty-text' }, '최근 1년 조제 내역이 없습니다.'));
  }
  $('import-result').hidden = false;
  $('import-form').hidden = false; // 다시 불러올 수 있게 폼도 보여 준다
}

/** 체크한 처방만 pill-api 에 저장 */
async function saveSelected() {
  const checked = [...document.querySelectorAll('#import-list input[type=checkbox]:checked')]
    .map((box) => state.prescriptions[Number(box.dataset.index)]);
  if (checked.length === 0) {
    toast('저장할 처방을 선택해 주세요.', true);
    return;
  }
  try {
    const result = await api.post('/api/pill/prescriptions', { source: state.source, prescriptions: checked });
    toast(`저장 ${result.saved_count}건` + (result.skipped_count ? `, 이미 저장돼 있어 건너뜀 ${result.skipped_count}건` : ''));
  } catch (error) {
    showError(error);
  }
}

/** 처음 화면(1단계 폼)으로. 같은 브라우저에서 다시 요청하면 서버가 이전 요청을 버리고 새로 시작한다 */
function resetToForm() {
  clearInterval(state.timer);
  state.importId = null;
  state.identity = null;
  $('import-wait').hidden = true;
  $('import-form').hidden = false;
}

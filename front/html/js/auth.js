/* =====================================================================
   auth.js : 회원가입 · 로그인 · 로그아웃

   - 로그인은 user-api(Spring Boot)만 한다. 성공하면 서버가 Redis 에 세션을 만들고
     SESSION 쿠키(HttpOnly)를 내려 준다. 이 쿠키는 자바스크립트로 읽을 수 없고, 브라우저가 알아서 보낸다.
   - 화면에 필요한 로그인 사용자 정보(닉네임 등)는 로그인 응답과 /api/user/me 로 받는다.
   - 회원가입은 로그인 화면 아래 [회원가입하기] 버튼으로 여는 모달 창(<dialog>)에서 한다.
   ===================================================================== */
import { api } from './api.js';
import { $, formValues, showError, toast, withBusy } from './ui.js';

/** 현재 로그인한 사용자 {user_id, username, nickname} (로그아웃 상태면 null) */
export const session = { user: null };

/**
 * 페이지를 열었을 때 이미 로그인돼 있는지 확인한다 (새로고침해도 세션 쿠키가 남아 있으면 로그인 유지)
 * @returns {Promise<boolean>}
 */
export async function restoreSession() {
  try {
    const me = await api.get('/api/user/me');
    session.user = me.login_user;
    return true;
  } catch (error) {
    session.user = null;
    return false;
  }
}

/**
 * 로그인/회원가입 화면의 이벤트 연결
 * @param {function} onLoggedIn 로그인 성공 후 부를 함수 (main.js 가 화면 전환)
 */
export function initAuth(onLoggedIn) {
  const dialog = $('signup-dialog');

  // [회원가입하기] → 모달 열기 (showModal : 뒤 화면을 막고 Esc 로 닫을 수 있다)
  $('signup-open').addEventListener('click', () => {
    dialog.showModal();
    $('signup-form').elements.email.focus();
  });
  // [✕] 또는 모달 바깥(어두운 배경)을 누르면 닫기
  $('signup-close').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });
  // 어떤 방법으로 닫히든 입력한 비밀번호 등이 남지 않도록 폼과 오류 메시지를 비운다
  dialog.addEventListener('close', () => {
    $('signup-form').reset();
    $('signup-error').hidden = true;
  });

  // 로그인 : POST /api/user/login
  $('login-form').addEventListener('submit', async (event) => {
    event.preventDefault(); // 폼 기본 동작(페이지 새로고침) 막기
    const form = event.currentTarget;
    await withBusy(form.querySelector('button'), async () => {
      try {
        session.user = await api.post('/api/user/login', formValues(form));
        form.reset();
        onLoggedIn();
      } catch (error) {
        showError(error);
      }
    });
  });

  // 회원가입 : POST /api/user/signup → 성공하면 모달을 닫고, 로그인 폼에 이메일을 채워 비밀번호 칸으로 이동
  $('signup-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    await withBusy(form.querySelector('button'), async () => {
      try {
        const values = formValues(form);
        await api.post('/api/user/signup', values);
        dialog.close();
        toast('가입했습니다. 로그인해 주세요.');
        $('login-form').elements.email.value = values.email;
        $('login-form').elements.password.focus();
      } catch (error) {
        // 실패 메시지는 모달 안에 (예: "이미 가입된 이메일입니다.")
        $('signup-error').textContent = error.message;
        $('signup-error').hidden = false;
      }
    });
  });
}

/* ---------- 세션 상태 감시 ----------
   다른 기기에서 같은 계정으로 로그인하면 서버는 이 브라우저의 세션을 즉시 지우지만,
   브라우저는 다음 요청을 보낼 때까지 모른다. 그래서 로그인 화면이 열려 있는 동안
   5초마다(그리고 탭으로 돌아올 때) GET /api/user/session/status 로 확인한다.
   이 API 는 세션을 열지 않아서 30분 만료 시간을 연장하지 않는다. */
const WATCH_INTERVAL_MS = 5000;
let watchTimer = null;
let onSessionEnded = () => {};

/** 탭이 다시 보이면 바로 한 번 확인 */
function checkWhenVisible() {
  if (document.visibilityState === 'visible') checkSession();
}

/**
 * 감시 시작 (로그인 후 화면을 보여 줄 때)
 * @param {function} handler 세션이 끝났을 때 부를 함수 (안내 메시지를 넘겨준다)
 */
export function startSessionWatch(handler) {
  onSessionEnded = handler;
  stopSessionWatch();
  watchTimer = setInterval(checkSession, WATCH_INTERVAL_MS);
  document.addEventListener('visibilitychange', checkWhenVisible);
}

/** 감시 멈춤 (로그아웃 화면으로 갈 때) */
export function stopSessionWatch() {
  clearInterval(watchTimer);
  watchTimer = null;
  document.removeEventListener('visibilitychange', checkWhenVisible);
}

async function checkSession() {
  let status;
  try {
    status = (await api.get('/api/user/session/status')).status;
  } catch (error) {
    return; // 네트워크 오류 등은 다음 확인에서 다시 본다
  }
  if (status === 'ACTIVE') return;
  stopSessionWatch();
  session.user = null;
  onSessionEnded(status === 'REPLACED'
    ? '다른 기기에서 로그인되어 로그아웃되었습니다.'
    : '로그인이 만료되었습니다. 다시 로그인해 주세요.');
}

/** 로그아웃 : POST /api/user/logout → Redis 의 세션이 지워져 모든 서버에서 동시에 로그아웃된다 */
export async function logout() {
  try {
    await api.post('/api/user/logout');
  } catch (error) {
    // 이미 세션이 끝났어도 화면은 로그아웃 상태로 바꾼다
  }
  session.user = null;
}

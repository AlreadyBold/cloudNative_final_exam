/* =====================================================================
   session.js : 세션 공유 확인 화면 (과제 검증용)

   [세션 공유 확인] 버튼을 누르면
     - GET /api/user/me 를 4번 → 프록시가 user-api-1, user-api-2 에 번갈아 보낸다 (Spring Boot)
     - GET /api/pill/me 를 1번 → pill-api (FastAPI)
   각 응답의 served_by(처리한 컨테이너), session_id_prefix(세션 ID 앞 8자리), 로그인 사용자를 표로 보여 준다.

   "처리한 서버는 여러 개인데 세션 ID·사용자는 모두 같다" = 서버 분산 + 세션 공유가 동시에 된다는 증명.
   ===================================================================== */
import { request, requestLog, setLogListener } from './api.js';
import { $, el, maskEmail, showError, withBusy } from './ui.js';

/** 확인할 요청 목록 (순서대로 보낸다) */
const CHECKS = [
  '/api/user/me', '/api/user/me', '/api/user/me', '/api/user/me',
  '/api/pill/me',
];

export function initSession() {
  $('session-check-btn').addEventListener('click', (event) => withBusy(event.currentTarget, runCheck));
  // API 요청이 생길 때마다 아래 "최근 요청 기록"을 다시 그린다
  setLogListener(renderLog);
}

/** 세션 공유 확인 실행 */
async function runCheck() {
  const tbody = $('session-table').querySelector('tbody');
  tbody.replaceChildren();
  $('session-table').hidden = false;
  const results = [];

  try {
    for (const [index, path] of CHECKS.entries()) {
      // 동시에 보내지 않고 하나씩 보내서 프록시의 번갈아 보내기(라운드 로빈)가 잘 보이게 한다
      const { data } = await request('GET', path);
      results.push(data);
      const user = data.login_user;
      tbody.append(el('tr', {},
        el('td', {}, index + 1),
        el('td', {}, path),
        el('td', {}, el('b', {}, data.served_by)),
        el('td', {}, data.session_id_prefix),
        // 이메일은 화면 녹화·캡처에 남지 않도록 가린다
        el('td', {}, `${user.nickname} (${maskEmail(user.username)}, #${user.user_id})`)));
    }
  } catch (error) {
    showError(error);
    return;
  }

  // 판정 : 세션 ID 와 회원 번호가 모두 같고, 처리한 서버는 2개 이상이어야 한다
  const sessions = new Set(results.map((r) => r.session_id_prefix));
  const users = new Set(results.map((r) => r.login_user.user_id));
  const servers = new Set(results.map((r) => r.served_by));
  const ok = sessions.size === 1 && users.size === 1 && servers.size >= 2;
  const verdict = $('session-verdict');
  verdict.className = `verdict ${ok ? 'ok' : 'fail'}`;
  verdict.textContent = ok
    ? `✅ 서로 다른 서버 ${servers.size}대(${[...servers].join(', ')})가 같은 세션(${[...sessions][0]})의 같은 사용자를 확인했습니다.`
    : `⚠️ 세션 ${sessions.size}개, 사용자 ${users.size}명, 서버 ${servers.size}대 — 세션 공유 또는 분산이 되지 않았습니다.`;
}

/** 최근 API 요청 기록 그리기 (시각, 메서드, 주소, 상태코드, 처리한 서버) */
function renderLog() {
  $('request-log').replaceChildren(...requestLog.map((log) =>
    el('li', {}, `${log.time}  ${log.method.padEnd(6)} ${log.path}  →  ${log.status}  [${log.servedBy}]`)));
}

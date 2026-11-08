/* =====================================================================
   main.js : 화면의 시작점
   - 각 기능 모듈의 이벤트를 연결하고
   - 로그인 여부에 따라 "로그인 화면" / "앱 화면"을 바꾸고
   - 탭을 누르면 해당 화면을 보여 주며 필요한 데이터를 새로 불러온다
   ===================================================================== */
import { setUnauthorizedHandler } from './api.js';
import { initAuth, logout, restoreSession, session } from './auth.js';
import { initImporter } from './importer.js';
import { initMyPage, loadMyPage } from './mypage.js';
import { loadPillbox } from './pillbox.js';
import { loadSaved } from './saved.js';
import { initSearch } from './search.js';
import { initSession } from './session.js';
import { $, toast } from './ui.js';

/** 탭을 열 때 부를 데이터 불러오기 함수 (목록이 항상 최신이 되도록 열 때마다 새로 부른다) */
const TAB_LOADERS = {
  pillbox: loadPillbox,
  saved: loadSaved,
  mypage: loadMyPage,
};

/** 로그인 후 화면 보여 주기 */
function showApp() {
  $('auth-view').hidden = true;
  $('app-view').hidden = false;
  $('who').hidden = false;
  renderWho();
}

/** 로그인 화면 보여 주기 */
function showAuth() {
  $('app-view').hidden = true;
  $('who').hidden = true;
  $('auth-view').hidden = false;
}

/** 상단 바의 "○○님" 표시 */
function renderWho() {
  $('who-name').textContent = session.user ? `${session.user.nickname} 님` : '';
}

/** 탭 전환 */
function openTab(name) {
  document.querySelectorAll('#tabs .tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.tab === name));
  document.querySelectorAll('.tab-panel').forEach((panel) => { panel.hidden = panel.id !== `tab-${name}`; });
  TAB_LOADERS[name]?.();
}

/** 페이지가 열리면 한 번 실행 */
async function start() {
  // 각 화면의 버튼·폼 이벤트 연결
  initAuth(() => { showApp(); openTab('search'); });
  initSearch();
  initImporter();
  initMyPage(renderWho, showAuth);
  initSession();

  document.querySelectorAll('#tabs .tab').forEach((tab) => tab.addEventListener('click', () => openTab(tab.dataset.tab)));

  $('logout-btn').addEventListener('click', async () => {
    await logout();
    showAuth();
    toast('로그아웃했습니다.');
  });

  // 어느 API 든 401(로그인 필요)이 오면 세션이 끝난 것이므로 로그인 화면으로
  // (다른 기기에서 같은 계정으로 로그인하면 "다른 기기에서 로그인되어 로그아웃되었습니다." 가 온다)
  setUnauthorizedHandler((message) => {
    if (!$('app-view').hidden) {
      session.user = null;
      showAuth();
      toast(message || '로그인이 필요합니다.', true);
    }
  });

  // 새로고침해도 SESSION 쿠키가 살아 있으면 로그인 상태로 시작
  if (await restoreSession()) {
    showApp();
    openTab('search');
  } else {
    showAuth();
  }
}

start();

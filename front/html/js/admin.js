/* =====================================================================
   admin.js : 관리자 백오피스 (user-api, ADMIN 권한 필요)
     GET  /api/user/admin/dashboard                    : 컨테이너 상태 + 회원·세션 통계
     GET  /api/user/admin/members                      : 회원 목록
     POST /api/user/admin/members/{id}/reset-password  : 비밀번호를 test1234 로 초기화 (+ 그 회원 로그아웃)
   [세션 확인] 탭은 session.js 가 담당한다 (서버 분산 + 세션 공유 검증, 과제 시연용).

   - 일반 사용자가 이 화면의 API 를 직접 불러도 서버가 403 으로 막는다 (화면 숨기기는 보조 수단일 뿐).
   - 관리자도 사용자의 약 정보(내 약통)는 볼 수 없다. 약 정보는 pill-api 에만 있고 여기서는 부르지 않는다.
   ===================================================================== */
import { api } from './api.js';
import { $, el, formatDate, showError, toast, withBusy } from './ui.js';

/** 대시보드 자동 새로고침 간격 (밀리초) */
const REFRESH_MS = 10000;
let refreshTimer = null;

export function initAdmin() {
  document.querySelectorAll('#admin-tabs .tab').forEach((tab) =>
    tab.addEventListener('click', () => openAdminTab(tab.dataset.adminTab)));
}

/** 관리자 탭 전환. 대시보드를 보는 동안만 자동 새로고침한다 */
export function openAdminTab(name) {
  document.querySelectorAll('#admin-tabs .tab').forEach((t) => t.classList.toggle('active', t.dataset.adminTab === name));
  document.querySelectorAll('.admin-panel').forEach((panel) => { panel.hidden = panel.id !== `admin-${name}`; });
  stopAdmin();
  if (name === 'dashboard') {
    loadDashboard();
    refreshTimer = setInterval(loadDashboard, REFRESH_MS);
  } else if (name === 'members') {
    loadMembers();
  }
}

/** 관리자 화면을 떠날 때(로그아웃 등) 자동 새로고침 멈추기 */
export function stopAdmin() {
  clearInterval(refreshTimer);
  refreshTimer = null;
}

/* ---------- 대시보드 ---------- */

async function loadDashboard() {
  try {
    const d = await api.get('/api/user/admin/dashboard');
    const s = d.stats;
    const up = d.services.filter((x) => x.up).length;
    $('dashboard-stats').replaceChildren(
      statTile(`${up} / ${d.services.length}`, '정상 컨테이너'),
      statTile(s.total_members, '전체 회원'),
      statTile(s.online_members, '접속 중인 회원'),
      statTile(s.new_members_today, '오늘 가입'),
      statTile(s.active_sessions, 'Redis 세션'));
    $('dashboard-services').replaceChildren(...d.services.map(serviceCard));
    $('dashboard-updated').textContent = `${formatDate(d.checked_at)} 기준 · ${d.served_by} 가 확인 · 10초마다 갱신`;
  } catch (error) {
    showError(error);
  }
}

/** 숫자 타일 */
function statTile(value, label) {
  return el('div', { class: 'stat' }, el('div', { class: 'stat-value' }, value), el('div', { class: 'stat-label' }, label));
}

/** 컨테이너 1개 카드 : 초록(정상) / 빨강(응답 없음) + 응답 시간 */
function serviceCard(service) {
  return el('div', { class: `service ${service.up ? 'up' : 'down'}` },
    el('div', { class: 'service-name' }, el('span', { class: `dot ${service.up ? 'on' : 'down'}` }), service.name),
    el('div', { class: 'service-meta' }, service.role),
    el('div', { class: 'service-meta' },
      service.up ? `정상 · ${service.response_ms}ms · ${service.detail}` : `응답 없음 · ${service.detail || ''}`));
}

/* ---------- 회원 관리 ---------- */

async function loadMembers() {
  try {
    const members = await api.get('/api/user/admin/members');
    $('members-body').replaceChildren(...members.map(memberRow));
  } catch (error) {
    showError(error);
  }
}

function memberRow(m) {
  const isAdmin = m.role === 'ADMIN';
  return el('tr', {},
    el('td', {}, m.id),
    el('td', {}, m.email),
    el('td', {}, m.nickname),
    el('td', {}, isAdmin ? el('span', { class: 'badge admin-badge' }, '관리자') : '사용자'),
    el('td', {}, formatDate(m.created_at)),
    el('td', {}, el('span', { class: `dot ${m.online ? 'on' : 'off'}` }), m.online ? '접속 중' : '-'),
    el('td', {}, isAdmin ? null : el('button', {
      type: 'button', class: 'btn small',
      onclick: (event) => withBusy(event.currentTarget, () => resetPassword(m)),
    }, '비밀번호 초기화')));
}

async function resetPassword(member) {
  if (!confirm(`${member.email} 의 비밀번호를 test1234 로 초기화할까요? (로그인 중이면 로그아웃됩니다)`)) return;
  try {
    await api.post(`/api/user/admin/members/${member.id}/reset-password`);
    toast('비밀번호를 test1234 로 초기화했습니다.');
    await loadMembers();
  } catch (error) {
    showError(error);
  }
}

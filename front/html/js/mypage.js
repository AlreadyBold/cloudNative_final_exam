/* =====================================================================
   mypage.js : 마이페이지 (user-api, 로그인 필요)
     GET    /api/user/me           : 내 정보
     PATCH  /api/user/me           : 닉네임 변경 (세션의 닉네임도 바뀌어 pill-api 에도 바로 반영)
     PATCH  /api/user/me/password  : 비밀번호 변경
     DELETE /api/user/me           : 회원 탈퇴 (+ 먼저 pill-api 의 내 데이터 삭제)
   ===================================================================== */
import { api } from './api.js';
import { session } from './auth.js';
import { $, el, formValues, formatDate, showError, toast, withBusy } from './ui.js';

/**
 * @param {function} onNicknameChanged 닉네임이 바뀌면 상단 바를 고치기 위해 부를 함수
 * @param {function} onWithdrawn       탈퇴 후 로그인 화면으로 보내기 위해 부를 함수
 */
export function initMyPage(onNicknameChanged, onWithdrawn) {
  // 닉네임 변경
  $('nickname-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    await withBusy(form.querySelector('button'), async () => {
      try {
        const member = await api.patch('/api/user/me', formValues(form));
        session.user.nickname = member.nickname;
        form.reset();
        toast('닉네임을 바꿨습니다.');
        onNicknameChanged();
        await loadMyPage();
      } catch (error) {
        showError(error);
      }
    });
  });

  // 비밀번호 변경
  $('password-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    await withBusy(form.querySelector('button'), async () => {
      try {
        await api.patch('/api/user/me/password', formValues(form));
        form.reset();
        toast('비밀번호를 바꿨습니다.');
      } catch (error) {
        showError(error);
      }
    });
  });

  // 회원 탈퇴
  $('withdraw-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!confirm('정말 탈퇴할까요? 모든 데이터가 삭제되고 되돌릴 수 없습니다.')) return;
    await withBusy(form.querySelector('button'), async () => {
      try {
        await withdraw(formValues(form).password);
        form.reset();
        toast('탈퇴했습니다.');
        onWithdrawn();
      } catch (error) {
        showError(error);
      }
    });
  });
}

/**
 * 회원 탈퇴 순서
 *  1) 비밀번호 확인 : 같은 이메일·비밀번호로 로그인 API 를 불러 본다 (틀리면 여기서 멈춰서 데이터가 지워지지 않는다)
 *  2) pill-api 의 내 약통·저장한 처방 삭제 (회원 정보와 다른 서버·DB 에 있으므로 따로 지운다)
 *  3) user-api 의 회원 삭제 + 세션 무효화 (세션이 사라지면 pill-api 를 부를 수 없으므로 마지막에)
 */
async function withdraw(password) {
  await api.post('/api/user/login', { email: session.user.username, password });
  await api.delete('/api/pill/me/data');
  await api.delete('/api/user/me', { password });
}

/** 마이페이지 탭을 열 때마다 내 정보를 새로 불러온다 */
export async function loadMyPage() {
  try {
    const me = await api.get('/api/user/me');
    const rows = [
      ['회원 번호', me.member.id],
      ['이메일', me.member.email],
      ['닉네임', me.member.nickname],
      ['가입일', formatDate(me.member.created_at)],
    ];
    $('my-info').replaceChildren(...rows.flatMap(([label, value]) => [el('dt', {}, label), el('dd', {}, value)]));
  } catch (error) {
    showError(error);
  }
}

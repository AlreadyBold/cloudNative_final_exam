/* =====================================================================
   api.js : 서버 API 호출을 한곳에서 처리하는 모듈

   - 모든 요청은 같은 주소(프록시)의 /api/user/** 또는 /api/pill/** 로 보낸다.
     같은 출처라서 브라우저가 SESSION 쿠키를 자동으로 함께 보낸다. (세션 공유의 출발점)
   - 두 서버의 응답 형식이 같다 : {success, data, error: {code, message}}
     성공이면 data 를, 실패면 ApiError 를 던진다.
   - 응답 헤더 X-Served-By(처리한 컨테이너 이름)를 기록해서 "세션 확인" 화면에 보여 준다.
   ===================================================================== */

/** 서버가 실패 응답을 보냈을 때 던지는 에러. code 로 상황별 처리를 나눈다 */
export class ApiError extends Error {
  /**
   * @param {number} status HTTP 상태코드 (401, 409 ...)
   * @param {string} code   서버 에러 코드 (COMMON_UNAUTHORIZED ...)
   * @param {string} message 사용자에게 보여 줄 메시지
   */
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/** 최근 요청 기록 (세션 확인 화면용). 오래된 것부터 지워서 최대 30개만 둔다 */
export const requestLog = [];
const LOG_LIMIT = 30;

/** 요청 기록이 바뀌면 부를 함수 (session.js 가 등록) */
let onLogChange = () => {};
export function setLogListener(listener) {
  onLogChange = listener;
}

/** 로그인이 풀렸을 때(401) 부를 함수 (main.js 가 등록 → 로그인 화면으로 이동) */
let onUnauthorized = () => {};
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

/**
 * API 요청 공통 함수
 * @param {string} method GET, POST, PATCH, DELETE
 * @param {string} path   /api/user/login 처럼 / 로 시작하는 주소
 * @param {object} [body] 보낼 JSON (없으면 본문 없음)
 * @returns {Promise<{data: any, servedBy: string}>} 응답 data 와 처리한 서버 이름
 */
export async function request(method, path, body) {
  const options = {
    method,
    // same-origin : 같은 주소로 보내는 요청에 쿠키(SESSION)를 싣는다
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(path, options);
  } catch (networkError) {
    // 서버가 꺼져 있거나 네트워크가 끊긴 경우
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.');
  }

  // 어느 컨테이너가 처리했는지 (user-api-1 / user-api-2 / pill-api)
  const servedBy = response.headers.get('X-Served-By') || '-';
  addLog(method, path, response.status, servedBy);

  // 응답 본문(JSON) 읽기. 프록시 오류(502 등)처럼 JSON 이 아닐 수도 있다
  let json = null;
  try {
    json = await response.json();
  } catch (parseError) {
    json = null;
  }

  if (!response.ok || !json || json.success !== true) {
    const code = json?.error?.code || `HTTP_${response.status}`;
    const message = json?.error?.message || `요청을 처리하지 못했습니다. (${response.status})`;
    // 로그인이 필요한 API 에서 401 이면 세션이 끝난 것 → 로그인 화면으로
    if (response.status === 401 && !path.endsWith('/login')) {
      onUnauthorized();
    }
    throw new ApiError(response.status, code, message);
  }
  return { data: json.data, servedBy };
}

/** 요청 기록에 한 줄 추가 */
function addLog(method, path, status, servedBy) {
  const time = new Date().toLocaleTimeString('ko-KR', { hour12: false });
  requestLog.unshift({ time, method, path, status, servedBy });
  if (requestLog.length > LOG_LIMIT) {
    requestLog.pop();
  }
  onLogChange();
}

// ----- 자주 쓰는 형태를 짧게 쓰기 위한 도우미 (data 만 돌려준다) -----
export const api = {
  get: async (path) => (await request('GET', path)).data,
  post: async (path, body) => (await request('POST', path, body ?? {})).data,
  patch: async (path, body) => (await request('PATCH', path, body ?? {})).data,
  delete: async (path, body) => (await request('DELETE', path, body)).data,
};

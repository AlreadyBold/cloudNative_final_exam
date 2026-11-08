/* =====================================================================
   calendar.js : 내 약통 캘린더 보기 (서버 호출 없음, 약통 목록 데이터로 그린다)

   - 월 단위 달력. 약을 먹는 기간을 약별 색깔 띠로 이어서 그린다 (구글 캘린더처럼)
     · 한 주(일~토) 안에서는 시작일~종료일이 하나의 띠, 주가 바뀌면 다음 줄에서 이어진다
     · 실제로 시작·끝나는 날만 모서리를 둥글게, 약 이름은 그 주 띠의 첫 칸에만 쓴다
     · 한 주에 보여 줄 줄(약) 수를 넘치면 날짜 칸에 +N
   - 병용금기 약을 함께 먹게 되는 날짜에는 ⚠ 표시
   - 날짜를 누르면 아래에 그날 먹는 약 목록과 복용법을 보여 준다
   - 좁은 화면(휴대폰)에서는 약 이름 대신 색깔 점만 보인다 (style.css)

   날짜는 모두 "YYYY-MM-DD" 문자열로 다룬다. 이 형식은 글자 순서 = 날짜 순서라서 문자열 비교(<, >)가 곧 날짜 비교다.
   ===================================================================== */
import { addDays, el, todayString } from './ui.js';

/** 약별 색깔 (약통 목록 순서대로 서로 다른 색. 같은 약은 어느 달에서나 같은 색) */
const COLORS = ['#2f6fde', '#1f9d55', '#d97706', '#9333ea', '#db2777', '#0891b2', '#65a30d', '#b45309'];
/** 한 주에 보여 줄 띠의 줄 수 (넘치는 약은 날짜 칸에 +N) */
const MAX_LANES = 4;
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** 지금 보고 있는 달(그달 1일)과 고른 날짜 */
const view = { month: null, selected: null };
/** 약통 항목 id → 색깔 (그릴 때마다 목록 순서로 다시 정한다) */
let colorById = new Map();

/**
 * 캘린더 그리기
 * @param {HTMLElement} box    그릴 곳
 * @param {Array} items        약통 항목 (start_date, end_date, drug_name, status ...)
 * @param {Array} pairs        병용금기 쌍 (overlap_start, overlap_end)
 * @param {function} describe  약 1개 → 복용법 글자 ("하루 3번 · 1번에 1개씩"), pillbox.js 가 넘긴다
 */
export function renderCalendar(box, items, pairs, describe) {
  view.month = view.month || `${todayString().slice(0, 7)}-01`;
  view.selected = view.selected || todayString();
  colorById = new Map([...items].sort((a, b) => a.id - b.id).map((item, i) => [item.id, COLORS[i % COLORS.length]]));

  const header = el('div', { class: 'cal-head' },
    el('button', { type: 'button', class: 'btn small', onclick: () => move(-1) }, '◀'),
    el('b', {}, `${view.month.slice(0, 4)}년 ${Number(view.month.slice(5, 7))}월`),
    el('button', { type: 'button', class: 'btn small', onclick: () => move(1) }, '▶'),
    el('button', {
      type: 'button', class: 'btn small ghost',
      onclick: () => { view.month = `${todayString().slice(0, 7)}-01`; view.selected = todayString(); redraw(); },
    }, '오늘'));

  const grid = el('div', { class: 'cal-grid' },
    el('div', { class: 'cal-weekdays' }, WEEKDAYS.map((day) => el('div', { class: 'cal-weekday' }, day))));
  const cells = monthCells(view.month);
  for (let i = 0; i < cells.length; i += 7) {
    grid.append(weekRow(cells.slice(i, i + 7), items, pairs));
  }

  box.replaceChildren(header, grid, dayDetail(view.selected, items, pairs, describe));

  // 달을 넘기거나 날짜를 고르면 같은 데이터로 다시 그린다
  function move(step) {
    const [y, m] = view.month.split('-').map(Number);
    const next = new Date(y, m - 1 + step, 1);
    view.month = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-01`;
    redraw();
  }
  function redraw() {
    renderCalendar(box, items, pairs, describe);
  }
  // 날짜 칸을 누르면 그 날짜를 고른다
  grid.addEventListener('click', (event) => {
    const cell = event.target.closest('[data-date]');
    if (!cell) return;
    view.selected = cell.dataset.date;
    redraw();
  });
}

/** 달력에 그릴 날짜들 : 그달 1일이 있는 주의 일요일부터 말일이 있는 주의 토요일까지 */
function monthCells(firstDay) {
  const [y, m] = firstDay.split('-').map(Number);
  const startWeekday = new Date(y, m - 1, 1).getDay();
  const lastDate = new Date(y, m, 0).getDate();
  const total = Math.ceil((startWeekday + lastDate) / 7) * 7;
  const start = addDays(firstDay, -startWeekday);
  return Array.from({ length: total }, (_, i) => addDays(start, i));
}

/** 그날 먹는 약 (시작일 ≤ 날짜 ≤ 종료일) */
function itemsOn(date, items) {
  return items.filter((item) => item.start_date <= date && date <= item.end_date);
}

/** 그날 병용금기 약을 함께 먹는지 */
function hasConflict(date, pairs) {
  return pairs.some((p) => p.overlap_start <= date && date <= p.overlap_end);
}

function colorOf(item) {
  return colorById.get(item.id) || COLORS[0];
}

/**
 * 한 주(7일) 줄 : 날짜 칸 7개 + 그 주에 걸친 약 띠들
 * CSS grid 로 7칸을 나누고, 띠는 grid-column 으로 "시작 칸 ~ 끝 칸"을 한 번에 차지해서 이어져 보인다.
 * 날짜 칸은 모든 줄을 차지하는 배경(클릭 영역)이고, 띠는 그 위에 겹쳐 그린다.
 */
function weekRow(dates, items, pairs) {
  const weekStart = dates[0];
  const weekEnd = dates[6];
  // 이 주에 걸친 약 : 먼저 시작한 약, 긴 약 순서로 줄(lane)을 배정한다
  const inWeek = items
    .filter((item) => item.start_date <= weekEnd && item.end_date >= weekStart)
    .sort((a, b) => a.start_date.localeCompare(b.start_date) || b.end_date.localeCompare(a.end_date));
  const laneEnds = []; // 줄마다 마지막으로 차지한 날짜
  const bars = [];
  const hiddenCount = new Array(7).fill(0);
  for (const item of inWeek) {
    const from = Math.max(0, dates.indexOf(item.start_date < weekStart ? weekStart : item.start_date));
    const to = item.end_date > weekEnd ? 6 : dates.indexOf(item.end_date);
    let lane = laneEnds.findIndex((end) => end < from);
    if (lane === -1) lane = laneEnds.length;
    if (lane >= MAX_LANES) {
      for (let d = from; d <= to; d += 1) hiddenCount[d] += 1; // 줄이 모자라면 +N 으로
      continue;
    }
    laneEnds[lane] = to;
    const classes = ['cal-bar'];
    if (item.start_date >= weekStart) classes.push('starts'); // 실제 시작일이면 왼쪽 모서리 둥글게
    if (item.end_date <= weekEnd) classes.push('ends'); // 실제 종료일이면 오른쪽 모서리 둥글게
    bars.push(el('div', {
      class: classes.join(' '),
      style: `--bar:${colorOf(item)}; grid-column:${from + 1} / ${to + 2}; grid-row:${lane + 2};`,
      title: `${item.drug_name} (${item.start_date} ~ ${item.end_date})`,
    }, shortName(item.drug_name)));
  }
  const lanes = Math.max(1, Math.min(laneEnds.length, MAX_LANES));
  const more = hiddenCount.map((count, d) => (count > 0
    ? el('div', { class: 'cal-more', style: `grid-column:${d + 1}; grid-row:${lanes + 2};` }, `+${count}`)
    : null));
  return el('div', { class: 'cal-week', style: `--lanes:${lanes}` },
    dates.map((date, d) => dayCell(date, d, pairs)), bars, more);
}

/** 날짜 칸 하나 (배경 + 날짜 숫자 + 병용금기 ⚠). d 는 그 주에서 몇 번째 칸인지 (0 = 일요일) */
function dayCell(date, d, pairs) {
  const classes = ['cal-day'];
  if (date.slice(0, 7) !== view.month.slice(0, 7)) classes.push('other-month');
  if (date === todayString()) classes.push('today');
  if (date === view.selected) classes.push('selected');

  return el('div', {
    class: classes.join(' '), 'data-date': date, role: 'button', tabindex: '0',
    style: `grid-column:${d + 1}; grid-row:1 / -1;`,
  }, el('div', { class: 'cal-date' }, Number(date.slice(8)),
    hasConflict(date, pairs) ? el('span', { class: 'cal-warn' }, ' ⚠') : null));
}

/** "리피토정10밀리그램(아토르바스타틴...)" → "리피토정10밀리그램" (괄호 앞까지) */
function shortName(name) {
  return String(name).split('(')[0];
}

/** 고른 날짜에 먹는 약 목록 */
function dayDetail(date, items, pairs, describe) {
  const taking = itemsOn(date, items);
  const [, m, d] = date.split('-').map(Number);
  return el('div', { class: 'cal-detail' },
    el('h3', {}, `${m}월 ${d}일에 먹는 약 ${taking.length}개`),
    hasConflict(date, pairs)
      ? el('p', { class: 'cal-warn' }, '⚠ 이날 함께 먹으면 안 되는 약이 있습니다. 위의 병용금기 안내를 확인하세요.') : null,
    taking.length === 0 ? el('p', { class: 'hint' }, '이날 먹는 약이 없습니다.') : el('ul', { class: 'cal-list' },
      taking.map((item) => el('li', {},
        el('span', { class: 'cal-dot', style: `--bar:${colorOf(item)}` }),
        el('b', {}, shortName(item.drug_name)),
        el('span', { class: 'hint' }, ` ${describe(item) || ''} (${item.start_date.slice(5)} ~ ${item.end_date.slice(5)})`)))));
}

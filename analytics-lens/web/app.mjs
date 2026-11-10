import { STAGES, filterRecords, summarize, csvForRecords, formatPercent } from './core.mjs';

const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const PAGE_SIZE = 8;
let all = [];
let current = [];
let page = 0;
let returnFocus = null;
const state = { department: '', channel: '', cohort: '', search: '' };

async function getRecords() {
  const response = await fetch('./data/synthetic_applications.json');
  if (!response.ok) throw new Error('Failed to load synthetic dataset');
  return await response.json();
}

function fillOptions(id, values) {
  const el = $(id);
  values.forEach(value => {
    const option = document.createElement('option');
    option.value = option.textContent = value;
    el.append(option);
  });
}

function renderStats(report) {
  $('filter-record-count').textContent = report.n.toLocaleString();
  $('metric-applications').textContent = report.n.toLocaleString();
  $('denom').textContent = report.n.toLocaleString();
  const setStage = (index, key) => {
    const row = report.funnel[index];
    $(`metric-${key}`).textContent = formatPercent(row.percent_of_applicants);
    $(`metric-${key}-n`).textContent = row.reached.toLocaleString();
  };
  setStage(1, 'screening');
  setStage(2, 'interview');
  setStage(3, 'offer');
}

function renderFunnel(report) {
  $('funnel').innerHTML = report.funnel.map((row, index) => `
    <div class="funnel-row">
      <div class="funnel-label"><span>${esc(row.stage)}</span><span>${row.reached.toLocaleString()} <span aria-hidden="true">·</span> ${formatPercent(row.percent_of_applicants)}</span></div>
      <div class="funnel-track" role="img" aria-label="${esc(row.stage)} reached by ${row.reached} of ${report.n} applications, ${formatPercent(row.percent_of_applicants)}"><div class="funnel-fill" style="width:${row.percent_of_applicants ?? 0}%"></div></div>
    </div>`).join('');
}

function renderChannels(report) {
  const max = Math.max(...report.channels.map(r => r.applicants), 1);
  $('channel-body').innerHTML = report.channels.map(row => `
    <tr data-channel="${esc(row.channel)}" class="${state.channel === row.channel ? 'selected' : ''}">
      <td><button class="channel-button" type="button" aria-label="Filter to ${esc(row.channel)}">${esc(row.channel)}</button><span class="fraction">Explore this source ↗</span></td>
      <td>${row.applicants.toLocaleString()}</td>
      <td><span class="rate">${formatPercent(row.interview_reach_percent)}</span><span class="fraction">${row.interview_reached}/${row.applicants}</span></td>
      <td><span class="rate">${formatPercent(row.offer_reach_percent)}</span><span class="fraction">${row.offer_reached}/${row.applicants}</span></td>
      <td><div class="bar-track"><div class="bar-fill" style="width:${row.applicants / max * 100}%"></div></div><span class="bar-caption">count within view</span></td>
    </tr>`).join('');
  $('channel-body').querySelectorAll('tr').forEach(tr => {
    const choose = () => {
      state.channel = state.channel === tr.dataset.channel ? '' : tr.dataset.channel;
      $('channel').value = state.channel;
      page = 0;
      render();
    };
    tr.addEventListener('click', choose);

  });
}

function renderRecords() {
  const start = page * PAGE_SIZE;
  const rows = current.slice(start, start + PAGE_SIZE);
  $('records-body').innerHTML = rows.length ? rows.map(row => `
    <tr>
      <td>${esc(row.id)}</td>
      <td><strong>${esc(row.role)}</strong><span class="subtext">${esc(row.department)}</span></td>
      <td>${esc(row.channel)}</td>
      <td><span class="stage-pill">${esc(row.highest_stage_reached)}</span></td>
      <td><span class="status-pill">${esc(row.status)}</span></td>
      <td><button class="inspect-btn" type="button" data-id="${esc(row.id)}">View timeline ↗</button></td>
    </tr>`).join('') : '<tr><td colspan="6">No synthetic records match these filters.</td></tr>';
  $('records-body').querySelectorAll('button[data-id]').forEach(button => {
    button.addEventListener('click', () => openDrawer(button.dataset.id, button));
  });
  $('record-page-count').textContent = current.length ? `Showing ${start + 1}–${Math.min(start + PAGE_SIZE, current.length)} of ${current.length} synthetic records` : 'No matching records';
  $('prev-page').disabled = page === 0;
  $('next-page').disabled = (page + 1) * PAGE_SIZE >= current.length;
}

function openDrawer(id, button) {
  const row = current.find(r => r.id === id);
  if (!row) return;
  returnFocus = button;
  $('detail-title').textContent = row.id;
  $('detail-content').innerHTML = `
    <div class="note">This is an entirely synthetic application. All event dates were generated for demonstration.</div>
    <div class="detail-grid">
      <div><span>ROLE</span><strong>${esc(row.role)}</strong></div><div><span>DEPARTMENT</span><strong>${esc(row.department)}</strong></div>
      <div><span>CHANNEL</span><strong>${esc(row.channel)}</strong></div><div><span>COHORT</span><strong>${esc(row.cohort)}</strong></div>
      <div><span>HIGHEST STAGE</span><strong>${esc(row.highest_stage_reached)}</strong></div><div><span>STATUS</span><strong>${esc(row.status)}</strong></div>
    </div>
    <h3 style="margin-top:28px">Recorded stage events</h3>
    <div class="timeline">${row.events.map(event => `<div class="event"><strong>${esc(event.stage)}</strong><span>${esc(event.date)}</span></div>`).join('')}</div>
    <div class="note">A stage event documents simulated workflow progression. This interface does not infer why a status or transition occurred.</div>`;
  $('drawer-overlay').hidden = false;
  $('detail-drawer').hidden = false;
  document.body.style.overflow = 'hidden';
  $('close-drawer').focus();
}

function closeDrawer() {
  $('drawer-overlay').hidden = true;
  $('detail-drawer').hidden = true;
  document.body.style.overflow = '';
  if (returnFocus?.isConnected) returnFocus.focus();
}

function render() {
  current = filterRecords(all, state);
  const report = summarize(current);
  renderStats(report);
  renderFunnel(report);
  renderChannels(report);
  renderRecords();
}

function attachHandlers() {
  for (const key of ['department', 'channel', 'cohort']) {
    $(key).addEventListener('change', e => { state[key] = e.target.value; page = 0; render(); });
  }
  $('search').addEventListener('input', e => { state.search = e.target.value; page = 0; render(); });
  $('clear-filters').addEventListener('click', () => {
    for (const key in state) { state[key] = ''; $(key).value = ''; }
    page = 0; render();
  });
  $('prev-page').addEventListener('click', () => { if (page > 0) { page--; renderRecords(); } });
  $('next-page').addEventListener('click', () => { if ((page + 1) * PAGE_SIZE < current.length) { page++; renderRecords(); } });
  $('export-btn').addEventListener('click', () => {
    const blob = new Blob([csvForRecords(current)], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `recruitment-lens-synthetic-${current.length}-records.csv`;
    document.body.appendChild(link);
    link.click(); link.remove();
    URL.revokeObjectURL(url);
  });
  $('close-drawer').addEventListener('click', closeDrawer);
  $('drawer-overlay').addEventListener('click', closeDrawer);
  document.addEventListener('keydown', event => { if (event.key === 'Escape') closeDrawer(); });
  document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', () => {
      document.querySelectorAll('.nav-link').forEach(x => x.classList.remove('active'));
      link.classList.add('active');
    });
  });
}

async function init() {
  try {
    all = await getRecords();
    if (!Array.isArray(all) || all.some(r => typeof r.id !== 'string' || !Array.isArray(r.events))) throw new Error('Invalid dataset');
    fillOptions('department', [...new Set(all.map(r => r.department))].sort());
    fillOptions('channel', [...new Set(all.map(r => r.channel))].sort());
    fillOptions('cohort', [...new Set(all.map(r => r.cohort))].sort());
    attachHandlers();
    render();
  } catch (err) {
    console.error(err);
    $('load-error').hidden = false;
  }
}
init();

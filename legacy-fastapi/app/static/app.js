'use strict';

const state = { section: 'overview', pipelineView: 'board', jobs: [], apps: [], metrics: null, audits: [], filterRequest: 0 };
const $ = (id) => document.getElementById(id);
const STAGES = ['applied', 'screening', 'interview', 'offer', 'hired', 'rejected'];
const title = (value) => value.charAt(0).toUpperCase() + value.slice(1);
const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (ch) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[ch]));
const fmtDate = (value) => value ? new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—';

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      ...options
    });
  } catch (error) {
    throw new Error('Cannot reach the local server. Check that python run.py is running.');
  }
  let body;
  try { body = await response.json(); } catch { throw new Error(`Unexpected server response (${response.status})`); }
  if (!response.ok) {
    const detail = body.detail;
    const message = typeof detail === 'string' ? detail :
      Array.isArray(detail) ? detail.map((entry) => entry.msg).join('; ') : `Request failed (${response.status})`;
    throw new Error(message);
  }
  return body;
}

let toastTimeout;
function notify(message, isError = false) {
  const el = $('toast');
  clearTimeout(toastTimeout);
  el.textContent = message;
  el.classList.toggle('error', isError);
  el.setAttribute('role', isError ? 'alert' : 'status');
  el.classList.remove('hidden');
  toastTimeout = setTimeout(() => el.classList.add('hidden'), 5500);
}

function pill(stage) { return `<span class="pill pill-${esc(stage)}">${esc(title(stage))}</span>`; }
function emptyRow(cols, message) { return `<tr><td colspan="${cols}" style="padding:27px;text-align:center;color:#8c9bb1">${esc(message)}</td></tr>`; }

async function refresh() {
  const [jobs, apps, metrics, audits] = await Promise.all([
    request('/api/jobs'), request('/api/applications'), request('/api/metrics'), request('/api/audit')
  ]);
  Object.assign(state, { jobs, apps, metrics, audits });
  renderOverview();
  renderJobOptions();
  renderJobs();
  renderAudits();
  await renderPipeline();
}

function renderOverview() {
  const m = state.metrics;
  const cards = [
    ['Open positions', m.open_jobs, `${m.jobs} total positions`, '▦'],
    ['Applications', m.applications, 'All positions combined', '▤'],
    ['In progress', m.active_applications, 'Applied through offer', '◷'],
    ['Unique candidates', m.candidates, 'Based on unique email records', '◉']
  ];
  $('metric-cards').innerHTML = cards.map(([label, number, foot, icon]) =>
    `<div class="metric"><span class="metric-icon" aria-hidden="true">${icon}</span><div class="metric-label">${esc(label)}</div><div class="metric-value">${number}</div><div class="metric-footer">${esc(foot)}</div></div>`).join('');
  const max = Math.max(1, ...Object.values(m.stage_counts));
  $('stage-chart').innerHTML = STAGES.map((stage) => {
    const count = m.stage_counts[stage];
    return `<div class="chart-row"><span class="chart-label">${esc(title(stage))}</span><div class="track" role="img" aria-label="${esc(title(stage))}: ${count} application${count === 1 ? '' : 's'}"><span class="fill" style="width:${100 * count / max}%"></span></div><span class="chart-count">${count}</span></div>`;
  }).join('');
  const recent = state.apps.slice(0, 5);
  $('recent-body').innerHTML = recent.length ? recent.map((a) =>
    `<tr><td><div class="person-name">${esc(a.full_name)}</div><div class="person-email">${esc(a.email)}</div></td><td>${esc(a.job_title)}</td><td>${pill(a.stage)}</td><td>${fmtDate(a.updated_at)}</td></tr>`).join('') : emptyRow(4, 'No applications yet');
}

function renderJobOptions() {
  const prevFilter = $('job-filter').value;
  $('job-filter').innerHTML = '<option value="">All job openings</option>' + state.jobs.map((job) =>
    `<option value="${job.id}">${esc(job.title)}</option>`).join('');
  $('job-filter').value = prevFilter;
  const prevApplicantJob = $('applicant-job').value;
  const open = state.jobs.filter((job) => job.status === 'open');
  $('applicant-job').innerHTML = '<option value="">Select an open job</option>' + open.map((job) =>
    `<option value="${job.id}">${esc(job.title)}</option>`).join('');
  if (open.some((job) => String(job.id) === prevApplicantJob)) $('applicant-job').value = prevApplicantJob;
}

async function renderPipeline() {
  const requestId = ++state.filterRequest;
  const query = new URLSearchParams();
  const search = $('search').value.trim();
  if (search) query.set('search', search);
  if ($('job-filter').value) query.set('job_id', $('job-filter').value);
  if ($('stage-filter').value) query.set('stage', $('stage-filter').value);
  const apps = await request('/api/applications' + (query.toString() ? '?' + query.toString() : ''));
  if (requestId !== state.filterRequest) return;
  $('pipeline-count').textContent = `Showing ${apps.length} application${apps.length === 1 ? '' : 's'}${apps.length === 500 ? ' (maximum 500 displayed)' : ''}`;
  $('pipeline-body').innerHTML = apps.length ? apps.map((a) => {
    const actions = stageActions(a);
    return `<tr><td><button type="button" class="inline-details" data-details="${a.id}">${esc(a.full_name)}</button><div class="person-email">${esc(a.email)}</div></td><td><div class="person-name">${esc(a.job_title)}</div><div class="person-email">${esc(a.team)}</div></td><td>${pill(a.stage)}</td><td>${fmtDate(a.updated_at)}</td><td>${actions}</td></tr>`;
  }).join('') : emptyRow(5, 'No matching applications');
  renderBoard(apps);
}

function stageActions(a) {
  if (!a.allowed_next.length) return '<span class="terminal">Final stage · No further actions</span>';
  return `<div class="action-cell"><label><span class="sr-only">Next stage for ${esc(a.full_name)} and ${esc(a.job_title)}</span><select data-next="${a.id}">${a.allowed_next.map((s) => `<option value="${esc(s)}">${esc(title(s))}</option>`).join('')}</select></label><button type="button" data-apply="${a.id}" data-version="${a.version}" aria-label="Apply selected stage for ${esc(a.full_name)}">Update</button></div>`;
}

function renderBoard(apps) {
  const grouped = Object.fromEntries(STAGES.map((s) => [s, []]));
  apps.forEach((a) => grouped[a.stage].push(a));
  $('board-columns').innerHTML = STAGES.map((stage) => {
    const cards = grouped[stage];
    return `<section class="board-column" aria-label="${title(stage)} applications"><div class="board-column-head"><div><span class="board-stage-dot stage-${stage}" aria-hidden="true"></span><h3>${title(stage)}</h3></div><span class="board-total" aria-label="${cards.length} applications">${cards.length}</span></div><div class="board-card-list">${cards.length ? cards.map((a) => `<article class="candidate-card"><div class="candidate-card-head"><div class="avatar" aria-hidden="true">${esc(a.full_name.split(/\s+/).slice(0,2).map((part) => part[0]).join('').toUpperCase())}</div><span class="candidate-record">#${a.id}</span></div><button type="button" class="candidate-title" data-details="${a.id}">${esc(a.full_name)}</button><p class="candidate-position">${esc(a.job_title)}</p><p class="candidate-meta">${esc(a.team)} · ${fmtDate(a.updated_at)}</p><div class="candidate-actions">${stageActions(a)}</div></article>`).join('') : '<p class="board-empty">No applications in this stage.</p>'}</div></section>`;
  }).join('');
}

async function showDetails(id) {
  const app = await request(`/api/applications/${id}`);
  const events = app.history;
  const dialog = $('detail-dialog');
  $('detail-dialog-title').textContent = app.full_name;
  $('detail-content').innerHTML = `<div class="detail-identity"><div class="avatar avatar-large" aria-hidden="true">${esc(app.full_name.split(/\s+/).slice(0,2).map((part) => part[0]).join('').toUpperCase())}</div><div><strong>${esc(app.job_title)}</strong><p>${esc(app.team)} · ${esc(app.location)}</p></div></div><div class="detail-fields"><div><span>Current stage</span>${pill(app.stage)}</div><div><span>Application ID</span><strong>#${app.id}</strong></div><div><span>Created</span><strong>${fmtDate(app.created_at)}</strong></div><div><span>Last updated</span><strong>${fmtDate(app.updated_at)}</strong></div></div><h3 class="detail-subheading">Available next steps</h3><p class="detail-next">${app.allowed_next.length ? app.allowed_next.map(title).join(' · ') : 'Final stage — no further transitions'}</p><h3 class="detail-subheading">Record history</h3><ol class="history-list">${events.length ? events.map((e) => `<li><span class="history-dot" aria-hidden="true"></span><div><strong>${esc(title(e.event_type.replaceAll('_', ' ')))}</strong><p>${esc(e.from_stage && e.to_stage ? `${title(e.from_stage)} → ${title(e.to_stage)}` : e.to_stage ? title(e.to_stage) : e.details || 'Created')} · ${fmtDate(e.created_at)}</p></div></li>`).join('') : '<li class="history-none">Seeded demonstration record; no individual transition events were imported.</li>'}</ol><p class="detail-disclaimer">Synthetic example · This record is not an actual applicant.</p>`;
  dialog.showModal();
}

function setPipelineView(view) {
  state.pipelineView = view;
  $('board-view').classList.toggle('hidden', view !== 'board');
  $('list-view').classList.toggle('hidden', view !== 'table');
  document.querySelectorAll('[data-pipeline-view]').forEach((button) => {
    const active = button.dataset.pipelineView === view;
    button.setAttribute('aria-pressed', String(active));
    button.classList.toggle('selected', active);
  });
}

function renderJobs() {
  $('job-cards').innerHTML = state.jobs.length ? state.jobs.map((job) =>
    `<article class="job-card"><div class="job-head"><div><div class="job-title">${esc(job.title)}</div><div class="job-team">${esc(job.team)}</div></div>${pill(job.status)}</div><p class="job-loc">⌖ &nbsp;${esc(job.location)}</p><div class="job-bottom"><span class="count"><strong>${job.application_count}</strong> application${job.application_count === 1 ? '' : 's'}</span><button type="button" data-toggle-job="${job.id}" data-current-status="${esc(job.status)}">${job.status === 'open' ? 'Close to applications' : 'Reopen position'}</button></div></article>`).join('') : '<p>No positions created.</p>';
}

function renderAudits() {
  $('audit-body').innerHTML = state.audits.length ? state.audits.map((e) =>
    `<tr><td>${fmtDate(e.created_at)}</td><td><span class="person-name">${esc(e.event_type.replaceAll('_', ' '))}</span></td><td>${esc(e.entity_type)}${e.entity_id === null ? '' : ` #${e.entity_id}`}</td><td>${esc(e.from_stage && e.to_stage ? `${e.from_stage} → ${e.to_stage}` : (e.to_stage || e.details || '—'))}</td></tr>`).join('') : emptyRow(4, 'No activity yet');
}

function navigate(section) {
  state.section = section;
  document.querySelectorAll('.view').forEach((node) => node.classList.toggle('hidden', node.id !== `view-${section}`));
  document.querySelectorAll('.nav-item').forEach((node) => {
    const active = node.dataset.section === section;
    node.classList.toggle('active', active);
    if (active) node.setAttribute('aria-current', 'page'); else node.removeAttribute('aria-current');
  });
  window.scrollTo({ top: 0, behavior: 'instant' });
}

function closeDialog(node) { if (node.open) node.close(); }

document.addEventListener('click', async (event) => {
  const section = event.target.closest('[data-section]');
  if (section) { navigate(section.dataset.section); if (section.dataset.focus === 'example-action') { setPipelineView('board'); } return; }
  const scrollButton = event.target.closest('[data-board-scroll]');
  if (scrollButton) { $('board-scroll').scrollBy({ left: Number(scrollButton.dataset.boardScroll) * 480, behavior: 'smooth' }); return; }
  const switcher = event.target.closest('[data-pipeline-view]');
  if (switcher) { setPipelineView(switcher.dataset.pipelineView); return; }
  const details = event.target.closest('[data-details]');
  if (details) { try { await showDetails(Number(details.dataset.details)); } catch (error) { notify(error.message, true); } return; }
  const open = event.target.closest('[data-open-dialog]');
  if (open) { $(open.dataset.openDialog).showModal(); return; }
  const close = event.target.closest('[data-close-dialog]');
  if (close) { closeDialog(close.closest('dialog')); return; }
  const action = event.target.closest('[data-apply]');
  if (action) {
    action.disabled = true;
    try {
      const id = Number(action.dataset.apply);
      const stage = action.closest('.action-cell').querySelector(`[data-next="${id}"]`).value;
      await request(`/api/applications/${id}/stage`, { method: 'PATCH', body: JSON.stringify({ stage, expected_version: Number(action.dataset.version) }) });
      await refresh();
      notify(`Application moved to ${title(stage)}.`);
    } catch (error) { notify(error.message, true); await refresh().catch(() => {}); }
    finally { action.disabled = false; }
    return;
  }
  const toggle = event.target.closest('[data-toggle-job]');
  if (toggle) {
    toggle.disabled = true;
    const newStatus = toggle.dataset.currentStatus === 'open' ? 'closed' : 'open';
    try {
      await request(`/api/jobs/${toggle.dataset.toggleJob}/status`, { method: 'PATCH', body: JSON.stringify({ status: newStatus }) });
      await refresh();
      notify(`Position ${newStatus}.`);
    } catch (error) { notify(error.message, true); }
    finally { toggle.disabled = false; }
  }
});

$('applicant-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const submit = form.querySelector('[type=submit]'); submit.disabled = true;
  try {
    const payload = Object.fromEntries(new FormData(form));
    payload.job_id = Number(payload.job_id);
    await request('/api/applications', { method: 'POST', body: JSON.stringify(payload) });
    form.reset(); closeDialog($('applicant-dialog')); await refresh(); navigate('pipeline');
    notify('Application created and saved to the local database.');
  } catch (error) { notify(error.message, true); }
  finally { submit.disabled = false; }
});

$('job-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const submit = form.querySelector('[type=submit]'); submit.disabled = true;
  try {
    await request('/api/jobs', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(form))) });
    form.reset(); closeDialog($('job-dialog')); await refresh(); navigate('jobs');
    notify('Job created and saved to the local database.');
  } catch (error) { notify(error.message, true); }
  finally { submit.disabled = false; }
});

let debounce;
$('search').addEventListener('input', () => {
  clearTimeout(debounce);
  debounce = setTimeout(() => renderPipeline().catch((e) => notify(e.message, true)), 180);
});
$('job-filter').addEventListener('change', () => renderPipeline().catch((e) => notify(e.message, true)));
$('stage-filter').addEventListener('change', () => renderPipeline().catch((e) => notify(e.message, true)));
$('clock').textContent = new Date().toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
setPipelineView('board');
refresh().catch((error) => notify(error.message, true));

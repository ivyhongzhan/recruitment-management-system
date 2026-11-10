/** Browser- and Node-compatible descriptive logic, intentionally dependency-free. */
export const STAGES = ['Applied', 'Screening', 'Interview', 'Offer', 'Hired'];
export const CHANNELS = ['Careers page', 'Referral', 'Campus', 'Community', 'Professional network'];

// Exact integer half-up rounding to a tenth of a percentage point.
const percent = (numerator, denominator) => denominator === 0 ? null : Math.floor((2 * numerator * 1000 + denominator) / (2 * denominator)) / 10;

export function filterRecords(records, { department = '', channel = '', cohort = '', search = '' } = {}) {
  const query = search.trim().toLowerCase();
  return records.filter(r =>
    (!department || r.department === department) &&
    (!channel || r.channel === channel) &&
    (!cohort || r.cohort === cohort) &&
    (!query || [r.id, r.role, r.department, r.channel].join(' ').toLowerCase().includes(query))
  );
}

export function summarize(records) {
  const n = records.length;
  let previous = null;
  const funnel = STAGES.map((stage, index) => {
    const reached = records.filter(r => STAGES.indexOf(r.highest_stage_reached) >= index).length;
    const entry = {
      stage, reached,
      percent_of_applicants: percent(reached, n),
      percent_of_prior_reached: previous === null ? null : percent(reached, previous),
      denominator_all_applicants: n,
      denominator_previous_stage: previous,
    };
    previous = reached;
    return entry;
  });
  const channels = CHANNELS.map(channel => {
    const group = records.filter(r => r.channel === channel);
    const m = group.length;
    const interview_reached = group.filter(r => STAGES.indexOf(r.highest_stage_reached) >= 2).length;
    const offer_reached = group.filter(r => STAGES.indexOf(r.highest_stage_reached) >= 3).length;
    const hired = group.filter(r => r.highest_stage_reached === 'Hired').length;
    return {
      channel, applicants: m, interview_reached,
      interview_reach_percent: percent(interview_reached, m), offer_reached,
      offer_reach_percent: percent(offer_reached, m), hired,
      hired_percent: percent(hired, m),
    };
  });
  return { n, funnel, channels };
}

export function csvForRecords(records) {
  const columns = ['id', 'department', 'role', 'channel', 'cohort', 'submitted_at', 'highest_stage_reached', 'status'];
  const quote = value => `"${String(value ?? '').replaceAll('"', '""')}"`;
  return [columns.map(quote).join(','), ...records.map(record => columns.map(key => quote(record[key])).join(','))].join('\r\n') + '\r\n';
}

export function formatPercent(value) { return value === null ? 'N/A' : `${value.toFixed(1)}%`; }

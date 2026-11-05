#!/usr/bin/env bash
set -euo pipefail

app_jar="target/recruitment-management-system-2026.1.0.jar"
port="18081"
log_file="${TMPDIR:-/tmp}/rms-spring-boot-restart-smoke.log"
job_title="Restart Persistence Verification"
process_id=""

start_app() {
  SERVER_PORT="$port" java -jar "$app_jar" >>"$log_file" 2>&1 &
  process_id=$!
  for _ in $(seq 1 60); do
    if curl --silent --fail "http://127.0.0.1:${port}/api/health" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  cat "$log_file"
  return 1
}

stop_app() {
  if [[ -n "$process_id" ]] && kill -0 "$process_id" 2>/dev/null; then
    kill "$process_id"
    wait "$process_id" || true
  fi
  process_id=""
}
trap stop_app EXIT

start_app
created="$(curl --silent --show-error --fail \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"${job_title}\",\"team\":\"Verification\",\"location\":\"Local CI\"}" \
  "http://127.0.0.1:${port}/api/jobs")"
job_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"$created")"
stop_app

start_app
curl --silent --show-error --fail "http://127.0.0.1:${port}/api/jobs" \
  | python3 -c 'import json,sys; rows=json.load(sys.stdin); assert any(row["id"] == int(sys.argv[1]) and row["title"] == sys.argv[2] for row in rows), "MySQL record missing after application restart"' "$job_id" "$job_title"
printf 'PASS: job %s persisted in MySQL across Spring Boot restart\n' "$job_id"

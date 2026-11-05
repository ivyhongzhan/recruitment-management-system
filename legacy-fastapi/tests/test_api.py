"""API integration tests use a separate SQLite database and no web services."""
import pytest
from fastapi.testclient import TestClient
from app.main import create_app


@pytest.fixture
def client(tmp_path):
    with TestClient(create_app(tmp_path / "test.sqlite3", seed_demo=True)) as c:
        yield c


def new_candidate(name="Pat Example", email="pat@example.test", job_id=1):
    return {"full_name": name, "email": email, "job_id": job_id}


def test_demo_seed_is_explicit_and_idempotent(client):
    first = client.get('/api/metrics').json()
    assert first['jobs'] == 4
    assert first['applications'] == 14
    assert sum(first['stage_counts'].values()) == 14
    assert client.get('/api/health').json() == {'status': 'ok', 'dataset': 'synthetic_demo'}


def test_create_application_persists_and_is_audited(client):
    response = client.post('/api/applications', json=new_candidate())
    assert response.status_code == 201, response.text
    record = response.json()
    assert record['stage'] == 'applied'
    records = client.get('/api/applications').json()
    assert any(r['id'] == record['id'] and r['full_name'] == 'Pat Example' for r in records)
    assert any(event['entity_id'] == record['id'] and event['event_type'] == 'application_created'
               for event in client.get('/api/audit').json())


def test_stage_transitions_and_optimistic_version(client):
    record = client.post('/api/applications', json=new_candidate()).json()
    url = f"/api/applications/{record['id']}/stage"
    assert client.patch(url, json={"stage": "offer", "expected_version": 1}).status_code == 409
    moved = client.patch(url, json={"stage": "screening", "expected_version": 1})
    assert moved.status_code == 200
    assert moved.json()['version'] == 2
    assert client.patch(url, json={"stage": "rejected", "expected_version": 1}).status_code == 409
    assert client.patch(url, json={"stage": "rejected", "expected_version": 2}).status_code == 200
    assert client.patch(url, json={"stage": "applied", "expected_version": 3}).status_code == 409
    assert any(e['from_stage'] == 'screening' and e['to_stage'] == 'rejected'
               for e in client.get('/api/audit').json())


def test_duplicate_and_case_insensitive_email(client):
    assert client.post('/api/applications', json=new_candidate()).status_code == 201
    assert client.post('/api/applications', json=new_candidate(email='PAT@EXAMPLE.TEST')).status_code == 409
    assert client.post('/api/applications', json=new_candidate(job_id=2, email='PAT@EXAMPLE.TEST')).status_code == 201
    # One candidate can apply to more than one job; records remain normalized.
    assert client.get('/api/metrics').json()['candidates'] == 15


def test_close_and_reopen_job(client):
    url = '/api/jobs/1/status'
    assert client.patch(url, json={"status": "closed"}).status_code == 200
    response = client.post('/api/applications', json=new_candidate())
    assert response.status_code == 409
    assert client.patch(url, json={"status": "open"}).status_code == 200
    assert client.post('/api/applications', json=new_candidate()).status_code == 201


def test_create_job_and_validation(client):
    response = client.post('/api/jobs', json={"title": "Research Assistant", "team": "Methods", "location": "Remote"})
    assert response.status_code == 201
    assert response.json()['id'] == 5
    assert client.post('/api/jobs', json={"title": "X", "team": "Q", "location": "N"}).status_code == 422
    assert client.post('/api/applications', json=new_candidate(email="not-an-email")).status_code == 422
    assert client.post('/api/applications', json=new_candidate(job_id=999)).status_code == 404
    assert client.patch('/api/applications/999/stage', json={"stage":"offer", "expected_version":1}).status_code == 404


def test_literal_search_wildcard_does_not_match_everything(client):
    assert client.get('/api/applications?search=%25').json() == []
    found = client.get('/api/applications?search=alex').json()
    assert len(found) == 1
    assert found[0]['full_name'] == 'Alex Rivera'
    assert all(row['stage'] == 'applied' for row in client.get('/api/applications?stage=applied').json())
    assert client.get('/api/applications?stage=unknown').status_code == 422


def test_database_survives_new_app_instance(tmp_path):
    path = tmp_path / 'persistent.sqlite3'
    with TestClient(create_app(path, seed_demo=False)) as first:
        job = first.post('/api/jobs', json={"title":"UX Researcher", "team":"Product", "location":"Remote"}).json()
        assert first.post('/api/applications', json=new_candidate(job_id=job['id'])).status_code == 201
    with TestClient(create_app(path, seed_demo=False)) as reopened:
        assert reopened.get('/api/metrics').json()['applications'] == 1
        assert reopened.get('/api/applications').json()[0]['full_name'] == 'Pat Example'


def test_homepage_and_api_schema_available(client):
    assert client.get('/').status_code == 200
    assert 'Candidate pipeline' in client.get('/').text
    assert client.get('/static/app.js').status_code == 200
    assert client.get('/openapi.json').status_code == 200


def test_research_portfolio_pages_are_served(client):
    pages = {
        '/portfolio/': 'Three complementary lines of work',
        '/portfolio/masters.html': '15 semi-structured interviews',
        '/portfolio/undergraduate.html': 'Recruitment Management System',
        '/portfolio/genai.html': 'Beyond the Aggregate',
        '/portfolio/styles.css': 'font-family',
        '/portfolio/MSc_Methods_and_Evidence.md': 'MSc Dissertation',
    }
    for path, text in pages.items():
        response = client.get(path)
        assert response.status_code == 200, path
        assert text in response.text, path


def test_portfolio_has_no_external_script_tracking(client):
    page = client.get('/portfolio/').text
    assert '<script src="https://' not in page
    assert 'google-analytics.com' not in page
    assert 'href="masters.html"' in page
    assert 'href="undergraduate.html"' in page


def test_portfolio_internal_resources_are_present(client):
    from html.parser import HTMLParser
    from urllib.parse import urlsplit

    class References(HTMLParser):
        def __init__(self):
            super().__init__()
            self.urls = []

        def handle_starttag(self, tag, attrs):
            values = dict(attrs)
            if tag in {"a", "link", "script"}:
                url = values.get("href") or values.get("src")
                if url:
                    self.urls.append(url)

    for page in ('/', '/masters.html', '/undergraduate.html', '/genai.html'):
        result = client.get('/portfolio' + page)
        assert result.status_code == 200
        parser = References()
        parser.feed(result.text)
        for url in parser.urls:
            parsed = urlsplit(url)
            if parsed.scheme or parsed.netloc or not parsed.path or parsed.path.startswith('#'):
                continue
            if parsed.path == '/':  # The application demo is at the service root.
                url_to_check = '/'
            elif parsed.path.startswith('/'):
                url_to_check = parsed.path
            else:
                url_to_check = '/portfolio/' + parsed.path
            assert client.get(url_to_check).status_code == 200, f'{page}: {url}'


def test_application_detail_and_scoped_history(client):
    record = client.post('/api/applications', json=new_candidate()).json()
    url = f"/api/applications/{record['id']}"
    initial = client.get(url)
    assert initial.status_code == 200
    assert initial.json()['stage'] == 'applied'
    assert initial.json()['allowed_next'] == ['screening', 'rejected']
    assert len(initial.json()['history']) == 1
    assert initial.json()['history'][0]['event_type'] == 'application_created'
    assert client.patch(url + '/stage', json={'stage':'screening', 'expected_version':1}).status_code == 200
    detail = client.get(url).json()
    assert detail['stage'] == 'screening'
    assert detail['version'] == 2
    assert detail['history'][0]['from_stage'] == 'applied'
    assert detail['history'][0]['to_stage'] == 'screening'
    assert all(e['event_type'] in ('application_created', 'application_stage_changed') for e in detail['history'])
    assert client.get('/api/applications/999999').status_code == 404


def test_ui_review_resources_and_reviewer_entrypoints(client):
    app_html = client.get('/').text
    assert '90-SECOND PRODUCT TOUR' in app_html
    assert 'id="board-columns"' in app_html
    assert 'id="detail-dialog"' in app_html
    assert 'href="/static/polish.css"' in app_html
    assert client.get('/static/polish.css').status_code == 200
    case = client.get('/portfolio/undergraduate.html').text
    assert 'Design decisions' in case
    assert 'not a screenshot' in case
    assert 'No usability participants' in case
    assert client.get('/portfolio/systems-case-study.css').status_code == 200


def test_app_has_skip_link_and_unique_html_ids(client):
    from html.parser import HTMLParser
    class IdCollector(HTMLParser):
        def __init__(self):
            super().__init__()
            self.ids = []
        def handle_starttag(self, tag, attrs):
            found = dict(attrs).get('id')
            if found:
                self.ids.append(found)

    for path in ('/', '/portfolio/undergraduate.html'):
        html = client.get(path).text
        parser = IdCollector()
        parser.feed(html)
        assert len(parser.ids) == len(set(parser.ids)), f'Duplicate IDs in {path}'
        assert 'Skip to ' in html

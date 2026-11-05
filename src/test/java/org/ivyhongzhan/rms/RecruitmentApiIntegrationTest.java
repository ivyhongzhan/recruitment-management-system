package org.ivyhongzhan.rms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.core.env.MapPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="rms.seed-demo=true")
@Sql(statements={"DELETE FROM audit_events","DELETE FROM applications","DELETE FROM candidates","DELETE FROM jobs"},executionPhase=ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM audit_events","DELETE FROM applications","DELETE FROM candidates","DELETE FROM jobs"},executionPhase=ExecutionPhase.AFTER_TEST_METHOD)
class RecruitmentApiIntegrationTest {
 static final MySQLContainer<?> MYSQL=System.getenv("RMS_TEST_MYSQL_URL")==null?new MySQLContainer<>("mysql:8.4").withDatabaseName("rms_test").withUsername("test").withPassword("test"):null;
 @BeforeAll static void startLocalMySqlIfNeeded(){if(MYSQL!=null)MYSQL.start();}
 @AfterAll static void stopLocalMySqlIfStarted(){if(MYSQL!=null)MYSQL.stop();}
 @DynamicPropertySource static void mysql(DynamicPropertyRegistry r){String url=System.getenv("RMS_TEST_MYSQL_URL");r.add("spring.datasource.url",()->url!=null?url:MYSQL.getJdbcUrl());r.add("spring.datasource.username",()->System.getenv().getOrDefault("RMS_TEST_MYSQL_USERNAME",MYSQL==null?"test":MYSQL.getUsername()));r.add("spring.datasource.password",()->System.getenv().getOrDefault("RMS_TEST_MYSQL_PASSWORD",MYSQL==null?"test":MYSQL.getPassword()));}
 @LocalServerPort int port; @Autowired TestRestTemplate http;
 String url(String p){return "http://localhost:"+port+p;}
 @Test void mysqlSeedAndHealthAreSynthetic(){Map<String,Object> health=http.getForObject(url("/api/health"),Map.class);assertThat(health.get("dataset")).isEqualTo("synthetic_demo");Map<String,Object> metrics=http.getForObject(url("/api/metrics"),Map.class);assertThat(metrics.get("jobs")).isEqualTo(4);assertThat(metrics.get("applications")).isEqualTo(14);}
 @Test void applicationLifecycleIsValidatedPersistedAndAudited(){
  Map<String,Object> job=Map.of("title","Research Assistant","team","Methods","location","Remote");ResponseEntity<Map> createdJob=http.postForEntity(url("/api/jobs"),job,Map.class);assertThat(createdJob.getStatusCode()).isEqualTo(HttpStatus.CREATED);long jobId=((Number)createdJob.getBody().get("id")).longValue();
  Map<String,Object> body=Map.of("full_name","Pat Example","email","pat@example.test","job_id",jobId);ResponseEntity<Map> created=http.postForEntity(url("/api/applications"),body,Map.class);assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);long id=((Number)created.getBody().get("id")).longValue();
  ResponseEntity<Map> invalid=http.exchange(url("/api/applications/"+id+"/stage"),HttpMethod.PATCH,new HttpEntity<>(Map.of("stage","offer","expected_version",1)),Map.class);assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  Map<String,Object> valid=Map.of("stage","screening","expected_version",1);Map<String,Object> moved=http.patchForObject(url("/api/applications/"+id+"/stage"),valid,Map.class);assertThat(moved.get("version")).isEqualTo(2);
  Map<String,Object> detail=http.getForObject(url("/api/applications/"+id),Map.class);assertThat(detail.get("stage")).isEqualTo("screening");assertThat((List<?>)detail.get("history")).hasSize(2);
  ResponseEntity<Map> duplicate=http.postForEntity(url("/api/applications"),body,Map.class);assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(http.getForObject(url("/api/audit"),List.class)).isNotEmpty();
 }
 @Test void searchAndJobClosingWork(){ResponseEntity<Map> close=http.exchange(url("/api/jobs/1/status"),HttpMethod.PATCH,new HttpEntity<>(Map.of("status","closed")),Map.class);assertThat(close.getStatusCode()).isEqualTo(HttpStatus.OK);ResponseEntity<Map> rejected=http.postForEntity(url("/api/applications"),Map.of("full_name","Another Person","email","another@example.test","job_id",1),Map.class);assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(http.getForObject(url("/api/applications?search=alex"),List.class)).hasSize(1);}
 @Test void frontendAndApiAreServedByTheRunningApplication(){ResponseEntity<String> page=http.getForEntity(url("/"),String.class);assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(page.getBody()).contains("Candidate pipeline");assertThat(http.getForEntity(url("/static/app.js"),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(http.getForEntity(url("/portfolio/undergraduate.html"),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(http.getForEntity(url("/api/jobs"),List.class).getStatusCode()).isEqualTo(HttpStatus.OK);}
 @Test void inputValidationAndUnknownResourcesReturnExpectedStatuses(){ResponseEntity<Map> badJob=http.postForEntity(url("/api/jobs"),Map.of("title","x","team","Q","location","N"),Map.class);assertThat(badJob.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);ResponseEntity<Map> badEmail=http.postForEntity(url("/api/applications"),Map.of("full_name","Bad Email","email","not-an-email","job_id",1),Map.class);assertThat(badEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);ResponseEntity<Map> missing=http.getForEntity(url("/api/applications/999999"),Map.class);assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);}
}

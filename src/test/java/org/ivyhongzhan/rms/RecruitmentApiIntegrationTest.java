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
import org.springframework.core.env.MapPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="rms.seed-demo=true")
class RecruitmentApiIntegrationTest {
 static final MySQLContainer<?> MYSQL=System.getenv("RMS_TEST_MYSQL_URL")==null?new MySQLContainer<>("mysql:8.4").withDatabaseName("rms_test").withUsername("test").withPassword("test"):null;
 @BeforeAll static void startLocalMySqlIfNeeded(){if(MYSQL!=null)MYSQL.start();}
 @AfterAll static void stopLocalMySqlIfStarted(){if(MYSQL!=null)MYSQL.stop();}
 @DynamicPropertySource static void mysql(DynamicPropertyRegistry r){String url=System.getenv("RMS_TEST_MYSQL_URL");r.add("spring.datasource.url",()->url!=null?url:MYSQL.getJdbcUrl());r.add("spring.datasource.username",()->System.getenv().getOrDefault("RMS_TEST_MYSQL_USERNAME",MYSQL==null?"test":MYSQL.getUsername()));r.add("spring.datasource.password",()->System.getenv().getOrDefault("RMS_TEST_MYSQL_PASSWORD",MYSQL==null?"test":MYSQL.getPassword()));}
 @LocalServerPort int port; @Autowired TestRestTemplate http;
 String url(String p){return "http://localhost:"+port+p;}
 @Test void mysqlSeedAndHealthAreSynthetic(){Map<String,Object> health=http.getForObject(url("/api/health"),Map.class);assertThat(health.get("dataset")).isEqualTo("synthetic_demo");Map<String,Object> metrics=http.getForObject(url("/api/metrics"),Map.class);assertThat(((Number)metrics.get("jobs")).longValue()).isGreaterThanOrEqualTo(4);assertThat(((Number)metrics.get("applications")).longValue()).isGreaterThanOrEqualTo(14);}
 @Test void applicationLifecycleIsValidatedPersistedAndAudited(){
  Map<String,Object> job=Map.of("title","Research Assistant","team","Methods","location","Remote");ResponseEntity<Map> createdJob=http.postForEntity(url("/api/jobs"),job,Map.class);assertThat(createdJob.getStatusCode()).isEqualTo(HttpStatus.CREATED);long jobId=((Number)createdJob.getBody().get("id")).longValue();
  Map<String,Object> body=Map.of("full_name","Pat Example","email","pat@example.test","job_id",jobId);ResponseEntity<Map> created=http.postForEntity(url("/api/applications"),body,Map.class);assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);long id=((Number)created.getBody().get("id")).longValue();
  ResponseEntity<Map> invalid=http.exchange(url("/api/applications/"+id+"/stage"),HttpMethod.PATCH,new HttpEntity<>(Map.of("stage","offer","expected_version",1)),Map.class);assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  long version=1;
  for(String stage:List.of("screening","interview","offer","hired")){Map<String,Object> moved=http.patchForObject(url("/api/applications/"+id+"/stage"),Map.of("stage",stage,"expected_version",version),Map.class);version++;assertThat(((Number)moved.get("version")).longValue()).isEqualTo(version);}
  ResponseEntity<Map> terminal=http.exchange(url("/api/applications/"+id+"/stage"),HttpMethod.PATCH,new HttpEntity<>(Map.of("stage","screening","expected_version",version)),Map.class);assertThat(terminal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  Map<String,Object> detail=http.getForObject(url("/api/applications/"+id),Map.class);assertThat(detail.get("stage")).isEqualTo("hired");assertThat((List<?>)detail.get("history")).hasSize(5);
  ResponseEntity<Map> duplicate=http.postForEntity(url("/api/applications"),body,Map.class);assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  Map<String,Object> rejectedApplication=http.postForObject(url("/api/applications"),Map.of("full_name","Reject Example","email","reject@example.test","job_id",jobId),Map.class);ResponseEntity<Map> rejected=http.exchange(url("/api/applications/"+rejectedApplication.get("id")+"/stage"),HttpMethod.PATCH,new HttpEntity<>(Map.of("stage","rejected","expected_version",1)),Map.class);assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
  Map<String,Object> secondJob=http.postForObject(url("/api/jobs"),Map.of("title","Another Role","team","Methods","location","Remote"),Map.class);Map<String,Object> reused=http.postForObject(url("/api/applications"),Map.of("full_name","Pat Example","email","PAT@EXAMPLE.TEST","job_id",secondJob.get("id")),Map.class);assertThat(((Number)reused.get("candidate_id")).longValue()).isEqualTo(((Number)created.getBody().get("candidate_id")).longValue());
  assertThat(http.getForObject(url("/api/applications?search=%25"),List.class)).isEmpty();
  assertThat(http.getForObject(url("/api/audit"),List.class)).isNotEmpty();
 }
 @Test void searchAndJobClosingWork(){ResponseEntity<Map> created=http.postForEntity(url("/api/jobs"),Map.of("title","Closing Test Role","team","Testing","location","Remote"),Map.class);long jobId=((Number)created.getBody().get("id")).longValue();ResponseEntity<Map> close=http.exchange(url("/api/jobs/"+jobId+"/status"),HttpMethod.PATCH,new HttpEntity<>(Map.of("status","closed")),Map.class);assertThat(close.getStatusCode()).isEqualTo(HttpStatus.OK);ResponseEntity<Map> rejected=http.postForEntity(url("/api/applications"),Map.of("full_name","Another Person","email","another@example.test","job_id",jobId),Map.class);assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);ResponseEntity<Map> reopen=http.exchange(url("/api/jobs/"+jobId+"/status"),HttpMethod.PATCH,new HttpEntity<>(Map.of("status","open")),Map.class);assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(http.postForEntity(url("/api/applications"),Map.of("full_name","Another Person","email","another@example.test","job_id",jobId),Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);assertThat(http.getForObject(url("/api/applications?search=alex"),List.class)).isNotEmpty();}
 @Test void frontendAndApiAreServedByTheRunningApplication(){ResponseEntity<String> page=http.getForEntity(url("/"),String.class);assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(page.getBody()).contains("Candidate pipeline");assertThat(http.getForEntity(url("/static/app.js"),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(http.getForEntity(url("/portfolio/undergraduate.html"),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(http.getForEntity(url("/api/jobs"),List.class).getStatusCode()).isEqualTo(HttpStatus.OK);}
 @Test void inputValidationAndUnknownResourcesReturnExpectedStatuses(){ResponseEntity<Map> badJob=http.postForEntity(url("/api/jobs"),Map.of("title","x","team","Q","location","N"),Map.class);assertThat(badJob.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);ResponseEntity<Map> badEmail=http.postForEntity(url("/api/applications"),Map.of("full_name","Bad Email","email","not-an-email","job_id",1),Map.class);assertThat(badEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);ResponseEntity<Map> missing=http.getForEntity(url("/api/applications/999999"),Map.class);assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);}
}

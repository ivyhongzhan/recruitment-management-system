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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="rms.seed-demo=true")
class RecruitmentApiIntegrationTest {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("rms_test").withUsername("test").withPassword("test");
 @DynamicPropertySource static void mysql(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
 @LocalServerPort int port; @Autowired TestRestTemplate http;
 String url(String p){return "http://localhost:"+port+p;}
 @Test void mysqlSeedAndHealthAreSynthetic(){Map<?,?> health=http.getForObject(url("/api/health"),Map.class);assertThat(health).containsEntry("dataset","synthetic_demo");Map<?,?> metrics=http.getForObject(url("/api/metrics"),Map.class);assertThat(metrics.get("jobs")).isEqualTo(4L);assertThat(metrics.get("applications")).isEqualTo(14L);}
 @Test void applicationLifecycleIsValidatedPersistedAndAudited(){
  Map<String,Object> job=Map.of("title","Research Assistant","team","Methods","location","Remote");ResponseEntity<Map> createdJob=http.postForEntity(url("/api/jobs"),job,Map.class);assertThat(createdJob.getStatusCode()).isEqualTo(HttpStatus.CREATED);long jobId=((Number)createdJob.getBody().get("id")).longValue();
  Map<String,Object> body=Map.of("full_name","Pat Example","email","pat@example.test","job_id",jobId);ResponseEntity<Map> created=http.postForEntity(url("/api/applications"),body,Map.class);assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);long id=((Number)created.getBody().get("id")).longValue();
  ResponseEntity<Map> invalid=http.exchange(url("/api/applications/"+id+"/stage"),HttpMethod.PATCH,new HttpEntity<>(Map.of("stage","offer","expected_version",1)),Map.class);assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  Map<String,Object> valid=Map.of("stage","screening","expected_version",1);Map<?,?> moved=http.patchForObject(url("/api/applications/"+id+"/stage"),valid,Map.class);assertThat(moved).containsEntry("version",2);
  Map<?,?> detail=http.getForObject(url("/api/applications/"+id),Map.class);assertThat(detail).containsEntry("stage","screening");assertThat((List<?>)detail.get("history")).hasSize(2);
  ResponseEntity<Map> duplicate=http.postForEntity(url("/api/applications"),body,Map.class);assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(http.getForObject(url("/api/audit"),List.class)).isNotEmpty();
 }
 @Test void searchAndJobClosingWork(){ResponseEntity<Map> close=http.exchange(url("/api/jobs/1/status"),HttpMethod.PATCH,new HttpEntity<>(Map.of("status","closed")),Map.class);assertThat(close.getStatusCode()).isEqualTo(HttpStatus.OK);ResponseEntity<Map> rejected=http.postForEntity(url("/api/applications"),Map.of("full_name","Another Person","email","another@example.test","job_id",1),Map.class);assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(http.getForObject(url("/api/applications?search=alex"),List.class)).hasSize(1);}
}

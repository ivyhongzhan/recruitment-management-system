package org.ivyhongzhan.rms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

record JobInput(@NotBlank @Size(min=2,max=100) String title,@NotBlank @Size(min=2,max=80) String team,@NotBlank @Size(min=2,max=100) String location) {}
record JobStatusInput(@Pattern(regexp="open|closed") String status) {}
record ApplicationInput(@NotBlank @Size(min=2,max=100) String full_name,@NotBlank @Email @Size(max=254) String email,@Positive Long job_id) {}
record StageInput(@NotNull Stage stage,@Positive Long expected_version) {}

@RestController
class ApiController {
 private static final Map<Stage,List<Stage>> NEXT=Map.of(Stage.applied,List.of(Stage.screening,Stage.rejected),Stage.screening,List.of(Stage.interview,Stage.rejected),Stage.interview,List.of(Stage.offer,Stage.rejected),Stage.offer,List.of(Stage.hired,Stage.rejected),Stage.hired,List.of(),Stage.rejected,List.of());
 private final JobRepository jobs; private final CandidateRepository candidates; private final ApplicationRepository applications; private final AuditRepository audit;
 ApiController(JobRepository j,CandidateRepository c,ApplicationRepository a,AuditRepository au){jobs=j;candidates=c;applications=a;audit=au;}
 @GetMapping("/api/health") Map<String,String> health(){return Map.of("status","ok","dataset","synthetic_demo");}
 @GetMapping("/api/jobs") List<Map<String,Object>> listJobs(){return jobs.listWithCounts().stream().map(row->{Map<String,Object> m=new LinkedHashMap<>();row.forEach((k,v)->m.put(k.equals("applicationCount")?"application_count":k,v));return m;}).toList();}
 @PostMapping("/api/jobs") @ResponseStatus(HttpStatus.CREATED) @Transactional Map<String,Object> createJob(@Valid @RequestBody JobInput p){Job j=jobs.save(new Job(p.title().trim(),p.team().trim(),p.location().trim())); audit.save(new AuditEvent("job_created","job",j.id,null,null,"")); return map("id",j.id,"title",j.title,"team",j.team,"location",j.location,"status",j.status);}
 @PatchMapping("/api/jobs/{id}/status") @Transactional Map<String,Object> updateJob(@PathVariable Long id,@Valid @RequestBody JobStatusInput p){Job j=jobs.findById(id).orElseThrow(()->notFound("Job not found"));if(!j.status.equals(p.status())){String old=j.status;j.status=p.status();jobs.save(j);audit.save(new AuditEvent("job_status_changed","job",id,null,null,old+" -> "+p.status()));}return map("id",id,"status",p.status());}
 @GetMapping("/api/applications") List<Map<String,Object>> listApplications(@RequestParam(defaultValue="") @Size(max=120) String search,@RequestParam(required=false) Long job_id,@RequestParam(required=false) String stage){Stage s=null;if(stage!=null&&!stage.isBlank())try{s=Stage.valueOf(stage);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Unknown stage");} if(job_id!=null&&job_id<1)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);return applications.search(search.trim(),job_id,s).stream().limit(500).map(this::appMap).toList();}
 @GetMapping("/api/applications/{id}") Map<String,Object> detail(@PathVariable Long id){ApplicationRecord a=applications.findDetailed(id).orElseThrow(()->notFound("Application not found"));Map<String,Object> result=appMap(a);result.put("location",a.job.location);result.put("history",audit.findByEntityTypeAndEntityIdOrderByIdDesc("application",id).stream().map(this::auditMap).toList());return result;}
 @PostMapping("/api/applications") @ResponseStatus(HttpStatus.CREATED) @Transactional Map<String,Object> createApplication(@Valid @RequestBody ApplicationInput p){Job job=jobs.findById(p.job_id()).orElseThrow(()->notFound("Job not found"));if(!job.status.equals("open"))throw conflict("This job is closed to new applications");String email=p.email().toLowerCase(Locale.ROOT);Candidate c=candidates.findByEmailIgnoreCase(email).orElseGet(()->candidates.save(new Candidate(p.full_name().trim(),email)));if(applications.existsByCandidateIdAndJobId(c.id,job.id))throw conflict("This candidate already applied to this job");try{ApplicationRecord a=applications.saveAndFlush(new ApplicationRecord(c,job));audit.save(new AuditEvent("application_created","application",a.id,null,"applied",""));return map("id",a.id,"candidate_id",c.id,"job_id",job.id,"stage","applied","version",1L);}catch(DataIntegrityViolationException e){throw conflict("This candidate already applied to this job");}}
 @PatchMapping("/api/applications/{id}/stage") @Transactional Map<String,Object> updateStage(@PathVariable Long id,@Valid @RequestBody StageInput p){ApplicationRecord a=applications.findById(id).orElseThrow(()->notFound("Application not found"));if(!Objects.equals(a.version,p.expected_version()))throw conflict("Record changed; refresh and try again");if(!NEXT.get(a.stage).contains(p.stage()))throw conflict("Invalid transition from "+a.stage+" to "+p.stage());Stage from=a.stage;a.stage=p.stage();a.updatedAt=Instant.now();applications.saveAndFlush(a);audit.save(new AuditEvent("application_stage_changed","application",id,from.name(),p.stage().name(),""));return map("id",id,"stage",p.stage().name(),"version",p.expected_version()+1);}
 @GetMapping("/api/metrics") Map<String,Object> metrics(){Map<String,Long> counts=new LinkedHashMap<>();for(Stage s:Stage.values())counts.put(s.name(),applications.countByStage(s));long total=counts.values().stream().mapToLong(Long::longValue).sum();return map("jobs",jobs.count(),"open_jobs",jobs.countByStatus("open"),"candidates",candidates.count(),"applications",total,"active_applications",counts.get("applied")+counts.get("screening")+counts.get("interview")+counts.get("offer"),"stage_counts",counts,"by_job",applications.countsByJob(),"note","Snapshot counts from synthetic demo records; not conversion rates or research findings.");}
 @GetMapping("/api/audit") List<Map<String,Object>> auditEvents(@RequestParam(defaultValue="40") @Min(1) @Max(200) int limit){return audit.findTop200ByOrderByIdDesc().stream().limit(limit).map(this::auditMap).toList();}
 private Map<String,Object> appMap(ApplicationRecord a){return map("id",a.id,"job_id",a.job.id,"candidate_id",a.candidate.id,"stage",a.stage.name(),"version",a.version,"created_at",a.createdAt,"updated_at",a.updatedAt,"full_name",a.candidate.fullName,"email",a.candidate.email,"job_title",a.job.title,"team",a.job.team,"job_status",a.job.status,"allowed_next",NEXT.get(a.stage).stream().map(Enum::name).toList());}
 private Map<String,Object> auditMap(AuditEvent e){return map("id",e.id,"event_type",e.eventType,"entity_type",e.entityType,"entity_id",e.entityId,"from_stage",e.fromStage,"to_stage",e.toStage,"details",e.details,"created_at",e.createdAt);}
 private static Map<String,Object> map(Object... kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
 private static ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);} private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}

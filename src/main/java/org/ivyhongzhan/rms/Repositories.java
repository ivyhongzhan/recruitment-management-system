package org.ivyhongzhan.rms;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

interface JobRepository extends JpaRepository<Job,Long> {
 @Query("select j.id as id,j.title as title,j.team as team,j.location as location,j.status as status,j.createdAt as createdAt,count(a.id) as applicationCount from Job j left join ApplicationRecord a on a.job.id=j.id group by j.id order by j.id") List<Map<String,Object>> listWithCounts();
 long countByStatus(String status);
}
interface CandidateRepository extends JpaRepository<Candidate,Long> { Optional<Candidate> findByEmailIgnoreCase(String email); }
interface ApplicationRepository extends JpaRepository<ApplicationRecord,Long> {
 boolean existsByCandidateIdAndJobId(Long candidateId,Long jobId);
 @Query("select a from ApplicationRecord a join fetch a.candidate c join fetch a.job j where (:jobId is null or j.id=:jobId) and (:stage is null or a.stage=:stage) and (:search='' or lower(c.fullName) like lower(concat('%',:search,'%')) or lower(j.title) like lower(concat('%',:search,'%'))) order by a.updatedAt desc,a.id desc")
 List<ApplicationRecord> search(@Param("search") String search,@Param("jobId") Long jobId,@Param("stage") Stage stage);
 @Query("select a from ApplicationRecord a join fetch a.candidate join fetch a.job where a.id=:id") Optional<ApplicationRecord> findDetailed(@Param("id") Long id);
 long countByStage(Stage stage);
 @Query("select j.id as id,j.title as title,count(a.id) as count from Job j left join ApplicationRecord a on a.job.id=j.id group by j.id,j.title order by count(a.id) desc,j.id") List<Map<String,Object>> countsByJob();
}
interface AuditRepository extends JpaRepository<AuditEvent,Long> { List<AuditEvent> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType,Long entityId); List<AuditEvent> findTop200ByOrderByIdDesc(); }

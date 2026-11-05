package org.ivyhongzhan.rms;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
class SyntheticDemoSeeder implements ApplicationRunner {
 private final JobRepository jobs; private final CandidateRepository candidates; private final ApplicationRepository applications; private final AuditRepository audit;
 @Value("${rms.seed-demo:true}") boolean enabled;
 SyntheticDemoSeeder(JobRepository j,CandidateRepository c,ApplicationRepository a,AuditRepository au){jobs=j;candidates=c;applications=a;audit=au;}
 @Override @Transactional public void run(ApplicationArguments args){if(!enabled||jobs.count()>0||candidates.count()>0)return;
  String[][] jobData={{"Product Design Researcher","Experience","Hybrid · New York"},{"Data Analyst","Research Operations","Remote"},{"Frontend Engineer","Engineering","Hybrid · Boston"},{"Community Operations Associate","Operations","Remote"}};
  List<Job> js=new ArrayList<>();for(String[] j:jobData)js.add(jobs.save(new Job(j[0],j[1],j[2])));
  String[][] people={{"Alex Rivera","screening","1"},{"Morgan Chen","interview","1"},{"Sam Taylor","applied","1"},{"Casey Patel","offer","1"},{"Jordan Lee","applied","2"},{"Riley Kim","screening","2"},{"Avery Thompson","hired","2"},{"Drew Nguyen","rejected","2"},{"Jamie Park","interview","3"},{"Charlie Brown","screening","3"},{"Taylor Brooks","applied","3"},{"Reese Davis","applied","4"},{"Blair Wilson","interview","4"},{"Skylar James","rejected","4"}};
  for(String[] p:people){String slug=p[0].toLowerCase(Locale.ROOT).replace(" ",".");Candidate c=candidates.save(new Candidate(p[0],slug+"@example.test"));ApplicationRecord a=applications.save(new ApplicationRecord(c,js.get(Integer.parseInt(p[2])-1)));a.stage=Stage.valueOf(p[1]);applications.save(a);}
  audit.save(new AuditEvent("synthetic_data_seeded","system",null,null,null,"{\"fictional_jobs\":4,\"fictional_applications\":14}"));
 }
}

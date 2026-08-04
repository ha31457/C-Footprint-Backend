package com.infosys.cfootprint.repository.mongo;

import com.infosys.cfootprint.model.ActivityProofImage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityProofImageRepository extends MongoRepository<ActivityProofImage, String> {
}

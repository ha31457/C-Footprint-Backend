package com.infosys.cfootprint.repository.mongo;

import com.infosys.cfootprint.model.AvatarImage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvatarImageRepository extends MongoRepository<AvatarImage, String> {
}

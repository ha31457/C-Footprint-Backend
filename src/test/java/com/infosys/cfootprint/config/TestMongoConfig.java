package com.infosys.cfootprint.config;

import com.infosys.cfootprint.repository.mongo.ActivityProofImageRepository;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestMongoConfig {

    @Bean
    public ActivityProofImageRepository activityProofImageRepository() {
        return Mockito.mock(ActivityProofImageRepository.class);
    }

    @Bean
    public com.infosys.cfootprint.repository.mongo.AvatarImageRepository avatarImageRepository() {
        return Mockito.mock(com.infosys.cfootprint.repository.mongo.AvatarImageRepository.class);
    }
}

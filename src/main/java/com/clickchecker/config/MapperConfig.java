package com.clickchecker.config;

import com.clickchecker.mapper.EventUserMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MapperConfig {

    @Bean
    @Primary
    public EventUserMapper eventUserMapper() {
        return Mappers.getMapper(EventUserMapper.class);
    }
}

package com.xx.jttprotocolparse.web;

import com.xx.jttprotocolparse.core.descr.DescriptorRegistry;
import com.xx.jttprotocolparse.core.engine.ParseEngine;
import com.xx.jttprotocolparse.core.frame.FrameAdapter;
import com.xx.jttprotocolparse.core.frame.GB32960FrameAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** 装配：core 是纯 Java，Spring 只负责把引擎 bean 起来 + CORS */
@Configuration
public class BeanConfig {

    @Bean
    public DescriptorRegistry descriptorRegistry() {
        return new DescriptorRegistry();
    }

    @Bean
    public List<FrameAdapter> frameAdapters() {
        return List.of(new GB32960FrameAdapter());
    }

    @Bean
    public ParseEngine parseEngine(DescriptorRegistry registry, List<FrameAdapter> adapters) {
        return new ParseEngine(registry, adapters);
    }

    @Bean
    public WebMvcConfigurer cors() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry r) {
                r.addMapping("/api/**").allowedOrigins("*");
            }
        };
    }
}

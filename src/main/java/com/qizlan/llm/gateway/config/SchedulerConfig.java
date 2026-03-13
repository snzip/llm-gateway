package com.qizlan.llm.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class SchedulerConfig {

    @Bean
    public Scheduler controlPlaneScheduler() {
        return Schedulers.newBoundedElastic(20, Integer.MAX_VALUE, "control-plane");
    }
}

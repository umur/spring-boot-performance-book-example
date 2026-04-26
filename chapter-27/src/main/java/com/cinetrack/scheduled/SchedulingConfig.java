package com.cinetrack.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// Enables Spring's @Scheduled support and provides a shared TaskScheduler bean
// with a small thread pool. Without this bean, Spring creates a single-threaded
// scheduler, which means one slow job can delay all other scheduled tasks.
@Slf4j
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("scheduler-");
        // Log errors instead of swallowing them; the default handler silently drops them.
        scheduler.setErrorHandler(t ->
                log.error("Unhandled error in scheduled task: {}", t.getMessage(), t));
        return scheduler;
    }
}

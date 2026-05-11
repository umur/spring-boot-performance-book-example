package com.cinetrack.async;

import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Enables Spring's @Async support and configures a dedicated thread pool for
// notification emails. Using a named executor keeps notification threads
// separate from the servlet thread pool, preventing slow email delivery from
// blocking API responses.
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            LoggerFactory.getLogger(AsyncConfig.class)
                .error("Uncaught exception in async method {}: {}",
                    method.getName(), ex.getMessage(), ex);
    }

    // Notification email sending: I/O-bound, blocking SMTP.
    // Sizing follows 12.6: threads = cores × (1 + wait/compute). SMTP wait/compute
    // is ~10 (mostly network), so 8 cores × 11 ≈ 88, capped at 32 because beyond
    // that the SMTP server itself becomes the bottleneck.
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("notification-");
        // CallerRunsPolicy turns the caller thread into a back-pressure valve when
        // the queue saturates: without this, overflow silently drops emails.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

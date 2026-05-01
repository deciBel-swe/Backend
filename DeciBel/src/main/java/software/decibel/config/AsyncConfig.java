package software.decibel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(15);      // Minimum number of worker threads
        executor.setMaxPoolSize(15);      // Maximum number of worker threads
        executor.setQueueCapacity(50);   // How many tasks can wait in line before throwing an error
        executor.setThreadNamePrefix("TrackUpload-");
        executor.initialize();
        return executor;
    }

}

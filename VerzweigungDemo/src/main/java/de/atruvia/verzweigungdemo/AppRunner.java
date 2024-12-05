package de.atruvia.verzweigungdemo;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppRunner implements CommandLineRunner {


    private final JobLauncher jobLauncher;
    private final Job job;

    @Override
    public void run(final String... args) throws Exception {
        System.out.println("\nJoblauf mit Job-Parameter 'ok':");
        JobExecution je = jobLauncher.run(job, new JobParametersBuilder().toJobParameters());

    }

}

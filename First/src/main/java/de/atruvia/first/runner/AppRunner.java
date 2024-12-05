package de.atruvia.first.runner;


import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppRunner implements CommandLineRunner {


    private final JobLauncher jobLauncher;


    private  final Job job;



    @Override
    public void run(final String... args) throws Exception {
        JobExecution jobExecution = jobLauncher.run(job, new JobParametersBuilder().addString("UUID", UUID.randomUUID().toString()).toJobParameters());
        //JobExecution jobExecution = jobLauncher.run(job, new JobParametersBuilder().addString("DEMO", "Hello").toJobParameters());
        System.out.println(jobExecution.getStatus());
    }
}

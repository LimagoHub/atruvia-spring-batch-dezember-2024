package com.example.partitionierer.runner;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class AppRunner implements CommandLineRunner {


    private final JobLauncher jobLauncher;
    private final Job job;

    @Override
    public void run(final String... args) throws Exception {



        JobExecution jobExecution = jobLauncher.run(job, new JobParametersBuilder()
                .addString("UUID", UUID.randomUUID().toString())
                .addString("inputFile", "/Users/xgadpfg/git/Partitionierer/src/main/resources/xy-data.csv")
                .addString("prefix", "a")
                        .addLong("partitionSize", 10L)
                        .addString("workingDirectory", "/Users/xgadpfg/git/Partitionierer/src/main/resources")
                .toJobParameters()
        );

        System.out.println(jobExecution.getStatus());
    }
}

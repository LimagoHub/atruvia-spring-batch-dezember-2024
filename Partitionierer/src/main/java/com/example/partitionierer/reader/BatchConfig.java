package com.example.partitionierer.reader;


import com.example.partitionierer.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.StepExecutionAggregator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.SystemCommandTasklet;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

@RequiredArgsConstructor
@Configuration
public class BatchConfig {

    public static final Integer DEFAULT_PARTITION_SIZE = 10;
    public static final String DEFAULT_PARTITION_PREFIX = "a";
    public static final long TASKLET_TIMEOUT = 10000L;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ResourcePatternResolver resourcePatternResolver;

    @Bean
    @StepScope
    public FlatFileItemReader<Person> reader(@Value("#{stepExecutionContext[fileName]}") String filename) {
        var flatFileItemReader = new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")

                .resource(new ClassPathResource(filename))
                .delimited()
                .names("firstName", "lastName", "age")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                    setTargetType(Person.class);
                }})
                .build();
        //flatFileItemReader.setLinesToSkip(1);
        return flatFileItemReader;
    }

    @Bean
    public ItemWriter<Person> writer() {
        return new ItemWriter<Person>() {

            @Override
            public void write(Chunk<? extends Person> chunk) throws Exception {
                System.out.println(chunk);
            }
        };
    }

    @Bean
    public Step step1(JobRepository jobRepository,
                      FlatFileItemReader<Person> reader,
                      ItemWriter<Person> writer) {

        return new StepBuilder("step1", jobRepository)
                .<Person, Person> chunk(10, transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }

    //@Bean
    public Job importUserJob( Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .flow(step1)
                .end()
                .build();
    }

    @Bean
    public CustomMultiResourcePartitioner partitioner() {
        CustomMultiResourcePartitioner partitioner = new CustomMultiResourcePartitioner();
        Resource[] resources;
        try {
            resources = resourcePatternResolver
                    .getResources("file:src/main/resources/*.csv");
        } catch (IOException e) {
            throw new RuntimeException("I/O problems when resolving"
                    + " the input file pattern.", e);
        }

        partitioner.setResources(resources);
        return partitioner;
    }

    //@Bean
    public Step slaveStep()
            throws UnexpectedInputException, ParseException {
        return new StepBuilder("slaveStep", jobRepository).<Person, Person> chunk(1, transactionManager)
                .reader(reader(null))
                .writer(writer())
                .build();
    }

    /*@Bean(name = "partitionerJob")
    public Job partitionerJob(JobRepository jobRepository, PlatformTransactionManager transactionManager) throws UnexpectedInputException, ParseException {
        return new JobBuilder("partitionerJob", jobRepository)
                .start(partitionStep(jobRepository, transactionManager))
                .build();
    }
*/
    @Bean(name = "partitionerJob")
    public Job partitionerJob(Step splitFileStep, Step auswertungStep) throws UnexpectedInputException, ParseException {
        return new JobBuilder("partitionerJob", jobRepository)
                .start(splitFileStep)
                .next(partitionStep(jobRepository, transactionManager))
                .next(auswertungStep)
                .build();
    }

    @Bean
    public Step auswertungStep() {
        return new StepBuilder("auswertungStep", jobRepository).tasklet(new AusgabeTasklet(), transactionManager).build();
    }



    public class AusgabeTasklet implements Tasklet{

        @Override
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
            System.out.println("AusgabeTasklet Counter:" + chunkContext.getStepContext().getJobExecutionContext().get("Counter"));
            return RepeatStatus.FINISHED;
        }
    }

    @Bean
    public Step splitFileStep() {
        return new StepBuilder("splitFileStep", jobRepository).tasklet(processBuilderTasklet(),transactionManager)
                .build();
    }

    @Bean
    public Step partitionStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) throws UnexpectedInputException, ParseException {
        return new StepBuilder("partitionStep", jobRepository)
                .partitioner("slaveStep", partitioner())
                .step(slaveStep(jobRepository, transactionManager))
                .taskExecutor(new SimpleAsyncTaskExecutor())
                .aggregator(new StepExecutionAggregator() {
                    @Override
                    public void aggregate(StepExecution result, Collection<StepExecution> executions) {
                        int counter = 0;
                        for(StepExecution ex : executions) {
                            counter += ex.getReadCount();
                            System.out.println(ex);
                        }
                        System.out.println("Counter: " + counter);
                        result.getJobExecution().getExecutionContext().put("Counter", counter);
                    }
                })
                .build();
    }

    @Bean
    public Step slaveStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) throws UnexpectedInputException, ParseException {
        return new StepBuilder("slaveStep", jobRepository)
                .<Person, Person>chunk(1, transactionManager)
                .reader(reader(null))
                .writer(writer())
                .build();
    }

    public ProcessBuilder processBuilder(            String inputFile,
                                                       String prefix,
                                                       Integer partitionSize,
                                                       String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File(workingDirectory));
        builder.command("sh", "-c", String.format("split -a 1 -l %d %s %s",
                partitionSize == null ? DEFAULT_PARTITION_SIZE : partitionSize,
                inputFile,
                 prefix == null ? DEFAULT_PARTITION_PREFIX : prefix)
                );
        return builder;
    }

    @Bean
    public Tasklet processBuilderTasklet() {
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
               String inputFile = "/Users/xgadpfg/git/Partitionierer/src/main/resources/xy-data.csv";
                String prefix = "a";
                Integer partitionSize =10;
                 String workingDirectory = "/Users/xgadpfg/git/Partitionierer/src/main/resources";

                ProcessBuilder processBuilder = processBuilder(inputFile, prefix, partitionSize, workingDirectory);
                processBuilder.start();
                return RepeatStatus.FINISHED;
            }
        };
    }

    @Bean
    @StepScope
    public SystemCommandTasklet getSplitTasklet(
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['prefix']}") String prefix,
            @Value("#{jobParameters['partitionSize']}") Integer partitionSize,
            @Value("#{jobParameters['workingDirectory']}") String workingDirectory) {
        SystemCommandTasklet splitTasklet = new SystemCommandTasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
                RepeatStatus repeatStatus = super.execute(contribution, chunkContext);
                JobExecution jobExecution = chunkContext.getStepContext().getStepExecution().getJobExecution();
                jobExecution.getExecutionContext().put("inputFiles", "file:" + workingDirectory + DEFAULT_PARTITION_PREFIX + "_*");
                return repeatStatus;
            }
        };// sh -c "split -a 1 -l %d %s %s"
        splitTasklet.setCommand(String.format("ls") // tested on Mac OS which uses the BSD version of the 'split' command (which is different from the GNU version)
                //partitionSize == null ? DEFAULT_PARTITION_SIZE : partitionSize,
                //inputFile,
               // prefix == null ? DEFAULT_PARTITION_PREFIX : prefix)
        );
        splitTasklet.setTimeout(TASKLET_TIMEOUT);
        splitTasklet.setWorkingDirectory(workingDirectory);
        return splitTasklet;
    }


}

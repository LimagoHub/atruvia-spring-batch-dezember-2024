package de.atruvia.partitioner;


import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class PartitionBatchConfig {

    private final JobRepository repository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Step slaveStep()
    {
        return new StepBuilder("meinLeerzeilenStep", repository)
                .chunk(100).reader()


    }

    @Bean
    public Step partitionsStep(JobRepository repository, PlatformTransactionManager transactionManager)
    {
        return new StepBuilder("meinFinishStep", repository)
                .partitioner(slaveStep().getName(), partitioner())
                .step(slaveStep())
                .gridSize(10)
                .taskExecutor(new SimpleAsyncTaskExecutor())
                .build();

    }



    @Bean
    public Partitioner partitioner() {

        return new RangePartinionierer();
    }


}

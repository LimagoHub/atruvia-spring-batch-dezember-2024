package de.atruvia.taskdemo;


import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class TaskletJobConfiguration {
    public static final String ANZAHLSTEPS_KEY = "AnzahlSteps";

   private final JobRepository repository;
   private final PlatformTransactionManager transactionManager;


    @Bean
    public Step meinLeerzeilenStep()
    {
        return new StepBuilder("meinLeerzeilenStep", repository).tasklet(new Tasklet() {
            @Override
            public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) throws Exception {
                System.out.println( "" );
                return RepeatStatus.FINISHED;
            }
        },transactionManager).build();

    }

    @Bean
    public Step meinFinishStep(JobRepository repository, PlatformTransactionManager transactionManager)
    {
        return new StepBuilder("meinFinishStep", repository).tasklet(new Tasklet() {
            @Override
            public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) throws Exception {
                System.out.println(chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().get("myKey") );
                return RepeatStatus.FINISHED;
            }
        },transactionManager).build();

    }



    @Bean
    public Step meinArbeitsStep()
    {
        return new StepBuilder("meinArbeitsStep", repository).tasklet(new Tasklet() {
            @Override
            public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) throws Exception {

                String anz = chunkContext.getStepContext().getStepExecution().getJobParameters().getString( ANZAHLSTEPS_KEY );


                int anzahlDerDurchzufuerendenSteps = ( anz != null ) ? Integer.parseInt( anz ) : 4;

                long commitCount = chunkContext.getStepContext().getStepExecution().getCommitCount();
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().put("myKey", anzahlDerDurchzufuerendenSteps);

                System.out.println( "Hallo Spring Batch mit Tasklet, Tasklet-Execution " + commitCount );
                return ( commitCount < anzahlDerDurchzufuerendenSteps - 1 ) ? RepeatStatus.CONTINUABLE : RepeatStatus.FINISHED;
            }
        },transactionManager).build();
    }

    @Bean
    public Job meinTaskletJob(final JobRepository repository, final PlatformTransactionManager transactionManager) throws Exception
    {
        return new JobBuilder("meinTaskletJob", repository).incrementer( new RunIdIncrementer() )
                .start( meinLeerzeilenStep())
                .next(  meinArbeitsStep() )
                .next(  meinFinishStep(repository, transactionManager) )

                .build();
    }
}

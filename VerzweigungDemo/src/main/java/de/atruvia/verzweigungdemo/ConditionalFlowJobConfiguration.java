package de.atruvia.verzweigungdemo;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class ConditionalFlowJobConfiguration {
    private final JobRepository repository;
    private final PlatformTransactionManager transactionManager;
    public static final String OK_ODER_FEHLER = "OK_ODER_FEHLER";


    public class PrintTextTasklet implements Tasklet
    {
        final String text;

        public PrintTextTasklet( String text ) { this.text = text; }

        @Override
        public RepeatStatus execute( StepContribution sc, ChunkContext cc ) throws Exception {
            System.out.println( text );
            return RepeatStatus.FINISHED;
        }
    }

    public class ArbeitsTasklet extends PrintTextTasklet
    {
        public ArbeitsTasklet( String text ) { super( text ); }

        @Override
        public RepeatStatus execute( StepContribution sc, ChunkContext chunkContext ) throws Exception {
            StepContext stepContext = chunkContext.getStepContext();


            System.out.println( "\n---- Job: " + stepContext.getJobName() + ", mit JobParametern: " + stepContext.getJobParameters() );

            String okOderFehler = stepContext.getStepExecution().getJobParameters().getString( OK_ODER_FEHLER );

            if( ( okOderFehler != null ) ? !"ok".equalsIgnoreCase( okOderFehler ) : (Math.random() < 0.5) ) {
                System.out.println( super.text + ": mit Fehler" );
                throw new Exception( "-- Dieser Fehler ist korrekt! --" );
            }
            System.out.println( super.text + ": ok" );
            return RepeatStatus.FINISHED;
        }
    }

    @Bean
    public Step arbeitsStep()
    {
        return new StepBuilder("arbeitsStep", repository).tasklet(new ArbeitsTasklet("ArbeitsStep"),transactionManager).build();

    }

    @Bean
    public Step fehlerbehandlungsStep()
    {
        return new StepBuilder("fehlerbehandlungsStep", repository).tasklet(new PrintTextTasklet("FehlerbehandlungsStep"),transactionManager).build();

    }

    @Bean
    public Step okStep()
    {
        return new StepBuilder("okStep", repository).tasklet(new PrintTextTasklet("OkStep"),transactionManager).build();

    }

    @Bean
    public Step abschliessenderStep()
    {
        return new StepBuilder("abschliessenderStep", repository).tasklet(new PrintTextTasklet("abschliessenderStep"),transactionManager).build();

    }

    /*
    public class MyDecider implements JobExecutionDecider {
    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        String exitCode = stepExecution.getExitStatus().getExitCode();
        if (exitCode.equals(ExitStatus.FAILED.getExitCode())) {
            return new FlowExecutionStatus("FAILED_CUSTOM");
        } else {
            return new FlowExecutionStatus("COMPLETED");
        }
    }
}


    @Bean
    public Flow exampleFlow() {
        Step step1 = stepBuilderFactory.get("step1").tasklet(myTasklet()).build();
        Step step2 = stepBuilderFactory.get("step2").tasklet(myTasklet()).build();

        return new FlowBuilder<SimpleFlow>("exampleFlow")
                .start(step1)
                .next(step2)
                .end();
    }

    @Bean
public Job exampleJob(JobBuilderFactory jobBuilders, StepBuilderFactory stepBuilders) {
    Step taskletStep = stepBuilders.get("taskletStep")
                                   .tasklet(myTasklet())
                                   .build();

    JobExecutionDecider decider = new MyDecider();

    return jobBuilders.get("exampleJob")
                      .start(taskletStep)
                      .next(decider)
                      .on("CONTINUE").to(taskletStep) // Wiederholung des Tasklets bei bestimmter Bedingung
                      .from(decider)
                      .on("COMPLETE").end() // Beendigung des Jobs
                      .build();
}
*/

    @Bean
    public Job meinTaskletJob() throws Exception
    {
        return new JobBuilder("jobAbc", repository)
                .incrementer( new RunIdIncrementer() )
                .start( arbeitsStep() )
                    .on("FAILED" ).to( fehlerbehandlungsStep() )
     //               .next( abschliessenderStep() )
                .from( arbeitsStep() ).on( "*" ).to( okStep() )
                        .next( abschliessenderStep() )
                .end().build();
    }
}

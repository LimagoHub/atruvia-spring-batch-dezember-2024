package de.atruvia.stoppabledelayedretry;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppRunner implements CommandLineRunner {


    private final JobLauncher asyncJobLauncher;
    private final Job job;
    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;

    @Override
    public void run(final String... args) throws Exception {


        var execution = asyncJobLauncher.run(job,
                new JobParametersBuilder()
                        .addString("UUID", UUID.randomUUID().toString())
                        .addString("OK_ODER_FEHLER", "ok")
                .toJobParameters()
        );
        JobExecution je1 = warteBisStatus( execution.getId(), BatchStatus.COMPLETED, 10 );
        printStepExecutions( je1 );

    }

    /** Warte bis die JobExecution den gewuenschten BatchStatus erreicht (oder der Timeout ueberschritten ist) */
    private JobExecution warteBisStatus( Long executionId, BatchStatus stat, int maxWartezeitSek )
    {
        for( int i = 0; i < 5 * maxWartezeitSek; i++ ) {
            JobExecution je = jobExplorer.getJobExecution( executionId );
            if( stat == null || je == null || stat.equals( je.getStatus() ) ) {
                return je;
            }
            try { Thread.sleep( 200 ); } catch( InterruptedException e ) {}
        }
        return jobExplorer.getJobExecution( executionId );
    }

    /** Zeige die StepExecutions, die JobExecution und den Job-ExecutionContext */
    private static void printStepExecutions( JobExecution je )
    {
        for( StepExecution se : je.getStepExecutions() ) {
            String s = se.getExitStatus().getExitDescription();
            if( s != null && s.length() > 0 ) { s = ", ExitDescription = " + s; }
            System.out.println( "StepExecution " + se.getId() + ": CommitCount = " + se.getCommitCount() +
                    ", Status = " + se.getStatus() + ", ExitStatus = " + se.getExitStatus().getExitCode() +
                    ", StepName = " + se.getStepName() + s );
        }
        String s = je.getExitStatus().getExitDescription();
        if( s != null && s.length() > 0 ) { s = ", ExitDescription = " + s; }
        System.out.println( "JobExecution  " + je.getId() + ": JobId = " + je.getJobId() +
                ",       Status = " + je.getStatus() + ", ExitStatus = " + je.getExitStatus().getExitCode() + s );
        ExecutionContext ec = je.getExecutionContext();
        if( ec != null && !ec.isEmpty() ) {
            for( Map.Entry<String, Object> e : ec.entrySet() ) {
                System.out.println( "Job-ExecutionContext: " + e.getKey() + ": " + e.getValue() );
            }
        }
    }
}

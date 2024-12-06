package de.atruvia.stoppabledelayedretry;

import java.util.Set;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.*;
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.*;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.*;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * JobConfiguration fuer:
 * a) "Delayed Retry" nach Fehlschlag,
 * b) "Stoppable" Warte-Tasklet.
 *
 * Mit dem JobParameter OK_ODER_FEHLER kann fuer Testzwecke vorgegeben werden, ob der ArbeitsStep mit OK oder mit Fehler enden soll.
 * Nach einem Fehlschlag im ersten ArbeitsStep ("ersterStep") wird nach einer Wartezeit im WarteStep der ArbeitsStep erneut versucht,
 * maximal ANZ_WIEDERHOL Male.
 * Fuer Testzwecke kann vorgegeben werden, dass nach ANZ_STEP_FEHL_OK-vielen Steps das Ergebnis auf OK gesetzt wird.
 * Die Step-Abfolge sowie der WarteStep koennen durch ein Stopp-Kommando abgebrochen werden.
 * Im OK-Fall werden nach dem "ersterStep" der "zweiterStep", der "okStep" und der "abschliessenderStep" ausgefuehrt.
 * Im Fehlerfall werden der "fehlerbehandlungsStep" und der "abschliessenderStep" ausgefuehrt.
 */
@Configuration
@EnableBatchProcessing
public class StoppableDelayedRetryJobConfiguration
{
    public static final String JOB_NAME         = "jobStoppableDelayedRetry";
    public static final String OK_ODER_FEHLER   = "OK_ODER_FEHLER";
    public static final String ANZ_WIEDERHOL    = "ANZ_WIEDERHOL";
    public static final String ANZ_STEP_FEHL_OK = "ANZ_STEP_FEHL_OK";
    public static final String ERGEBNIS_OK      = "Ergebnis: OK";
    public static final String ERGEBNIS_FEHLER  = "Ergebnis: Fehler";
    public static final String ABBRUCH_WEIL_JOB_BEREITS_LAEUFT     = "Abbruch weil Job bereits laeuft";
    public static final String STOPP_SIGNAL_DURCH_STOPPABLETASKLET = "Stopp-Signal durch StoppableTasklet";

    @Autowired protected JobRepository jobRepository;
    @Autowired protected JobExplorer        jobExplorer;
    @Autowired private  PlatformTransactionManager transactionManager;
    /** Step zur Vermeidung des wiederholten Starts des gleichen Jobs */
    @Bean
    public Step avoidDuplicateRun()
    {

        return new StepBuilder("meinLeerzeilenStep", jobRepository).tasklet(new Tasklet() {
            @Override
            public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) throws Exception {
                String jobName = chunkContext.getStepContext().getJobName();
                Set<JobExecution> jes = jobExplorer.findRunningJobExecutions( jobName );
                if( jes.size() > 1 ) {
                    String exitDescription = ABBRUCH_WEIL_JOB_BEREITS_LAEUFT;
                    System.out.println( "\n !!! " + exitDescription + " !!!" );
                    contribution.setExitStatus( new ExitStatus( ExitStatus.FAILED.getExitCode(), exitDescription ) );
                }
                return RepeatStatus.FINISHED;
            }
        },transactionManager).build();
    }

    /** Tasklet zur Anzeige von Text im Kommandozeilenfenster (und Speicherung im Job-ExecutionContext) */
    public class PrintTextTasklet implements Tasklet
    {
        final String text;

        public PrintTextTasklet( String text ) {
            this.text = text;
        }

        @Override
        public RepeatStatus execute( StepContribution sc, ChunkContext cc ) throws Exception {
            System.out.println( text );
            ExecutionContext ec = cc.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
            Object msg = ec.get( "Msg" );
            msg = (( msg == null ) ? "" : (msg + ", ")) + text;
            ec.put( "Msg", msg );
            return RepeatStatus.FINISHED;
        }
    }

    /** Tasklet mit zwei Funktionen:
     *  a) Anzeige von Text im Kommandozeilenfenster,
     *  b) speichert Text in der ExitDescription des Steps. */
    public class StoreTextAndPrintTextTasklet extends PrintTextTasklet
    {
        final String storeText;

        public StoreTextAndPrintTextTasklet( String printText, String storeText ) {
            super( printText );
            this.storeText = storeText;
        }

        @Override
        public RepeatStatus execute( StepContribution sc, ChunkContext cc ) throws Exception {
            super.execute( sc, cc );
            sc.setExitStatus( sc.getExitStatus().addExitDescription( storeText ) );
            return RepeatStatus.FINISHED;
        }
    }

    /** Tasklet mit mehreren Funktionen:
     *  a) zur Anzeige von Text im Kommandozeilenfenster,
     *  b) nimmt Abbruchssignal per StoppableTasklet.stop() entgegen (funktioniert nicht in jedem Fall korrekt),
     *  c) erfragt Abbruchssignal vom JobExplorer (funktioniert immer, aber benoetigt Datenbankzugriff). */
    public class StoppablePrintTextTasklet extends PrintTextTasklet implements StoppableTasklet
    {
        volatile boolean stopped = false;

        public StoppablePrintTextTasklet( String text ) {
            super( text );
        }

        @Override
        public void stop() {
            stopped = true;
        }

        public void resetStopped() {
            stopped = false;
        }

        public boolean isStopped( StepContribution sc, StepExecution se ) {
            // Achtung: Die StoppableTasklet-Variante wird nicht von jedem JobOperator unterstuetzt,
            // und so wie hier implementiert ist sie nicht thread-save,
            // weshalb doppelte Job-Laeufe ausgeschlossen werden muessen (s.o. avoidDuplicateRun):
            if( stopped ) {
                stopped = false;
                String exitDescription = STOPP_SIGNAL_DURCH_STOPPABLETASKLET;
                System.out.println( " !!! " + exitDescription + " !!!" );
                sc.setExitStatus( sc.getExitStatus().addExitDescription( exitDescription ) );
                return true;
            }
            // Explizite Stopp-Abfrage, funktioniert immer, aber benoetigt DB-Abfrage:
            se = jobExplorer.getStepExecution( se.getJobExecutionId(), se.getId() );
            if( se.getJobExecution().isStopping() || se.isTerminateOnly() ) {
                String exitDescription = "Stopp-Signal durch JobExecution";
                System.out.println( " !!! " + exitDescription + " !!!" );
                sc.setExitStatus( sc.getExitStatus().addExitDescription( exitDescription ) );
                return true;
            }
            return false;
        }
    }

    /** Stoppable Warte-Tasklet: Falls nicht bereits zu viele Schleifen: Nach Wartezeit erneute Schleife */
    public class WarteTasklet extends StoppablePrintTextTasklet
    {
        public WarteTasklet( String text ) {
            super( text );
        }

        @Override
        public RepeatStatus execute( StepContribution sc, ChunkContext cc ) throws Exception {
            super.execute( sc, cc );
            resetStopped();
            StepExecution se = cc.getStepContext().getStepExecution();
            int anzahlStepExecutions = se.getJobExecution().getStepExecutions().size();
            String anzWiederholStrng = se.getJobParameters().getString( ANZ_WIEDERHOL );
            long anzWiederhol = ( anzWiederholStrng != null ) ? Long.parseLong( anzWiederholStrng ) : 5;
            System.out.println( "anzahlStepExecutions: " + anzahlStepExecutions );
            if( anzahlStepExecutions > anzWiederhol ) {
                System.out.println( super.text + ": keine Wiederholung" );
                sc.setExitStatus( ExitStatus.FAILED );
            } else {
                System.out.print( super.text + ": Wiederholung nach Wartezeit " );
                for( int i = 0; i < 10 && !isStopped( sc, se ); i++ ) {
                    System.out.print( ". " );
                    Thread.sleep( 100 );
                }
                System.out.println();
            }
            return RepeatStatus.FINISHED;
        }
    }

    /** Erstes Arbeits-Tasklet */
    public class ErstesTasklet extends PrintTextTasklet
    {
        public ErstesTasklet( String text ) {
            super( text );
        }

        @Override
        public RepeatStatus execute( StepContribution sc, ChunkContext cc ) throws Exception {
            super.execute( sc, cc );
            StepContext stpCtx = cc.getStepContext();
            System.out.println( "\n---- Job: " + stpCtx.getJobName() + ", mit JobParametern: " + stpCtx.getJobParameters() );
            int anzahlStepExecutions  = stpCtx.getStepExecution().getJobExecution().getStepExecutions().size();
            String okOderFehler       = stpCtx.getStepExecution().getJobParameters().getString( OK_ODER_FEHLER );
            String anzStepFehlOkStrng = stpCtx.getStepExecution().getJobParameters().getString( ANZ_STEP_FEHL_OK );
            long anzStepFehlOk        = ( anzStepFehlOkStrng != null ) ? Long.parseLong( anzStepFehlOkStrng ) : 100;
            boolean b1 = ( okOderFehler != null ) ? !"ok".equalsIgnoreCase( okOderFehler ) : (Math.random() < 0.5);
            boolean b2 = anzahlStepExecutions > anzStepFehlOk && anzStepFehlOk > 0;
            if( !b1 ) {
                System.out.println( super.text + ": ok" );
            } else if( b2 ) {
                System.out.println( super.text +
                        ": auf ok gesetzt, weil anzahlStepExecutions(=" + anzahlStepExecutions +
                        ") > anzStepFehlOk(=" + anzStepFehlOk + ")" );
            } else {
                System.out.println( super.text + ": mit Fehler" );
                sc.setExitStatus( ExitStatus.FAILED );
            }
            return RepeatStatus.FINISHED;
        }
    }

    /** Zweites Arbeits-Tasklet */
    public class ZweitesTasklet extends ErstesTasklet
    {
        public ZweitesTasklet( String text ) {
            super( text );
        }
    }

    @Bean
    public Step ersterStep()
    {

        return new StepBuilder("ersterStep", jobRepository).tasklet( new ErstesTasklet( "ErstesTasklet" ),transactionManager).build();
    }

    @Bean
    public Step zweiterStep()
    {

        return new StepBuilder("zweiterStep", jobRepository).tasklet( new ErstesTasklet( "ZweitesTasklet" ),transactionManager).build();
    }

    @Bean
    public Step warteStep()
    {
        return new StepBuilder("warteStep", jobRepository).tasklet( new ErstesTasklet( "WarteTasklet" ),transactionManager).build();

    }

    @Bean
    public Step fehlerbehandlungsStep()
    {

        return new StepBuilder("fehlerbehandlungsStep", jobRepository).tasklet( new StoreTextAndPrintTextTasklet( "FehlerbehandlungsStep", ERGEBNIS_FEHLER ),transactionManager).build();
    }

    @Bean
    public Step okStep()
    {

        return new StepBuilder("okStep", jobRepository).tasklet( new StoreTextAndPrintTextTasklet( "OkStep", ERGEBNIS_OK ),transactionManager).build();

    }

    @Bean
    public Step abschliessenderStep()
    {
        return new StepBuilder("abschliessenderStep", jobRepository).tasklet( new PrintTextTasklet( "AbschliessenderStep" ) ,transactionManager).build();

    }

    /** Job:
     *  a) Stoppable:     Job kann unterbrochen werden, auch innerhalb des Warte-Tasklets;
     *  b) Delayed Retry: Falls der erste Step fehlschlaegt, wird er nach einer Wartezeit einige Male wiederholt ausgefuehrt */
    @Bean
    public Job meinStoppableDelayedRetryJob()
    {
        return new JobBuilder( JOB_NAME , jobRepository).incrementer( new RunIdIncrementer() ).listener( meinJobExecutionListener() )
                .start( avoidDuplicateRun() )
                .next( ersterStep() )
                .on( ExitStatus.FAILED.getExitCode() )
                .to( warteStep() ).on( ExitStatus.FAILED.getExitCode() ).to( fehlerbehandlungsStep() ).next( abschliessenderStep() )
                .from( warteStep() ).on( "*" ).to( ersterStep() )
                .from( ersterStep() )
                .on( "*" )
                .to( zweiterStep() ).on( ExitStatus.FAILED.getExitCode() ).to( fehlerbehandlungsStep() ).next( abschliessenderStep() )
                .from( zweiterStep() ).on( "*" ).to( okStep() ).next( abschliessenderStep() )
                .end().build();
    }

    /** JobExecutionListener:
     *  Sammelt alle ExitDescription aus den Steps und speichert sie in der ExitDescription der JobExecution */
    @Bean
    public JobExecutionListener meinJobExecutionListener()
    {
        return new JobExecutionListener()
        {
            @Override
            public void beforeJob( JobExecution je ) {}

            @Override
            public void afterJob( JobExecution je ) {
                for( StepExecution se : je.getStepExecutions() ) {
                    String sesd = se.getExitStatus().getExitDescription();
                    if( sesd != null && sesd.length() > 0 ) {
                        String jesd = je.getExitStatus().getExitDescription();
                        String[] ss = sesd.split( ";" );
                        for( String s : ss ) {
                            if( jesd == null || !jesd.contains( s.trim() ) ) {
                                je.setExitStatus( je.getExitStatus().addExitDescription( s.trim() ) );
                                jesd = je.getExitStatus().getExitDescription();
                            }
                        }
                    }
                }
            }
        };
    }

    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher ();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
        return jobLauncher;
    }

    /*@Bean
    @Qualifier("MyTaskExecutor")
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor asyncTaskExecutor = new SimpleAsyncTaskExecutor();
        asyncTaskExecutor.setConcurrencyLimit(5); // Gleichzeitig maximal 5 Jobs
        return asyncTaskExecutor;
    }*/


}
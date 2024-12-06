package de.atruvia.taskdemo;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

/*

@Autowired
private JobExplorer jobExplorer;

public boolean isJobRunning(String jobName) {
    // Holen Sie alle laufenden Job-Instanzen für einen bestimmten Job
    List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, Integer.MAX_VALUE);

    for (JobInstance instance : instances) {
        // Holen Sie alle Ausführungen für jede Instanz
        List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(instance);
        for (JobExecution jobExecution : jobExecutions) {
            // Überprüfen Sie, ob der Status der Ausführung anzeigt, dass der Job noch läuft
            if (jobExecution.isRunning() || jobExecution.getStatus() == BatchStatus.STARTED || jobExecution.getStatus() == BatchStatus.STARTING) {
                return true;
            }
        }
    }
    return false;
}
 */


//@Component
@RequiredArgsConstructor
public class AppRunner2 implements CommandLineRunner {


    private final JobLauncher jobLauncher;
    private final Job job;

    @Override
    public void run(final String... args) throws Exception {

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());


        System.out.println( "\nJoblauf mit fehlerhaftem Job-Parameter (Text statt Zahl):" );
        JobExecution je = jobLauncher.run( job, new JobParametersBuilder().addString(
                TaskletJobConfiguration.ANZAHLSTEPS_KEY, "xx" ).toJobParameters() );
        // Ueberpruefe Job-Ergebnis:
       for( StepExecution se : je.getStepExecutions() ) {
            System.out.println( "StepExecution " + se.getId() + ": StepName = " + se.getStepName() +
                    ", CommitCount = " + se.getCommitCount() +
                    ", BatchStatus = " + se.getStatus() + ", ExitStatus = " + se.getExitStatus().getExitCode() );
        }
    }
}

package de.atruvia.testdemo;

import de.atruvia.testdemo.entity.Person;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.internal.matchers.Equals;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.File;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@SpringBootTest
@SpringBatchTest
@Sql({"/create.sql", "/insert.sql"})
public class TestTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    private JdbcTemplate jdbcTemplate;

    @Autowired private JobExplorer jobExplorer;
    @Autowired
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @MockitoBean
    private ItemReader<Person> flatFileItemReaderMock;

    @Test
    void firsttest() throws Exception {
        System.out.println(jdbcTemplate.getDataSource().getConnection());
    }

    @Test
    void testJob(@Autowired  @Qualifier("chunk") Job job) throws Exception {

        Mockito.when(flatFileItemReaderMock.read())
                .thenReturn(Person.builder().firstName("Max").lastName("Mustermann").age(18).build())
                .thenReturn(Person.builder().firstName("Erika").lastName("Mustermann").age(21).build())
                .thenReturn(null);
        //System.out.println(flatFileItemReaderMock.read());

        JobParameters param = new JobParametersBuilder().addString("file.output", "output.json").toJobParameters();

        this.jobLauncherTestUtils.setJob(job);
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(param);
        printStepExecutions(jobExecution);
        assertThat(new File("input.json")).hasSameTextualContentAs(new File("output.json"));
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");


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

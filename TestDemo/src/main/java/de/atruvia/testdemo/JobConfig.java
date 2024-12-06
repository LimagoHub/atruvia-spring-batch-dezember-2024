package de.atruvia.testdemo;

import de.atruvia.testdemo.entity.Person;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.item.json.JsonFileItemWriter;
import org.springframework.batch.item.json.builder.JsonFileItemWriterBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.io.IOException;

@Configuration
public class JobConfig {
    @Autowired
    private JobRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Bean
    public Step meinEinsStep()
    {
        return new StepBuilder("meinLeerzeilenStep", repository).tasklet((contribution, chunkContext) -> {
            System.out.println( "Eins" );
            return RepeatStatus.FINISHED;
        },transactionManager).build();

    }

    @Bean
    public Step meinZweiStep()
    {
        return new StepBuilder("meinLeerzeilenStep", repository).tasklet((contribution, chunkContext) -> {
            System.out.println( "Zwei" );
            return RepeatStatus.FINISHED;
        },transactionManager).build();

    }

    @Bean
    @Qualifier("tasklet")
    public Job meinTaskletJob(final JobRepository repository, final PlatformTransactionManager transactionManager) throws Exception
    {
        return new JobBuilder("meinTaskletJob", repository).incrementer( new RunIdIncrementer() )
                .start( meinEinsStep())
                .next(  meinZweiStep() )
                .build();
    }

    // -----------------------------------------------------------------------------
    @Bean
    @StepScope
    @Profile("production")
    public FlatFileItemReader<Person> reader() {
        var flatFileItemReader = new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")

                .resource(new ClassPathResource("sample-data.csv"))
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
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<Person> writer(final DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public Step step1(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      ItemReader<Person> reader,
                      PersonItemProcessor processor
                      //, JdbcBatchItemWriter<Person> writer
            , JsonFileItemWriter<Person> writer
    ) {

        return new StepBuilder("step1", jobRepository)

                .<Person, Person>chunk(10, transactionManager)
                .reader(reader)
                .faultTolerant()
                .skipLimit(2)
                .skip(FlatFileParseException.class)
                .noSkip(FileNotFoundException.class)
                //.skipPolicy(createSkipPolicy())
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @Qualifier("chunk")
    public Job importUserJob(
            JobRepository jobRepository,
            JobCompletionNotificationListener listener, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step1)
                .end()
                .build();
    }

    @Bean
    @StepScope

    public JsonFileItemWriter<Person> jsonItemWriter(
            @Value("#{jobParameters['file.output']}") String output) throws IOException {
        JsonFileItemWriterBuilder<Person> builder = new JsonFileItemWriterBuilder<>();
        JacksonJsonObjectMarshaller<Person> marshaller = new JacksonJsonObjectMarshaller<>();
        return builder
                .name("bookItemWriter")
                .jsonObjectMarshaller(marshaller)
                .resource(new FileSystemResource(output))
                .build();
    }
}

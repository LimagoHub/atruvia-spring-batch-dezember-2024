package de.atruvia.first.batchprocessing;


import de.atruvia.first.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class BatchConfig {

    @Bean
    @StepScope
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
                      FlatFileItemReader<Person> reader,
                      PersonItemProcessor processor,
                      JdbcBatchItemWriter<Person> writer) {

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
    public Job importUserJob(JobRepository jobRepository, JobCompletionNotificationListener listener, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step1)
                .end()
                .build();
    }

    @Bean
    SkipPolicy createSkipPolicy() {

        return new SkipPolicy() {

            private final Logger logger = LoggerFactory.getLogger("badRecordLogger");

            @Override
            public boolean shouldSkip(final Throwable exception, final long skipCount) throws SkipLimitExceededException {

                if (exception instanceof FileNotFoundException) {
                    return false;
                } else if (exception instanceof FlatFileParseException && skipCount <=2) {
                    FlatFileParseException ffpe = (FlatFileParseException) exception;

                    StringBuilder errorMessage = new StringBuilder();
                    errorMessage.append("An error occured while processing the ");
                    errorMessage.append(ffpe.getLineNumber());
                    errorMessage.append(" line of the file '");

                    Pattern pattern = Pattern.compile(".*(\\W\\w+\\.csv).*");
                    Matcher matcher = pattern.matcher(ffpe.toString());
                    if (matcher.matches())
                        errorMessage.append(matcher.group(1));
                    else
                        errorMessage.append("unknown");
                    errorMessage.append("'. Below was the faulty input.");
                    errorMessage.append("\n");
                    errorMessage.append(ffpe.getInput());
                    errorMessage.append("\n");
                    logger.error("{}", errorMessage.toString());
                    return true;
                } else {
                    return false;
                }
            }


        };

    }
}
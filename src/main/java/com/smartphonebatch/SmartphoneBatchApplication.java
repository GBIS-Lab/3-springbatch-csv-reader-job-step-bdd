package com.smartphonebatch;

import com.smartphonebatch.model.Smartphone;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.List;

@SpringBootApplication
@EnableBatchProcessing
public class SmartphoneBatchApplication {

//	public static void main(String[] args) {
//		SpringApplication.run(SmartphoneBatchApplication.class, args);
//	}

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(SmartphoneBatchApplication.class);
		ConfigurableApplicationContext context = app.run(args);

		JobLauncher jobLauncher = context.getBean(JobLauncher.class);
		Job job = context.getBean("job", Job.class);

		try {
			System.out.println("🔄 Lancement du job batch...");
			jobLauncher.run(job, new JobParametersBuilder()
					.addLong("startAt", System.currentTimeMillis())
					.toJobParameters());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Bean
	public FlatFileItemReader<Smartphone> reader() throws IOException {
		FlatFileItemReader<Smartphone> reader = new FlatFileItemReader<>();

		System.out.println("Initialisation du Reader...");

		// Vérification du chemin du fichier avant de le charger
		// Chargement du fichier via le classpath
		ClassPathResource classpathresource = new ClassPathResource("smartphones.csv");
		String filePath = classpathresource.getFile().getAbsolutePath();
		reader.setResource(classpathresource);

		//String filePath = "C:/Users/<user>/IdeaProjects/smartphonebatch2/src/main/resources/smartphones.csv";
		System.out.println("Chemin du fichier : " + filePath);

		File file = new File(filePath);

		if (file.exists() && file.isFile()) {
			System.out.println("Le fichier existe à l'endroit indiqué : " + filePath);
			List<String> lignes = java.nio.file.Files.readAllLines(file.toPath());
			lignes.forEach(System.out::println);  // Affichage de chaque ligne
		} else {
			System.err.println("Le fichier n'a pas été trouvé à l'endroit suivant : " + filePath);
		}

		// Charger le fichier en utilisant FileSystemResource
		reader.setResource(new FileSystemResource(filePath));

		System.out.println("Reader fichier smartphones.csv chargé à partir du chemin : " + filePath);

		//reader.setResource(new ClassPathResource("smartphones.csv"));



		reader.setLinesToSkip(1);

		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setDelimiter(";");
		tokenizer.setNames("marque", "modele", "os", "anneeSortie", "tailleEcran", "prix");

		BeanWrapperFieldSetMapper<Smartphone> mapper = new BeanWrapperFieldSetMapper<>();
		mapper.setTargetType(Smartphone.class);

		DefaultLineMapper<Smartphone> lineMapper = new DefaultLineMapper<>();
		lineMapper.setLineTokenizer(tokenizer);
		lineMapper.setFieldSetMapper(mapper);

		reader.setLineMapper(lineMapper);
		return reader;
	}

	@Bean
	public ItemProcessor<Smartphone, Smartphone> processor() {
		return smartphone -> {
			System.out.println("Smartphone en cours de traitement : " + smartphone.getMarque() + " / " + smartphone.getModele() + "/" + smartphone.getOs()  + "/" + smartphone.getAnneeSortie() + "/" + smartphone.getTailleEcran() + "/" + smartphone.getPrix());
			if (smartphone.getAnneeSortie() < 2023) {
				smartphone.setPrix(smartphone.getPrix() * 0.9);
			}
			System.out.println("Smartphone traité : " + smartphone.getMarque() + " / " + smartphone.getModele() + "/" + smartphone.getOs()  + "/" + smartphone.getAnneeSortie() + "/" + smartphone.getTailleEcran() + "/" + smartphone.getPrix());
			return smartphone;
		};
	}

	@Bean
	public DataSource dataSource() {
		return DataSourceBuilder.create()
				.url("jdbc:h2:file:./data/testdb")  // Assure-toi que l'URL correspond à ta base de données
				.driverClassName("org.h2.Driver")
				.username("sa")
				.password("password")
				.build();
	}
	
	@Bean
	public JdbcBatchItemWriter<Smartphone> smartphoneWriter(DataSource dataSource) {
		JdbcBatchItemWriter<Smartphone> writer = new JdbcBatchItemWriter<>();
		writer.setDataSource(dataSource);
		writer.setSql("""
        INSERT INTO smartphones (marque, modele, os, annee_sortie, taille_ecran, prix)
        VALUES (:marque, :modele, :os, :anneeSortie, :tailleEcran, :prix)
    """);
		writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
		writer.afterPropertiesSet();
		return writer;
	}

	@Bean
	public Step step(JobRepository jobRepository, PlatformTransactionManager transactionManager, DataSource dataSource) throws IOException {
		return new StepBuilder("step1", jobRepository)
				.<Smartphone, Smartphone>chunk(10, transactionManager)
				.reader(reader())
				.processor(processor())
				//.writer(items -> {
				//	System.out.println("== Résultat du traitement ==");
				//	items.forEach(System.out::println);
				//})
				.writer(smartphoneWriter(dataSource)) // <-- Ici le JdbcBatchItemWriter
				.build();
	}

	@Bean
	public Job job(JobRepository jobRepository,
				   Step step) {
		return new JobBuilder("smartphoneJob", jobRepository)
				.start(step)
				.build();
	}
}
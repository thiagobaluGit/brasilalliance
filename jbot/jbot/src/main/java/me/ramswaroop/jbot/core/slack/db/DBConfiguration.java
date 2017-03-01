package me.ramswaroop.jbot.core.slack.db;

import java.net.URI;
import java.net.URISyntaxException;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories
@EnableTransactionManagement
public class DBConfiguration {

	@Bean
	public DataSource dataSource() throws URISyntaxException {
		System.out.println("!!!!!!!!!!!!!!Checking Envirnoment!!!!!!!!!!!!!!!!!");

		String url1 = System.getenv("dbUrl");
		if (null == url1 || url1.isEmpty()) {
			System.out.println("URL VAZIA! Tentando pegar da segunda variavel!");
			url1 = System.getenv("DATABASE_URL");
		}
		System.out.println("URl retornada: " + url1);

		URI dbUri = new URI(url1);

		String username = dbUri.getUserInfo().split(":")[0];
		String password = dbUri.getUserInfo().split(":")[1];
		// String dbUrl = "jdbc:postgresql://" + dbUri.getHost() +
		// dbUri.getPath();

		final PGSimpleDataSource basicDataSource = new PGSimpleDataSource();
		basicDataSource.setServerName(dbUri.getHost() + dbUri.getPath());
		basicDataSource.setUser(username);
		basicDataSource.setPassword(password);

		return basicDataSource;
	}

	// @Bean
	// public Properties hibernateProperties(){
	// final Properties properties = new Properties();
	//
	// properties.put( "hibernate.dialect",
	// "org.hibernate.dialect.PostgreSQLDialect" );
	// properties.put( "hibernate.connection.driver_class",
	// "org.postgresql.Driver" );
	// properties.put( "hibernate.hbm2ddl.auto", "update" );
	//
	// return properties;
	// }
	//
	// @Bean
	// public EntityManagerFactory entityManagerFactory( DataSource dataSource,
	// Properties hibernateProperties ){
	// final LocalContainerEntityManagerFactoryBean em = new
	// LocalContainerEntityManagerFactoryBean();
	// em.setDataSource( dataSource );
	// em.setPackagesToScan( "me.ramswaroop.jbot" );
	// em.setJpaVendorAdapter( new HibernateJpaVendorAdapter() );
	// em.setJpaProperties( hibernateProperties );
	// em.setPersistenceUnitName( "me.ramswaroop.jbot.core.slack.db" );
	// em.setPersistenceProviderClass(HibernatePersistenceProvider.class);
	// em.afterPropertiesSet();
	//
	// return em.getObject();
	// }

	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() throws URISyntaxException {

		HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		vendorAdapter.setGenerateDdl(true);

		LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		factory.setJpaVendorAdapter(vendorAdapter);
		factory.setPackagesToScan("me.ramswaroop.jbot");
		factory.setDataSource(dataSource());
		return factory;
	}

	@Bean
	public PlatformTransactionManager transactionManager() throws URISyntaxException {

		JpaTransactionManager txManager = new JpaTransactionManager();
		txManager.setEntityManagerFactory(entityManagerFactory().getObject());
		return txManager;
	}

}

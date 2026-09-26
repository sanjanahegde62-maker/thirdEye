
package com.thirdeye.backend;

import com.thirdeye.backend.entity.Project;
import com.thirdeye.backend.repository.ProjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initializeProjects(ProjectRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new Project(
                        "DayFlow",
                        "HRMS website",
                        "https://github.com/sanjanahegde62-maker/DayFlow.git",
                        "ACTIVE"
                ));

                repository.save(new Project(
                        "Lost & Found",
                        "AI-powered lost and found portal",
                        "https://github.com/sanjanahegde62-maker/lost-and-found.git",
                        "ACTIVE"
                ));


            }
        };
    }
}
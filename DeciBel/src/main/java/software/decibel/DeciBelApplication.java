package software.decibel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DeciBelApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeciBelApplication.class, args);
    }

}

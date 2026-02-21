package Software.DeciBel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration;

@SpringBootApplication(exclude = CassandraAutoConfiguration.class)
public class DeciBelApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeciBelApplication.class, args);
    }

}

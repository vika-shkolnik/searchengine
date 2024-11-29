package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Random;

@Component
@ConfigurationProperties(prefix = "user-agent-settings")
@Data
public class UserAgents {

    private List<String> users;
    private String referrer;
    private final Random random = new Random();

    public String getRandomUser() {
        return users.get(random.nextInt(users.size()));
    }

}


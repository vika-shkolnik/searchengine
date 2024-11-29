package searchengine.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class Site {
    private String url;
    private String name;
}

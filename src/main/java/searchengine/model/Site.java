package searchengine.model;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "site")
@Data
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')")
    private Status status;

    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255)")
    private String url;

    @Column(name = "name", columnDefinition = "VARCHAR(255)")
    private String name;

    public Site() {

    }

    public Site(Status status, Date statusTime, String url, String name) {
        this.status = status;
        this.statusTime = statusTime;
        this.lastError = "";
        this.url = url;
        this.name = name;
    }

}

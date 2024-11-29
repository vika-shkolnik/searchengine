package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer>{

    Site findSiteByUrl (String url);

    @Query(value = "SELECT * FROM site WHERE url = :url", nativeQuery = true)
    Site findStatusByUrl (String url);


}

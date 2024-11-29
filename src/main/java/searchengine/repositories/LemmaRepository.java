package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;
import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    Lemma findByLemmaAndSite(String lemma, Site site);

    Integer countBySite(Site site);

    @Query("SELECT l FROM Lemma AS l WHERE l.frequency < 300 " + "AND l.lemma IN (:lemmas) AND l.site =:site")
    List<Lemma> selectLemmasBySite(Set<String> lemmas, Site site);
}

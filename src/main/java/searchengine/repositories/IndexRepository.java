package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    @Query("SELECT e FROM Index AS e WHERE e.page =:page")
    List<Index> findByPageIn(Page page);

    @Query("SELECT i FROM Index AS i WHERE i.lemma =:lemma")
    List<Index> findByLemma(Lemma lemma);

}

package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer>{
    @Query(value = "select i from Index i join Page p " +
            "on p.site = :site and i.page = p")
    List<Index> findAllBySite(@Param("site") Site site);
}

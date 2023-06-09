package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.Response;
import searchengine.model.Page;
import searchengine.model.Site;

import java.io.IOException;

public interface IndexingService {
    ResponseEntity<Response> indexingAll();
    ResponseEntity<Response> stopIndexing();

    ResponseEntity<Response> indexPage(String url) throws IOException;
    void createLemmasAndIndices(Site site, Page page) throws IOException;
}

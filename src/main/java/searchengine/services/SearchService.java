package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.Response;
import searchengine.dto.search.SearchResponse;

import java.io.IOException;

public interface SearchService {
    ResponseEntity<? extends Response> allSitesSearch(String searchText, String url, int offset, int limit) throws IOException;
    ResponseEntity<? extends Response> siteSearch(String searchText, String url, int offset, int limit) throws IOException;
}

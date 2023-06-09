package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.Response;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;
import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        return indexingService.indexingAll();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        return indexingService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Response> indexPage(@RequestParam(required = false) String url) throws IOException {
        return indexingService.indexPage(url);
    }
    @GetMapping("/search")
    public ResponseEntity<? extends Response> search(@RequestParam(name = "query", required = false, defaultValue = "")String request,
                           @RequestParam(name = "site", required = false, defaultValue = "") String site,
                           @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
                           @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) throws IOException {
        return searchService.allSitesSearch(request, site, offset, limit);

    }
}

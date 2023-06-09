package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteUrlName;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteUrlName> sitesList = sites.getSites();
        for(int i = 0; i < sitesList.size(); i++) {
            SiteUrlName siteUrlName = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteUrlName.getName());
            item.setUrl(siteUrlName.getUrl());
            Optional<Site> optionalSite = siteRepository.findByUrl(siteUrlName.getUrl());
            int pages = optionalSite.isEmpty() ? 0 : pageRepository.countBySite(optionalSite.get());
            int lemmas = optionalSite.isEmpty()? 0 : lemmaRepository.findAllBySite(optionalSite.get()).size();
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(optionalSite.isEmpty()? "" : optionalSite.get().getType());
            item.setError(optionalSite.isEmpty()? "" : optionalSite.get().getLastError());
            item.setStatusTime(optionalSite.isEmpty()? 0 : (optionalSite.get().getStatusTime().toEpochSecond(ZoneOffset.UTC) - 3 * 3600) * 1000);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}

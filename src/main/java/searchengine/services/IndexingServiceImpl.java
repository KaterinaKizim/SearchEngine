package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SiteUrlName;
import searchengine.config.SitesList;
import searchengine.dto.ErrorResponse;
import searchengine.dto.Response;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.*;
import searchengine.utilities.LemmaFinder;
import searchengine.utilities.LinkExecutor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    public final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private static ExecutorService executorService;
    public static final ConcurrentHashMap<String, Site> sitesInProcessingIndexing = new ConcurrentHashMap<>();
    public volatile boolean stoppedByUser = false;

    @Override
    public ResponseEntity<Response> indexingAll() {

        if (!sitesInProcessingIndexing.isEmpty()) {
            return new ResponseEntity<>(new ErrorResponse("Индексация уже запущена"), HttpStatus.BAD_REQUEST);
        }
        stoppedByUser = false;
        indexRepository.deleteAll();
        siteRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        LinkExecutor.stoppedByUser = false;
        createSites();
        createPages();
        return new ResponseEntity<>(new Response(), HttpStatus.OK);
    }

    public void createSites() {
        List<SiteUrlName> siteUrlNameList = sites.getSites();
        sitesInProcessingIndexing.clear();
        LinkExecutor.allLinks.clear();
        for (SiteUrlName siteUrlName : siteUrlNameList) {
            Site site = createSite(siteUrlName);
            sitesInProcessingIndexing.put(site.getUrl(), site);
        }
    }

    public Site createSite(SiteUrlName siteUrlName) {
        Optional<Site> indexingSite = siteRepository.findByUrl(siteUrlName.getUrl());
        if (!indexingSite.isEmpty()) {
            indexingSite.get().setType(Site.Type.REMOVING.name());
            siteRepository.saveAndFlush(indexingSite.get());
            siteRepository.deleteByType(Site.Type.REMOVING.name());
        }
        Site site = new Site();
        site.setName(siteUrlName.getName());
        site.setUrl(siteUrlName.getUrl());
        site.setStatusTime(LocalDateTime.now());
        site.setType(Site.Type.INDEXING.name());
        siteRepository.saveAndFlush(site);
        return site;
    }

    public void createPages() {
        for (Site site : sitesInProcessingIndexing.values()) {
            try {
                synchronized (Executors.class) {
                    if (executorService == null) {
                        executorService = Executors.newCachedThreadPool();
                    }
                }
                LinkExecutor linkExecutor = new LinkExecutor(siteRepository, pageRepository, site.getUrl(), site, this);
                executorService.execute(linkExecutor);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public ResponseEntity<Response> stopIndexing() {
        if (!sitesInProcessingIndexing.isEmpty()) {
            LinkExecutor.stoppedByUser = true;
            stoppedByUser = true;
            sitesInProcessingIndexing.clear();
            return new ResponseEntity<>(new Response(), HttpStatus.OK);
        }
        return new ResponseEntity<>(new ErrorResponse("Индексация не запущена"), HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<Response> indexPage(String pageUrl) throws IOException {
        List<ResponseEntity<Response>> checkPage = checkPage(pageUrl);
        if (!checkPage(pageUrl).isEmpty()) return checkPage.get(0);
        URL url = null;
        try {
            url = new URL(pageUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String home = Objects.requireNonNull(url).getProtocol() + "://" + url.getHost();
        Site site = siteRepository.findByUrl(home).orElse(null);
        if (site == null) site = createSite(sites.findSiteUrlNameByUrl(home));
        deletePageIfExist(site, pageUrl);
        Page page = createSinglePage(site, pageUrl);
        createLemmasAndIndices(site, page);
        site.setLastError("Проведена индексация страницы " + pageUrl);
        site.setType(Site.Type.INDEXED.name());
        siteRepository.saveAndFlush(site);
        return new ResponseEntity<>(new Response(), HttpStatus.OK);
    }

    private Page createSinglePage(Site site, String pageUrl) {
        Page page = new Page(site, pageUrl, 200, "");
        site.getPages().add(page);
        Document document;
        try {
            Connection.Response response = Jsoup.connect(pageUrl).userAgent("Mozilla/5.0").execute();
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                page.setCode(response.statusCode());
            }
            document = Jsoup.connect(pageUrl).ignoreContentType(true).userAgent("Mozilla/5.0").get();
            page.setContent(document.outerHtml());
        } catch (Exception e) {
            e.printStackTrace();
        }
        pageRepository.saveAndFlush(page);
        siteRepository.saveAndFlush(site);
        return page;
    }

    private void deletePageIfExist(Site site, String pageUrl) {
        List<Page> list = pageRepository.findAllBySiteAndPathAndCode(site, pageUrl, 200);
        if (!list.isEmpty()) {
            for (Page p : list) {
                pageRepository.delete(p);
                deleteIndexDecreaseFrequency(site, pageUrl);
            }
        }
    }

    private void deleteIndexDecreaseFrequency(Site site, String pageUrl) {
        List<Index> indexList = indexRepository.findAllBySite(site);
        for (Index i : indexList) {
            if (i.getPage().getPath().equals(pageUrl)) {
                Lemma lemmaToDecrease = i.getLemma();
                int frequencyToDecrease = i.getRank();
                lemmaToDecrease.setFrequency(lemmaToDecrease.getFrequency() - frequencyToDecrease);
                indexRepository.delete(i);
                lemmaRepository.saveAndFlush(lemmaToDecrease);
            }
        }
    }

    private List<ResponseEntity<Response>> checkPage(String pageUrl) {
        List<ResponseEntity<Response>> result = new ArrayList<>();
        URL url;
        try {
            url = new URL(pageUrl);
        } catch (MalformedURLException e) {
            result.add(new ResponseEntity<>(new ErrorResponse("Страница не найдена"), HttpStatus.BAD_REQUEST));
            return result;
        }
        String home = url.getProtocol() + "://" + url.getHost();
        if (sitesInProcessingIndexing.contains(home)) {
            result.add(new ResponseEntity<>(new ErrorResponse("Страница уже индексируется"), HttpStatus.BAD_REQUEST));
            return result;
        }
        boolean inApplicarionSettings = false;
        for (SiteUrlName siteName : sites.getSites()) {
            if (siteName.getUrl().equals(home)) {
                inApplicarionSettings = true;
            }
        }
        if (!inApplicarionSettings) {
            result.add(new ResponseEntity<>(new ErrorResponse("Данная страница находится за пределами сайтов, \n" +
                    "указанных в конфигурационном файле\n"), HttpStatus.BAD_REQUEST));
            return result;
        }
        return result;
    }

    public void createLemmasAndIndices(Site site, Page page) throws IOException {
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        Map<String, Integer> mapLemmasOnPage = lemmaFinder.collectLemmas(page.getContent());
        List<Lemma> lemmaList = lemmaRepository.findAllBySite(site);
        Map<String, Lemma> lemmaMap = new HashMap<>();
        for (Lemma lemma : lemmaList) {
            lemmaMap.put(lemma.getLemma(), lemma);
        }
        List<Index> indexList = indexRepository.findAllBySite(site);
        Map<Integer, Index> indices = new HashMap<>();
        for (Index index : indexList) {
            indices.put(index.hashCode(), index);
        }
        for (String lemmWord : mapLemmasOnPage.keySet()) {
            Lemma lemma;
            if (lemmaMap.containsKey(lemmWord)) {
                lemmaMap.get(lemmWord).setFrequency(lemmaMap.get(lemmWord).getFrequency() + 1);
                lemma = lemmaMap.get(lemmWord);
            } else {
                lemma = new Lemma(lemmWord, 1, site);
                lemmaMap.put(lemmWord, lemma);
                site.getLemmas().add(lemma);
            }
            lemmaMap.put(lemmWord, lemma);
            indexList.add(new Index(page,lemma, mapLemmasOnPage.get(lemmWord)));
        }
        lemmaRepository.saveAllAndFlush(lemmaMap.values());
        indexRepository.saveAllAndFlush(indexList);
        siteRepository.saveAndFlush(site);
    }
}

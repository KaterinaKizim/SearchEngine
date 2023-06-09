package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.ErrorResponse;
import searchengine.dto.Response;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResponseData;
import searchengine.exceptions.SearchException;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.*;
import searchengine.utilities.LemmaFinder;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;


    @Override
    public ResponseEntity<? extends Response> allSitesSearch(String searchText, String url, int offset, int limit) throws IOException {
        if (searchText.isEmpty())
            return new ResponseEntity<>(new ErrorResponse("Задан пустой поисковый запрос"), HttpStatus.BAD_REQUEST);
        Optional<Site> siteOptional = siteRepository.findByUrl(url);
        if (!url.isEmpty()) {
            if (siteOptional.isEmpty()) {
                return new ResponseEntity<>(new ErrorResponse("Сайт не найден в базе данных"), HttpStatus.BAD_REQUEST);
            } else {
                return siteSearch(searchText, url, offset, limit);
            }
        } else {
            List<SearchResponseData> searchResponseDataList = new ArrayList<>();
            for (Site site : siteRepository.findAllByType(Site.Type.INDEXED.name())) {
                searchResponseDataList.addAll(siteSearch(searchText, site.getUrl(), offset, limit).getBody().getData());
            }
            return new ResponseEntity<>(new SearchResponse(searchResponseDataList, searchResponseDataList.size()), HttpStatus.OK);
        }
    }

    @Override
    public ResponseEntity<SearchResponse> siteSearch(String searchText, String url, int offset, int limit) throws IOException {
        List<Lemma> lemmasFromRequest = null;
        try {
            lemmasFromRequest = getLemmasFromRequestInDB(searchText, url);
        } catch (SearchException e) {
            lemmasFromRequest = new ArrayList<>();
        }
        List<Index> indexesFromRequest = getIndicesFromRequest(lemmasFromRequest, url);
        List<Index> indexPageList = new ArrayList<>();
        for(Lemma l : lemmasFromRequest) {
            List<Index> tempIndex = selectNextLemmaIndices(l, indexesFromRequest, indexPageList);
            if (!tempIndex.isEmpty()) {
                indexPageList = tempIndex;
            }
        }
        List<Page> pageList = new ArrayList<>();
        for (Index i : indexPageList) {
            pageList.add(i.getPage());
        }

        HashMap<Page, Float> pageAbsRelevance = getPageAbsRelevance(pageList, indexPageList);
        List<SearchResponseData> searchResponseDataList = getSearchResponseData(pageAbsRelevance, indexPageList);
        return new ResponseEntity<>(new SearchResponse(searchResponseDataList, searchResponseDataList.size()), HttpStatus.OK);
    }

    private List<Index> selectNextLemmaIndices(Lemma l, List<Index> indexesFromRequest, List<Index> indexPageList) {
        List<Index> tempIndex = new ArrayList<>();
        for (Index i : indexesFromRequest) {
            if (i.getLemma().getLemma().equals(l.getLemma()) && indexPageList.isEmpty()) {
                tempIndex.add(i);
            } else if (i.getLemma().getLemma().equals(l.getLemma()) && indexPageList.contains(i)) {
                tempIndex.add(i);
            }
        }
        return tempIndex;
    }

    private List<Index> getIndicesFromRequest(List<Lemma> lemmasFromRequest, String url) {
        List<Index> indicesFromRequest = new ArrayList<>();
        Optional<Site> siteOptional = siteRepository.findByUrl(url);
        if (siteOptional.isPresent()) {
            Site site = siteOptional.get();
            List<Index> indexList = indexRepository.findAllBySite(site);
            for (Lemma l : lemmasFromRequest) {
                indicesFromRequest.addAll(getIndicesByLemma(l, indexList));
            }
        }
        return indicesFromRequest;
    }

    private List<Index> getIndicesByLemma(Lemma l, List<Index> indexList) {
        List<Index> result = new ArrayList<>();
        for (Index i : indexList) {
            if (i.getLemma().getLemma().equals(l.getLemma())) {
                result.add(i);
            }
        }
        return result;
    }


    private List<Lemma> getLemmasFromRequestInDB(String searchText, String url) throws IOException {
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        List<Lemma> lemmasFromRequest = new ArrayList<>();
        Site site = siteRepository.findByUrl(url).get();
        Map<String, Integer> requestLemmas = lemmaFinder.collectLemmas(searchText);
        List<Lemma> listOfLemmasOnSite = lemmaRepository.findAllBySite(site);
        for (String lemma : requestLemmas.keySet()) {
            lemmasFromRequest.add(findRequestLemmaInBase(lemma, listOfLemmasOnSite));
        }
        lemmasFromRequest.sort((o1, o2) -> o1.getFrequency() - o2.getFrequency());
        return lemmasFromRequest;
    }

    private Lemma findRequestLemmaInBase(String lemma, List<Lemma> listOfLemmasOnSite) {
        for (Lemma lemmaOnSite : listOfLemmasOnSite) {
            if (lemmaOnSite.getLemma().equals(lemma)) {
                return lemmaOnSite;
            }
        }
        throw new SearchException("Леммы в базе не найдены");
    }

    private List<SearchResponseData> getSearchResponseData(HashMap<Page, Float> pageAbsRelevance, List<Index> indexPageList) throws IOException {
        List<SearchResponseData> list = new ArrayList<>();
        for (Map.Entry<Page, Float> entry : pageAbsRelevance.entrySet()) {
            String contentString = entry.getKey().getContent();
            String uri = entry.getKey().getPath().replaceFirst(entry.getKey().getSite().getUrl(), "");
            String title = contentString.substring(contentString.indexOf("<title>") + 7, contentString.indexOf("</title>"));
            String snippet = getSnippetFromIndex(entry.getKey().getPath(), indexPageList);
            list.add(new SearchResponseData(entry.getKey().getSite().getUrl(),
                    entry.getKey().getSite().getName(), uri, title, snippet, entry.getValue()));
        }
        return list;
    }

    private String getSnippetFromIndex(String path, List<Index> indexPageList) throws IOException {
        String snippet = "";
        for (Index index : indexPageList) {
            if (path.equals(index.getPage().getPath())) {
                String lemma = index.getLemma().getLemma();
                snippet = getSnippet(lemma, clear(index.getPage().getContent(), "body"));
            }
        }
        return snippet;
    }


    private String getSnippet(String lemma, String content) throws IOException {
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        String snippet = "";
        int lemmaId = 0;
        String[] words = content.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (lemmaFinder.collectLemmas(words[i]).keySet().contains(lemma)) {
                lemmaId = i;
            }
        }
        for (int i = 15; i > 0; i--) {
            if (lemmaId > i) {
                snippet = snippet + words[lemmaId - i] + " ";
            }
        }
        snippet = snippet + "<b>" + words[lemmaId] + "</b>";
        for (int i = 1; i < 16 && (lemmaId + i) < words.length; i++) {
            snippet = snippet + " " + words[lemmaId + i];
        }
        return snippet;
    }

    private String clear(String content, String selector) {
        StringBuilder html = new StringBuilder();
        var doc = Jsoup.parse(content);
        var elements = doc.select(selector);
        for (Element el : elements) {
            html.append(el.html());
        }
        return Jsoup.parse(html.toString()).text();
    }

    private HashMap<Page, Float> getPageAbsRelevance(List<Page> pageList, List<Index> indexPageList) {
        HashMap<Page, Float> pageRelevance = new HashMap<>();
        for (Page page : pageList) {
            pageRelevance.put(page, getPageRelevance(page, indexPageList));
        }
        HashMap<Page, Float> pageAbsRelevance = new HashMap<>();
        for (Page page : pageRelevance.keySet()) {
            float absRelevant = pageRelevance.get(page) / Collections.max(pageRelevance.values());
            pageAbsRelevance.put(page, absRelevant);
        }
        List<Map.Entry<Page, Float>> list = new ArrayList(pageAbsRelevance.entrySet());
        list.sort((a, b) -> a.getValue() > b.getValue() ? +1 : 0);
        pageAbsRelevance.clear();
        for (Map.Entry<Page, Float> entry : list) {
            pageAbsRelevance.put(entry.getKey(), entry.getValue());
        }
        return pageAbsRelevance;
    }

    private Float getPageRelevance(Page page, List<Index> indexPageList) {
        float relevant = 0;
        for (Index index : indexPageList) {
            if (index.getPage() == page) {
                relevant += index.getRank();
            }
        }
        return relevant;
    }
}

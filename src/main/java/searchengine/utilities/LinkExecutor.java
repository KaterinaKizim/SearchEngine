package searchengine.utilities;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.*;
import searchengine.services.IndexingService;
import searchengine.services.IndexingServiceImpl;

@RequiredArgsConstructor
public class LinkExecutor extends RecursiveTask<List<Page>> implements Runnable {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private String url;
    private Site site;
    private IndexingService indexingService;
    public volatile static boolean stoppedByUser = false;
    public static CopyOnWriteArrayList<String> allLinks = new CopyOnWriteArrayList<>();

    public LinkExecutor(SiteRepository siteRepository, PageRepository pageRepository, String url, Site site, IndexingService indexingService) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.url = url;
        this.site = site;
        this.indexingService = indexingService;
    }

    @Override
    protected List<Page> compute() {
        List<Page> pages = new ArrayList<Page>();
        System.out.println(url);
        try {
            if (stoppedByUser) {
                return pages;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Page page = new Page(site, url, 200, "");
        site.getPages().add(page);
        site.setStatusTime(LocalDateTime.now());
        List<LinkExecutor> allTasks = new CopyOnWriteArrayList<>();
        Document document;
        Elements elements;
        try {
            Thread.sleep(150);
            Connection.Response response = Jsoup.connect(url).userAgent("Mozilla/5.0").execute();
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                page.setCode(response.statusCode());
                return pages;
            }
            document = Jsoup.connect(url).ignoreContentType(true).userAgent("Mozilla/5.0").get();
            page.setContent(document.outerHtml());
            pageRepository.saveAndFlush(page);
            elements = document.select("a[href]");
            for (Element element : elements) {
                String attributeUrl = element.absUrl("href").toLowerCase();
                if (!attributeUrl.isEmpty() && attributeUrl.startsWith(url) && !allLinks.contains(attributeUrl)
                        && !attributeUrl.contains("#") && !attributeUrl.contains("?") && !attributeUrl.contains("&")
                        && !attributeUrl.contains(".jpg") && !attributeUrl.contains(".pdf") && !attributeUrl.contains(".png")
                        && !attributeUrl.contains(".jpeg") && !attributeUrl.contains(".jfif") && !attributeUrl.contains(".doc")
                        && !attributeUrl.contains(".docx") && !attributeUrl.contains(".xls") && !attributeUrl.contains(".xlsx")
                        && !attributeUrl.contains(".pptx") && !attributeUrl.contains(".rtf") && !attributeUrl.contains(".mp4")
                        && !attributeUrl.contains(".gif")) {
                    LinkExecutor linkExecutor = new LinkExecutor(siteRepository, pageRepository, attributeUrl, site, indexingService);
                    linkExecutor.fork();
                    allTasks.add(linkExecutor);
                    allLinks.add(attributeUrl);
                }
            }
        } catch (InterruptedException | IOException e) {
            String message = e.toString();
            setErrorCode(page, message);
        }
        pages.add(page);
        for (LinkExecutor link : allTasks) {
            pages.addAll(link.join());
        }
        return pages;
    }

    private void setErrorCode(Page page, String message) {
        int errorCode;
        if (message.contains("UnsupportedMimeTypeException")) {
            errorCode = 415;
        } else if (message.contains("Status=401")) {
            errorCode = 401;
        } else if (message.contains("UnknownHostException")) {
            errorCode = 401;
        } else if (message.contains("Status=403")) {
            errorCode = 403;
        } else if (message.contains("Status=404")) {
            errorCode = 404;
        } else if (message.contains("Connection timed out")) {
            errorCode = 408;
        } else if (message.contains("Status=500")) {
            errorCode = 401;
        } else if (message.contains("ConnectException: Connection refused")) {
            errorCode = 500;
        } else if (message.contains("SSLHandshakeException")) {
            errorCode = 525;
        } else {
            errorCode = -1;
        }
        page.setCode(errorCode);
        pageRepository.saveAndFlush(page);
    }

    @Override
    public void run() {
        List<Page> list = new ForkJoinPool().invoke(this);
        System.out.println("На сайте " + site.getName() + " найдено страниц " + list.size());
        if (stoppedByUser){
            site.setType(Site.Type.FAILED.name());
            site.setLastError("Индексация прервана пользователем");
        }
        try {
            createLemmas(list);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        IndexingServiceImpl.sitesInProcessingIndexing.remove(site.getUrl());
    }

    public synchronized  void createLemmas(List<Page> list) throws IOException {
        for (Page page:list) {
            if (stoppedByUser){
                site.setStatusTime(LocalDateTime.now());
                site.setType(Site.Type.FAILED.name());
                site.setLastError("Индексация прервана пользователем");
                siteRepository.saveAndFlush(site);
                return;
            }
            indexingService.createLemmasAndIndices(site, page);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.saveAndFlush(site);
        }
        site.setType(Site.Type.INDEXED.name());
        siteRepository.saveAndFlush(site);
        System.out.println("Завершено построение лемм и индексов для сайта " + site);
    }

}

package searchengine.dto.search;
import lombok.Data;

@Data
public class SearchResponseData {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private Float relevance;

    public SearchResponseData(String site, String siteName, String uri,
                            String title, String snippet, Float relevance) {
        this.site = site;
        this.siteName = siteName;
        this.uri = uri;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }
}

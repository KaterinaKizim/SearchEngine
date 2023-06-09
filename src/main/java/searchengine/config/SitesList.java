package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<SiteUrlName> sites;

    public SiteUrlName findSiteUrlNameByUrl(String site){
        for (SiteUrlName siteName:sites) {
            if (siteName.getUrl().equals(site)) return siteName;
        }
        return null;
    }

}

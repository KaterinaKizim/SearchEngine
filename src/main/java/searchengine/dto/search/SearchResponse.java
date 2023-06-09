package searchengine.dto.search;

import lombok.Data;
import lombok.EqualsAndHashCode;
import searchengine.dto.Response;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class SearchResponse extends Response {
    private int count;
    private List<SearchResponseData> data;

    public SearchResponse(List<SearchResponseData> searchResponseDataList, int count) {
        this.data = searchResponseDataList;
        this.count = count;
    }
}

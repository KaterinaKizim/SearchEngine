package searchengine.dto;

import lombok.Data;

@Data
public class Response {
    private boolean result;
    public Response() {
        result = true;
    }
}

package searchengine.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ErrorResponse extends Response {
    private String error;

    public ErrorResponse(String error) {
        setResult(false);
        this.error = error;
    }
}

package searchengine.dto.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class IndexingResponse {

    private boolean result;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;

    public IndexingResponse() {
        result = true;
    }

    public IndexingResponse(String error) {
        result = false;
        this.error = error;
    }

}

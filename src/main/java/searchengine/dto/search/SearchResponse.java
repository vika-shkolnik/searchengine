package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer count;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<DataSearchItem> data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;

    public SearchResponse(String error) {
        this.result = false;
        this.error = error;
    }

    public SearchResponse(int count, List<DataSearchItem> data) {
        this.result = true;
        this.count = count;
        this.data = data;
    }

}
